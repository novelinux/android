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
 * PsParser parses log files produced by <code>ps</code>.
 */
public class PsParser {
	private static final Logger log = Logger.getLogger(PsParser.class.getName());
	
	/** The mapping between ps column types and Java objects. */
	private static Map COLUMN_TYPES = new HashMap();
	
	static {
		// Populate the COLUMN_TYPES map.
		COLUMN_TYPES.put("PID",     Integer.class.getName());
		COLUMN_TYPES.put("PPID",    Integer.class.getName());
		COLUMN_TYPES.put("S",       String.class.getName());
		COLUMN_TYPES.put("STAT",    String.class.getName());
		COLUMN_TYPES.put("%CPU",    Double.class.getName());
		COLUMN_TYPES.put("COMMAND", String.class.getName());
		COLUMN_TYPES.put("CMD",     String.class.getName());
	}

	/**
	 * Parses the <code>ps.log</code> file.  Consecutive ps samples are
	 * parsed and returned in a list.  The ps samples are only parsed up to
	 * the point where one of the specified <code>exitProcesses</code> is
	 * running and the system is idle.
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
	 * @return                 ps statistics
	 *                         and a list of processes
	 * @throws IOException     if an I/O error occurs
	 */
	public static PsStats parseLog(InputStream is, Map pidNameMap, Map forkMap)
		throws IOException {
		BufferedReader reader = Common.getReader(is);
		String line = reader.readLine();

		Map processMap = new TreeMap();
		int numSamples = 0;
		
		Date startTime = null;
		Date time = null;
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
			if (line.startsWith("#")) {
				continue;
			}
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
			// read column line
			if (line == null) {
				break;
			}
			String[] columns = line.trim().split("\\s+");
			line = reader.readLine();
			if (line == null) {
				break;
			}
			while (line != null && line.trim().length() > 0) {
				line = line.trim();
				String[] data = line.split("\\s+");
				Map procInfo = new HashMap();
				for (int i=0; i<columns.length; i++) {
					if (columns[i].equals("CMD")) {
						// concatenate command line back together
						StringBuffer sb = new StringBuffer();
						for (int j=i; j<data.length; j++) {
							if (sb.length() > 0) {
								sb.append(" ");
							}
							sb.append(data[j]);
						}
						data[i] = sb.toString();
					}
					// convert process info string to a Java object
					try {
						Object o = getObject(data[i], columns[i]);
						procInfo.put(columns[i], o);
					} catch (NumberFormatException e) {
						log.fine("Error parsing: " + columns[i] + " (" + data[i] + ")");
						procInfo.put(columns[i], null);
					}
				}

				int pid = ((Integer)procInfo.get("PID")).intValue();
				String cmd = (String)procInfo.get("CMD");
				if (cmd == null) {
					cmd = (String)procInfo.get("COMMAND");
				}
				
				// remove shell invocations, parameters, etc. and retain only
				// the command name
				cmd = Common.formatCommand(cmd);
				if (cmd == null || cmd.length() == 0) {
					cmd = "(unknown)";
				}

				if (pidNameMap != null &&
					pidNameMap.containsKey((Integer)procInfo.get("PID"))) {
					cmd = (String)pidNameMap.get((Integer)procInfo.get("PID"));
				}

				Process proc = (Process)processMap.get(new Integer(pid));
				if (proc == null) {
					Integer ppid = (Integer)procInfo.get("PPID");
					proc = new Process(pid, cmd);
					if (ppid != null) {
						proc.ppid = ppid.intValue();
					}
					if (forkMap != null) {
						// check if the forkMap contains the PPID
						List ppids = PacctParser.getPPIDs(pid, forkMap);
						if (ppids != null) {
							for (Iterator i=ppids.iterator(); i.hasNext(); ) {
								int p = ((Integer)i.next()).intValue();
								if (processMap.containsKey(new Integer(p))) {
									proc.ppid = p;
									break;
								}
							}
						}
					}
					processMap.put(new Integer(pid), proc);
				} else {
					proc.cmd = cmd;
				}
				
				Double dCpu = (Double)procInfo.get("%CPU");
				double cpu = dCpu != null ? dCpu.doubleValue() / 100.0 : 0.0;
				cpu = Math.max(0.0, Math.min(1.0, cpu));
				
				int state = getState(procInfo);
				if (cpu > 0.0) {
					state = Process.STATE_RUNNING;
				}
				if (state != Process.STATE_SLEEPING) {
					proc.active = true;
				}
				CPUSample procCpuSample = new CPUSample(null, cpu, 0.0, 0.0);
				ProcessSample procSample =
					new ProcessSample(time, state, procCpuSample, null, null);
				proc.samples.add(procSample);
				line = reader.readLine();
			}
			if (numSamples > Common.MAX_PARSE_SAMPLES) {
				break;
			}
		}

		log.fine("Parsed " + numSamples + " ps samples "
			     + "(" + processMap.values().size() + " processes)");
		
		// set process parents
		for (Iterator i=processMap.values().iterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			if (p.ppid != -1) {
				p.parent = (Process)processMap.get(new Integer(p.ppid));
				if (p.parent == null) {
					log.fine("No parent for: " + p);
				}
			}
		}
		
		int samplePeriod = (int)(time.getTime() - startTime.getTime()) / numSamples;
		log.fine("Sample period: " + samplePeriod);
		
		// update process times
		for (Iterator i=processMap.values().iterator(); i.hasNext(); ) {
			Process p = (Process)i.next();
			Sample fs = (Sample)p.samples.get(0);
			Sample ls = (Sample)p.samples.get(p.samples.size() - 1);
			p.startTime = fs.time;
			p.duration = ls.time.getTime() - fs.time.getTime() + samplePeriod;
		}
		PsStats psStats = new PsStats();
		psStats.processList = new ArrayList(processMap.values());
		psStats.startTime = startTime;
		psStats.endTime = time;
		psStats.samplePeriod = samplePeriod;
		return psStats;
	}
	
	/**
	 * Convert the specified data string into a Java object.
	 * 
	 * @param data    data string
	 * @param column  column name
	 * @return        object
	 */
	private static Object getObject(String data, String column) {
		if (data == null) {
			return null;
		}
		String colType = (String)COLUMN_TYPES.get(column);
		if (colType == null) {
			colType = String.class.getName();
		}
		if (colType.equals(Integer.class.getName())) {
			return new Integer(data);
		} else if (colType.equals(Double.class.getName())) {
			return new Double(data);
		} else if (colType.equals(String.class.getName())) {
			if (data.equals("-")) {
				return null;
			}
			return data;
		} else {
			return data;
		}
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
     * @param procInfo  process info map
     * @return          process state
     */
    public static int getState(Map procInfo) {
        String state = (String)procInfo.get("S");
        if (state == null) {
            // older procps versions
        	state = (String)procInfo.get("STAT");
        }
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
