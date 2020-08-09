/*
 * Bootchart -- Boot Process Visualization
 *
 * Copyright (C) 2004  Ziga Mahkovec <ziga.mahkovec@klika.si>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.bootchart.common;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ProcessTree encapsulates a process tree.  The tree is built from log files
 * retrieved during the boot process.  When building the process tree, it is
 * pruned and merged in order to be able to visualize it in a comprehensible
 * manner.
 * <p>The following pruning techniques are used:</p>
 * <ul>
 *   <li>idle processes that keep running during the last process sample
 *       (which is a heuristic for a background processes) are removed,</li>
 *   <li>short-lived processes (i.e. processes that only live for the
 *       duration of two samples or less) are removed,</li>
 *   <li>the processes used by the boot logger are removed,</li>
 *   <li>expoders (i.e. processes that are known to spawn huge meaningless
 *       process subtrees) have their subtrees merged together,</li>
 *   <li>siblings (i.e. processes with the same command line living
 *       concurrently -- thread heuristic) are merged together,</li>
 *   <li>process runs (unary trees with processes sharing the command line)
 *       are merged together.</li>
 * </ul>
 */
public class ProcessTree {
	private static final Logger log = Logger.getLogger(ProcessTree.class.getName());
	
	/** The process that performs the logging. */
	private static final String LOGGER_PROC = "bootchartd";
	
	/**
	 * The list of processes that should have their subtrees merged.  This is
	 * used for processes that usually spawn huge meaningless process trees.
	 */
	private static final List MERGE_PROC_LIST =
		Arrays.asList(new String[]{"hwup"});
	
	/** The time format used when printing process duration. */
	private static final SimpleDateFormat TIME_FORMAT =
		new SimpleDateFormat("mm:ss.SSS", Common.LOCALE);
	
	/** The start time of the graph */
	public Date startTime;
	public Date endTime;
	
	/**
	 * The duration of the process tree (measured from the start time of
	 * the first process to the end time of the last process).  This is
	 * also the total boot time.
	 */
	public long duration;
	
	/** The process statistics */
	private PsStats psStats;
	/** Statistics sampling period. */
	public int samplePeriod;
	
	/** The number of all processes in the tree. */
	public int numProc;
	
	/** List of {@link Process} instances. */
	private List processList;
	
	/** The {@link Process} tree. */
	public List processTree;
	
	/**
	 * Creates a new process tree from the specified list of
	 * <code>Process</code> instances.
	 * 
	 * @param psStats       process statistics
	 * @param monitoredApp  monitored application (or <code>null</code> if
	 *                      the boot process is monitored)
	 * @param prune         whether to prune the tree by removing sleepy and
	 *                      short-living processes and merging threads
	 */
	public ProcessTree(PsStats psStats, String monitoredApp, boolean prune) {
		this.psStats = psStats;
		if(psStats == null || psStats.processList.size() == 0) {
			this.processList = null;
			return;
		}

		this.processList = psStats.processList;
		this.samplePeriod = psStats.samplePeriod;
		
		if (processList == null || processList.size() == 0) {
			return;
		}
		
		build();
		log.fine("Number of all processes: " + numNodes(processTree));
		
		/*
		 * Fedora hack: when loading the system services from rc, runuser(1)
		 * is used.  This sets the PPID of all daemons to 1, skewing the 
		 * process tree.  Try to detect this and set the PPID of these
		 * processes the PID of rc.
		 */
		int rcStartPid = -1; // PID of rc
		int rcEndPid = -1;   // PID of the last rc child
		Process rcProc = null;
		for (Iterator i=processList.iterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (p.cmd.equals("rc") && p.ppid == 1) {
				rcProc = p;
				rcStartPid = p.pid;
				rcEndPid = getMaxPid(p.childList);
			}
		}
		if (rcStartPid != -1 && rcEndPid != -1) {
			// include the stray daemons in the rc subtree
			for (Iterator i=processList.iterator(); i.hasNext(); ) {
				Process p = (Process)i.next();
				if (p.pid > rcStartPid && p.pid < rcEndPid && p.ppid == 1) {
					p.ppid = rcStartPid;
					p.parent = rcProc;
				}
			}
			// rebuild the process tree
			for (Iterator i=processList.iterator(); i.hasNext(); ) {
				Process p = (Process)i.next();
				p.childList = new ArrayList();
			}
			build();
		}
		
		// compute the process tree times
		//startTime = (monitoredApp == null ? new Date(0) : getStartTime(processTree));
		startTime = getStartTime(processTree);
		endTime = getEndTime(processTree);
		duration = endTime.getTime() - startTime.getTime();

		/*
		 * Merge the logger's processes, since it forks lots of sleeps and other
		 * processes.
		 */
		int numRemoved = mergeLogger(processTree, LOGGER_PROC, monitoredApp,
				false);

		/**
		 * If we're monitoring an application, remove all processes that existed
		 * before the app started
		 */
		if (monitoredApp != null) {
			int preExistingProcsRemoved = removePreExisting();
			log.fine("Removing " + preExistingProcsRemoved
					+ " pre-existing processes");
		}

		if (prune) { // profiling the boot process
			numRemoved = prune(processTree, null);
			log.fine("Number of removed idle processes: " + numRemoved);
			numRemoved = mergeExploders(processTree, MERGE_PROC_LIST);
			log.fine("Number of removed exploders: " + numRemoved);
			numRemoved = mergeSiblings(processTree);
			log.fine("Number of removed threads: " + numRemoved);
			numRemoved = mergeRuns(processTree);
			log.fine("Number of removed runs: " + numRemoved);
		}
		numProc = numNodes(processTree);
		log.fine("Number of processes after pruning: " + numProc);
		
		// sort siblings by PID
		sort(processTree);
		//log.fine(toString());
		
		// update process tree times
		if (monitoredApp == null) {
			// Start time is time at which first process was started
			//startTime = new Date(0);
			startTime = getStartTime(processTree);
			endTime = getEndTime(processTree);
		} else {
			// Start time is when stat collection started
			startTime = psStats.startTime;
			endTime = psStats.endTime;
		}

		duration = endTime.getTime() - startTime.getTime();
		log.fine("Boot time: " + duration);
	}
	
	/**
	 * Build the process tree from the list of top samples.
	 */
	private void build() {
		processTree = new ArrayList();
		for (Iterator i=processList.iterator(); i.hasNext(); ) {
			Process proc = (Process)i.next();
			// find the parent process
			if (proc.parent != null) {
				proc.parent.childList.add(proc);
			} else {
				// a root process
				processTree.add(proc);
			}
		}
	}
	
	/**
	 * Returns the start time of the process subtree.  This is the start time of
	 * the earliest process.
	 * 
	 * @param processSubtree  the process subtree to get the start time for
	 * @return                the start time
	 */
	private Date getStartTime(List processSubtree) {
		long minTime = Long.MAX_VALUE;
		for (Iterator i=processSubtree.iterator(); i.hasNext(); ) {
			Process proc = (Process)i.next();
			if (proc.startTime.getTime() < minTime) {
				minTime = proc.startTime.getTime();
			}
			minTime = Math.min(minTime, getStartTime(proc.childList).getTime());
		}
		return new Date(minTime);
	}
	
	/**
	 * Returns the end time of the process subtree.  This is the end time of
	 * the last collected sample.
	 * 
	 * @param processSubtree  the process subtree to get the end time for
	 * @return                the end time
	 */
	private Date getEndTime(List processSubtree) {
		long maxTime = Long.MIN_VALUE;
		for (Iterator i=processSubtree.iterator(); i.hasNext(); ) {
			Process proc = (Process)i.next();
			if (proc.startTime.getTime() + proc.duration > maxTime) {
				maxTime = proc.startTime.getTime() + proc.duration;
			}
			maxTime = Math.max(maxTime, getEndTime(proc.childList).getTime());
		}
		return new Date(maxTime);
	}
	
	/**
	 * Returns the max PID found in the process tree.
	 * 
	 * @param processSubtree  process subtree
	 * @return                max PID
	 */
	private static int getMaxPid(List processSubtree) {
		int maxPid = Integer.MIN_VALUE;
		for (Iterator i=processSubtree.iterator(); i.hasNext(); ) {
			Process proc = (Process)i.next();
			if (proc.pid > maxPid) {
				maxPid = proc.pid;
			}
			maxPid = Math.max(maxPid, getMaxPid(proc.childList));
		}
		return maxPid;
	}

	/**
	 * Returns a map of all processes in the tree.
	 * 
	 * @param processSubtree  the subtree to get the processes for
	 * @return                a map of all processes in the tree
	 */
	private Map getProcessMap(List processSubtree) {
	    Map procMap = new HashMap();
		for (Iterator i=processSubtree.iterator(); i.hasNext();) {
			Process p = (Process)i.next();
			procMap.put(p, p);
			procMap.putAll(getProcessMap(p.childList));
		}
		return procMap;
	}
	
	/**
	 * Prunes the process tree by removing idle processes and processes that
	 * only live for the duration of a single top sample are removed.
	 * Sibling processes with the same command line (i.e. threads) are
	 * merged together.
	 *
	 * @param processSubtree  the process tree to prune
	 * @param parent          parent node (<code>null</code> for roots)
	 * @return                the number of removed processes
	 */
	private int prune(List processSubtree, Process parent) {
		int numRemoved = 0;
		for (ListIterator i=processSubtree.listIterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (parent != null || p.childList.size() == 0) {
				/*
				 * Filter out sleepy background processes, short-lived
				 * processes and bootchart's anaylsis tools.
				 */
				Date processEnd =
					new Date(p.startTime.getTime() + p.duration);
				
				// whether to filter out this process
				boolean prune = false;
				if (!p.active &&
					processEnd.getTime() >= startTime.getTime() + duration &&
					p.startTime.getTime() > startTime.getTime() &&
					p.duration > 0.9 * duration &&
					numNodes(p.childList) == 0) {
					// idle background processes without children
					prune = true;
				} else if (p.duration <= 2 * samplePeriod) {
					// short-lived process
					prune = true;
				}
				
				if (prune) { 
					i.remove();
					numRemoved++;
					for (Iterator j=p.childList.iterator(); j.hasNext(); ) {
						i.add(j.next());
						i.previous();
					}
				} else {
					numRemoved += prune(p.childList, p);
				}
			} else {
				numRemoved += prune(p.childList, p);
			}
		}
		return numRemoved;
	}
	
	/**
	 * Merges specific process subtrees (used for processes which usually
	 * spawn huge meaningless process trees).
	 *
	 * @param processSubtree  the process tree to merge
	 * @param processes       the exploder processes to merge
	 * @return                the number of removed processes
	 */
	private int mergeExploders(List processSubtree, List processes) {
		int numRemoved = 0;
		for (ListIterator i=processSubtree.listIterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (processes.contains(p.cmd) && p.childList.size() > 0) {
				// get the entire subtree as a map
				Map subtreeMap = getProcessMap(p.childList);
				for (Iterator j=subtreeMap.entrySet().iterator(); j.hasNext(); ) {
					Map.Entry entry = (Map.Entry)j.next();
					Process child = (Process)entry.getValue();
					mergeProcesses(p, child);
				}
				numRemoved += subtreeMap.size();
				p.childList = new ArrayList();
				p.cmd += " (+)";  // mark merger
			} else {
				numRemoved += mergeExploders(p.childList, processes);
			}
		}
		return numRemoved;
	}
	
	/**
	 * Merges the logger's process subtree.  The logger will typically
	 * spawn lots of sleep and cat processes, thus polluting the process
	 * tree.
	 *
	 * @param processSubtree  the process tree to merge
	 * @param loggerProc      logger's process
	 * @param monitoredApp    monitored application (or <code>null</code> if
	 *                        if the boot process is monitored)
	 * @return                the number of removed processes
	 */
	private int mergeLogger(List processSubtree, String loggerProc,
		                    String monitoredApp, boolean appTree) {
		int numRemoved = 0;
		for (ListIterator i=processSubtree.listIterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			boolean isAppTree = appTree;
			if (loggerProc.equals(p.cmd) && !appTree) {
				isAppTree = true;
				numRemoved += mergeLogger(p.childList, loggerProc, monitoredApp, isAppTree);
				// don't remove the logger itself
				continue;
			}
			if (appTree && monitoredApp != null && monitoredApp.equals(p.cmd)) 
				isAppTree = false;

			if (isAppTree) {
				for (ListIterator j=p.childList.listIterator(); j.hasNext(); ) {
					Process child = (Process)j.next();
					mergeProcesses(p, child);
					numRemoved++;
				}
				p.childList = new ArrayList();
			} else 
			    numRemoved += mergeLogger(p.childList, loggerProc, monitoredApp, isAppTree);

		}
		return numRemoved;
	}
	
	/**
	 * Merges thread processes.  Sibling processes with the same command line
	 * are merged together.
	 *
	 * @param processSubtree  the process tree to merge
	 * @return                the number of removed processes
	 */
	private int mergeSiblings(List processSubtree) {
		int numRemoved = 0;
		for (int i=0; i<processSubtree.size() - 1; i++) {
			Process p = (Process)processSubtree.get(i);
			Process nextP = (Process)processSubtree.get(i+1);
			if (nextP.cmd.equals(p.cmd)) {
				processSubtree.remove(i+1);
				i--;
				numRemoved++;
				for (Iterator j=nextP.childList.iterator(); j.hasNext(); ) {
					p.childList.add(j.next());
				}
				mergeProcesses(p, nextP);
			}
			numRemoved += mergeSiblings(p.childList);
		}
		if (processSubtree.size()  > 0) {
			Process p = (Process)processSubtree.get(processSubtree.size() - 1);
			numRemoved += mergeSiblings(p.childList);
		}
		return numRemoved;
	}
	
	private static void mergeProcesses(Process p1, Process p2) {
		p1.samples.addAll(p2.samples);
		long p1time = p1.startTime.getTime();
		long p2time = p2.startTime.getTime();
		p1.startTime = new Date(Math.min(p1time, p2time));
		long pEndTime =
			Math.max(p1time + p1.duration, p2time + p2.duration);
		p1.duration = pEndTime - p1.startTime.getTime();
	}
	
	/**
	 * Merges process runs.  Single child processes which share the same
	 * command line with the parent are merged.
	 *
	 * @param processSubtree  the process tree to merge
	 * @return                the number of removed processes
	 */
	private int mergeRuns(List processSubtree) {
		int numRemoved = 0;
		for (ListIterator i=processSubtree.listIterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (p.childList.size() == 1 &&
				((Process)p.childList.get(0)).cmd.equals(p.cmd)) {
				Process child = (Process)p.childList.get(0);
				p.childList = new ArrayList();
				for (Iterator j=child.childList.iterator(); j.hasNext(); ) {
					p.childList.add(j.next());
				}
				p.samples.addAll(child.samples);
				long ptime = p.startTime.getTime();
				long nptime = child.startTime.getTime();
				p.startTime = new Date(Math.min(ptime, nptime));
				long pEndTime =
					Math.max(ptime + p.duration, nptime + child.duration);
				p.duration = pEndTime - p.startTime.getTime();
				numRemoved++;
				i.previous();
				continue;
			}
			numRemoved += mergeRuns(p.childList);
		}
		return numRemoved;
	}
	
	/**
	 * Removes processes that already existed when data collection started.
	 * Useful for when a specific application is being monitored.
	 * @return the number of removed processes
	 */
	private int removePreExisting(List processSubtree) {
		int numRemoved = 0;

		List rest = new ArrayList();
		for (Iterator i = processSubtree.iterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (p.startTime.getTime() < psStats.startTime.getTime()) {
				if (p.parent != null) {
					log.fine("Removing process " + p.cmd);
					mergeProcesses(p.parent,p);
					numRemoved += removePreExisting(p.childList);	
					for (Iterator j=p.childList.iterator(); j.hasNext();) rest.add(j.next());
					i.remove();
					numRemoved++;
				} else {
					numRemoved += removePreExisting(p.childList);
					p.startTime = psStats.startTime;
					p.duration = psStats.endTime.getTime() - p.startTime.getTime();
				}
			}
		}
		for (Iterator j=rest.iterator(); j.hasNext();) {
			processSubtree.add(j.next());
		}

		return numRemoved;
	}
	
	private int removePreExisting() {
		return removePreExisting(processTree);
	}

	/**
     * Sort process tree.
     *
     * @param processSubtree  the process tree to sort
     */
    private void sort(List processSubtree) {
            for (Iterator i=processSubtree.iterator(); i.hasNext();) {
                    Process p = (Process)i.next();
                    Collections.sort(p.childList);
                    sort(p.childList);
            }
    }

	/**
	 * Counts the number of nodes in the specified process tree.
	 * 
	 * @param processSubtree  the tree to cound nodes for
	 * @return                the number of nodes
	 */
	private static int numNodes(List processSubtree) {
		int nodes = 0;
		for (Iterator i=processSubtree.iterator(); i.hasNext();) {
			Process p = (Process)i.next();
			nodes++;
			nodes += numNodes(p.childList);
		}
		return nodes;
	}
	
	/**
	 * Recursively returns a string representation of the process tree.
	 * 
	 * @param processSubtree  the process subtree to print
	 * @param tablevel        current tab level
	 * @return                a string representation of the process tree
	 */
	private String toString(List processSubtree, int tablevel) {
		StringBuffer sb = new StringBuffer();
		for (Iterator i=processSubtree.iterator(); i.hasNext();) {
			Process p = (Process)i.next();
			for (int j=0; j<tablevel; j++) {
				sb.append("  ");
			}
			sb.append(p.pid + " " + p.cmd + " "
				+ TIME_FORMAT.format(p.startTime) + " " + p.duration + "\n");
			sb.append(toString(p.childList, tablevel + 1));
		}
		return sb.toString();
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return toString(processTree, 0);
	}
}
