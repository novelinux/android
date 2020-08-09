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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;


/**
 * PidNameParser parses PID to command name mapping log files.
 */
public class PidNameParser {
	private static final Logger log = Logger.getLogger(PidNameParser.class.getName());
	
	/**
	 * Parses the <code>pidname</code> log file (containing pid to command
	 * name mappings).
	 * 
	 * @param is               the input stream to parse
	 * @return                 a map of Integer to String instances
	 * @throws IOException     if an I/O error occurs
	 */
	public static Map parseLog(InputStream is) throws IOException {
		Map pidNameMap = new HashMap();
		Properties props = new Properties();
		props.load(is);
		for (Iterator i=props.keySet().iterator(); i.hasNext(); ) {
			String pid = (String)i.next();
			String cmdDesc = props.getProperty(pid);
			String[] tokens = cmdDesc.split("\\s*\\n\\s*");
			pidNameMap.put(new Integer(pid), tokens);
		}
		log.fine("Parsed " + pidNameMap.size() + " pid-name mappings");
		return pidNameMap;
	}
	
}
