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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


/**
 * PacctParser parses the BSD process accounting v3 files.  The accounting
 * file contains information about process creation times, PIDs, PPIDs etc.
 * It is used to get detailed information about process forks, filling in any
 * dependency blanks caused by the polling nature of
 * <code>/proc/[PID]/top</code> logging.
 */
public class PacctParser {
	private static final Logger log = Logger.getLogger(PacctParser.class.getName());

	/**
	 * Parses the <code>pacct</code> accounting file.  See
	 * <code>include/linux/acct.h</code> for format.
	 * 
	 * @param is               the input stream to parse
	 * @return                 a map of Integer to Integer forks
	 * @throws IOException     if an I/O error occurs
	 */
	public static Map parseLog(InputStream is) throws IOException {
		Map forkMap = new HashMap();
		while (is.available() > 0) {
			// Note: TarInputStream's skip() seems broken, so read() instead
			is.read(); // 
			byte version = (byte)is.read();
			if (version < 3) {
				log.warning("Invalid accounting version: " + version);
				return null;
			}
			for (int i=0; i<14; i++) { // tty, exitcode, uid, gid
				is.read();
			}
			Integer pid = new Integer((int)readUInt32(is));
			Integer ppid = new Integer((int)readUInt32(is));
			List forkedPids = (List)forkMap.get(ppid);
			if (forkedPids == null) {
				forkedPids = new ArrayList();
			}
			forkedPids.add(pid);
			forkMap.put(ppid, forkedPids);
			for (int i=0; i<24; i++) { // times, mem, faults, etc.
				is.read();
			}
			byte[] buff = new byte[16];
			is.read(buff); // comm
			StringBuffer comm = new StringBuffer();
			for (int i=0; i<buff.length; i++) {
				if (buff[i] == 0) {
					break;
				}
				comm.append((char)buff[i]);
			}
			//log.fine(comm + " (" + pid + ") was forked by " + ppid);
		}
		return forkMap;
	}
	
	private static long readUInt32(InputStream is) throws IOException {
		return (is.read() & 0xFF) | ((is.read() & 0xFF) << 8) |
		       ((is.read() & 0xFF) << 16) | ((is.read() & 0xFF) << 24);
	}
	
	/**
	 * Returns a list of all parent PIDs (parent, grandparent, etc.) for the
	 * specified PID.
	 * 
	 * @param pid      the PID to get the PPIDs for
	 * @param forkMap  fork map
	 * @return         a list of parent PIDs
	 */
	public static List getPPIDs(int pid, Map forkMap) {
		if (forkMap == null) {
			return null;
		}
		// find the process that forked the specified PID
		Integer ppid = null;
		Integer iPid = new Integer(pid);
		for (Iterator i = forkMap.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry)i.next();
			List pids = (List)entry.getValue();
			if (pids.contains(iPid)) {
				ppid = (Integer)entry.getKey();
				break;
			}
		}
		if (ppid == null) {
			return null;
		} else if (ppid.intValue() != pid) {
			List recPpids = getPPIDs(ppid.intValue(), forkMap);
			List ppids = new ArrayList();
			ppids.add(ppid);
			if (recPpids != null) {
				ppids.addAll(recPpids);
			}
			return ppids;
		} else {
			return null;
		}
	}
}
