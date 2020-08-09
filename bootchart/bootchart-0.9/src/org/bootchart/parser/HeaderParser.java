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
package org.bootchart.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * HeaderParser parses the <code>header</code> log file, which contains
 * the chart title and basic information about the system, OS release,
 * CPU, etc.
 */
public class HeaderParser {
	/**
	 * Parses the header log file.  The <code>Properties</code> instance
	 * should contain at least the following values:
	 * <ul>
	 *   <li>title</li> (e.g. "Boot chart for serenity.klika.si (Sun Nov 21 01:48:05 CET 2004)")
	 *   <li>system.uname</li> (e.g. "Linux 2.6.7 #2 Thu Jul 1 14:25:23 CEST 2004 i686 GNU/Linux")
	 *   <li>system.release</li> (e.g. "Fedora Core release 3 (Heidelberg)")
	 *   <li>system.cpu</li> (e.g. "Intel(R) Pentium(R) M processor 1500MHz")
	 *   <li>system.kernel.options</li> (e.g. "cmdline: ro root=/dev/vg0/root vga=0x318 rhgb")
	 * </ul>
	 * 
	 * @param is  the input stream to read from
	 * @return    header properties
	 * @throws IOException  if an I/O error occurs
	 */
	public static Properties parseLog(InputStream is) throws IOException {
		Properties props = new Properties();
		props.load(is);
		return props;
	}
	
	/**
	 * Parses the header log file (old version).
	 * 
	 * @param reader  the reader to read from
	 * @return        header properties
	 * @throws IOException  if an I/O error occurs
	 */
	public static Properties oldParseLog(BufferedReader reader) throws IOException {
		Properties props = new Properties();
		String line = reader.readLine();
		List headerList = new ArrayList();
		reader.mark(8096);
		while (line != null && line.trim().length() > 0) {
			if (line.startsWith("#")) {
				headerList.add(line.replaceFirst("#", "").trim());
			}
			reader.mark(8096);
			line = reader.readLine();
		}
		reader.reset();
		if (headerList.size() > 4) {
			props.put("title",                 headerList.get(0));
			props.put("system.uname",          headerList.get(1));
			props.put("system.release",        headerList.get(2));
			props.put("system.cpu",            headerList.get(3));
			props.put("system.kernel.options", headerList.get(4));
		}
		return props;
	}
	
	/**
	 * Get the number of CPUs from the <code>system.cpu</code> header
	 * property.
	 * 
	 * @param headers  header properties
	 * @return         the number of CPUs
	 */
	public static int getNumCPUs(Properties headers) {
		if (headers == null) {
			return 1;
		}
		String cpuModel = (String)headers.get("system.cpu");
		if (cpuModel == null) {
			return 1;
		}
		Pattern pat = Pattern.compile(".*\\((\\d+)\\)");
		Matcher mat = pat.matcher(cpuModel);
		if (mat.matches()) {
			return Integer.parseInt(mat.group(1));
		} else {
			return 1;
		}
	}
}
