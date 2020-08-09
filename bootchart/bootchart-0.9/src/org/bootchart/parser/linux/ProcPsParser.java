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
package org.bootchart.parser.linux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.bootchart.common.CPUSample;
import org.bootchart.common.Common;
import org.bootchart.common.Process;
import org.bootchart.common.ProcessSample;
import org.bootchart.common.PsStats;
import org.bootchart.common.Sample;


/**
 * ProcPsParser parses log files produced by logging the output of
 * <code>/proc/[PID]/stat</code> files.  The samples contain status
 * information about processes (PID, command, state, PPID, user and system
 * CPU times, etc.).
 */
public class ProcPsParser {
	private static final Logger log = Logger.getLogger(ProcPsParser.class.getName());

	/**
	 * Parses the <code>proc_ps.log</code> file.  The output from
	 * <code>/proc/[PID]stat</code> is used to collect process 
	 * information.
	 * 
	 * <p>If <code>pidNameMap</code> is set, it is used to map PIDs to
	 * command names.  This is useful when init scripts are sourced, and thus
	 * ps is unable to report the proper process name.  A sysinit
	 * modification is necessary to generate the mapping log file.</p>
	 * <p><code>forkMap</code> is an optional map that provides detailed
	 * information about process forking.<p>
	 * 
	 * @param is               the input stream to read from
	 * @param pidNameMap       PID to name mapping map (optional)
	 * @param forkMap          process forking map (optional)
	 * @return                 process statistics
	 * @throws IOException     if an I/O error occurs
	 */
	public static PsStats parseLog(InputStream is, Map pidNameMap, Map forkMap)
		throws IOException {
		BufferedReader reader = Common.getReader(is);
		String line = reader.readLine();

		Map processMap = new TreeMap();
		Map lastUserCpuTimes = new HashMap();
		Map lastSysCpuTimes = new HashMap();
		int numSamples = 0;
		
		Date startTime = null;
		Date time = null;
		// last time
		Date ltime = null;
		while (line != null) {
			// skip empty lines
			while (line != null && line.trim().length() == 0) {
				line = reader.readLine();
			}
			if (line == null) {
				// EOF
				break;
			}
			line = line.trim();
			if (line.matches("^\\d+$")) {
				// jiffies (1/100s uptime)
				time = new Date(Long.parseLong(line) * 10);
				if (startTime == null) {
					startTime = time;
				}
				numSamples++;
			} else {
				line = reader.readLine();
				continue;
			}
			line = reader.readLine();
			while (line != null && line.trim().length() > 0) {
				line = line.trim();
				/*
				 * See proc(5) for details.
				 * 
				 * {pid, comm, state, ppid, pgrp, session, tty_nr, tpgid,
				 *  flags, minflt, cminflt, majflt, cmajflt, utime, stime,
				 *  cutime, cstime, priority, nice, 0, itrealvalue, starttime, 
				 *  vsize, rss, rlim, startcode, endcode, startstack, 
				 *  kstkesp, kstkeip}
				 */
				int pos = line.indexOf(' ');
				if (pos == -1) {
					log.fine("Invalid line: " + line);
					line = reader.readLine();
					continue;
				}
				int pid = Integer.parseInt(line.substring(0, line.indexOf(' ')));
				int p1 = line.indexOf('(') + 1;
				int p2 = line.lastIndexOf(')');
				if (p1 == -1 || p2 == -1) {
					log.fine("Invalid line: " + line);
					line = reader.readLine();
					continue;
				}
				String cmd = line.substring(p1, p2);
				
				if (pid == 0) {
					log.fine("Invalid line: " + line);
					line = reader.readLine();
					continue;
				}
				// Note that indexes get shifted by -2.
				String[] data = line.substring(p2 + 1).trim().split("\\s+");
				
				Process proc = (Process)processMap.get(new Integer(pid));
				if (proc == null) {
					int ppid = Integer.parseInt(data[1]);
					proc = new Process(pid, cmd);
					proc.ppid = ppid;
					proc.startTime = new Date(Math.min(Integer.parseInt(data[19]) * 10, time.getTime()));
					processMap.put(new Integer(pid), proc);
				} else {
					proc.cmd = cmd;
				}
				
				int state = getState(data[0]);
				long userCpu = Long.parseLong(data[11]);
				long sysCpu = Long.parseLong(data[12]);
				double cpuLoad = 0.0;
				Long lUserCpu = (Long)lastUserCpuTimes.get(new Integer(pid));
				Long lSysCpu = (Long)lastSysCpuTimes.get(new Integer(pid));
				if (lUserCpu != null && lSysCpu != null && ltime != null) {
					long interval = time.getTime() - ltime.getTime();
					//interval = Math.max(interval, 1);
					double userCpuLoad =
						(double)(userCpu - lUserCpu.longValue()) * 10.0 / interval;
					double sysCpuLoad =
						(double)(sysCpu - lSysCpu.longValue()) * 10.0 / interval;
					cpuLoad = userCpuLoad + sysCpuLoad;
					// normalize
					if (cpuLoad > 1.0) {
						userCpuLoad /= cpuLoad;
						sysCpuLoad /= cpuLoad;
					}
					CPUSample procCpuSample = new CPUSample(null, userCpuLoad, sysCpuLoad, 0.0);
					ProcessSample procSample =
						new ProcessSample(time, state, procCpuSample, null, null);
					proc.samples.add(procSample);
				}
				lastUserCpuTimes.put(new Integer(pid), new Long(userCpu));
				lastSysCpuTimes.put(new Integer(pid), new Long(sysCpu));
				
				if (cpuLoad > 0.0) {
					state = Process.STATE_RUNNING;
				}
				line = reader.readLine();
			}
			ltime = time;
			if (numSamples > Common.MAX_PARSE_SAMPLES) {
				break;
			}
		}

		log.fine("Parsed " + numSamples + " process samples "
			     + "(" + processMap.values().size() + " processes)");
		
		// set process parents
		for (Iterator i=processMap.values().iterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (forkMap != null) {
				// check if the forkMap contains the PPID
				List ppids = PacctParser.getPPIDs(p.pid, forkMap);
				if (ppids != null) {
					for (Iterator j=ppids.iterator(); j.hasNext(); ) {
						int ppid = ((Integer)j.next()).intValue();
						if (processMap.get(new Integer(ppid)) != null) {
							p.ppid = ppid;
							break;
						}
					}
				}
			}
			if (p.ppid != -1) {
				p.parent = (Process)processMap.get(new Integer(p.ppid));
				if (p.parent == null && p.pid > 1) {
					log.fine("No parent for: " + p);
				}
			}
		}
		
		int samplePeriod = (int)(time.getTime() - startTime.getTime()) / numSamples;
		log.fine("Sample period: " + samplePeriod);
		
		// update process times, names and descriptions
		for (Iterator i=processMap.values().iterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (p.samples.size() > 0) {
			  Sample fs = (Sample)p.samples.get(0);
			  p.startTime = new Date(Math.min(fs.time.getTime(), p.startTime.getTime()));
			  Sample ls = (Sample)p.samples.get(p.samples.size() - 1);
			  p.duration = ls.time.getTime() - p.startTime.getTime() + samplePeriod;
            } else {
              p.duration = 0;
            }
			
			/*
			 * Check if the pid-name map provides detailed information about
			 * the process.
			 */
			if (pidNameMap != null &&
				pidNameMap.containsKey(new Integer(p.pid))) {
				String[] cmdDesc = (String[])pidNameMap.get(new Integer(p.pid));
				p.cmd = cmdDesc[0];
				StringBuffer desc = new StringBuffer();
				if (cmdDesc.length > 1) {
					desc.append(cmdDesc[1]);
					for (int j=2; j<cmdDesc.length; j++) {
						desc.append("\n" + cmdDesc[j]);
					}
				}
				p.desc = desc.toString();
			}
			
			int activeSamples = 0;
			for (Iterator j=p.samples.iterator(); j.hasNext(); ) {
				ProcessSample sample = (ProcessSample)j.next();
				if (sample.cpu != null &&
					sample.cpu.user + sample.cpu.sys + sample.cpu.io > 0.0) {
					activeSamples++;
				} else if (sample.state == Process.STATE_WAITING) {
					activeSamples++;
				}
			}
			p.active = (activeSamples > 2);
		}
		PsStats psStats = new PsStats();
		psStats.processList = new ArrayList(processMap.values());
		psStats.samplePeriod = samplePeriod;
		psStats.startTime = startTime;
		psStats.endTime = time;
		return psStats;
	}
	
	/**
     * Returns the process state.  State can be one of:
     * <ul>
     *   <li>"D": uninterruptible sleep</li>
     *   <li>"R": running</li>
     *   <li>"S": sleeping</li>
     *   <li>"T": traced or stopped</li>
     *   <li>"Z": zombie</li>
     * </ul>
     * 
     * @param state  process state string
     * @return       process state
     */
    public static int getState(String state) {
        if ("D".equals(state)) {
        	return Process.STATE_WAITING;
        } else if ("R".equals(state)) {
        	return Process.STATE_RUNNING;
        } else if ("S".equals(state)) {
        	return Process.STATE_SLEEPING;
        } else if ("T".equals(state)) {
        	return Process.STATE_STOPPED;
        } else if ("Z".equals(state)) {
        	return Process.STATE_ZOMBIE;
        } else {
        	return Process.STATE_UNDEFINED;
        }
    }
    
}
