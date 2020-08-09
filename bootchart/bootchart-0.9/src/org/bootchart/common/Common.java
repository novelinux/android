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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Common methods.
 */
public class Common {
	/** Program version. */
	public static final String VERSION = "bootchart 0.9";
	
	/** Default locale. */
	public static final Locale LOCALE = Locale.US;
	
	/** A list of processes which should include parameters in their command lines. */ 
	public static final List PROC_PARAM = Arrays.asList(new String[]{
		"init", "udev", "initlog", "modprobe", "rc", "ifup", "ifconfig",
		"cat", "sed", "awk", "grep",
		"hotplug", "default.hotplug", "05-wait_for_sysfs.hotplug", "20-hal.hotplug" }
	);
	
	/** The maximum number of samples to parse. */
	public static final int MAX_PARSE_SAMPLES = 15 * 60 * 5; // 15 min.
	
	/** Process description date format. */
	private static final DateFormat DESC_TIME_FORMAT =
		new SimpleDateFormat("mm:ss.SSS", LOCALE);
	
	/**
	 * File name filter for bootchart log files.
	 */
	public static class LogFileFilter implements FilenameFilter {
		/* (non-Javadoc)
		 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
		 */
		public boolean accept(File dir, String name) {
			return name.startsWith("bootchart") && name.endsWith(".tgz");
		}
	}
	
	/**
	 * Loads the contents of the file.  The file is either read from the
	 * file system or retrieved as resource stream.
	 * 
	 * @param file  the file to read
	 * @return      file contents string
	 * @throws IOException            if an I/O error occurs
	 */
	public static String loadFile(File file) throws IOException {
		InputStream is = null;
		String path = file.getPath();
		if (file.exists()) {
			is = new FileInputStream(path);
		} else {
			path = "/" + path.replaceFirst("\\./", "");
			is = Common.class.getResourceAsStream(path);
		}
		if (is == null) {
			throw new FileNotFoundException("File or resource " + path + " not found");
		}
		try {
			byte[] buff = new byte[8096];
			StringBuffer sb = new StringBuffer();
			while (true) {
				int read = is.read(buff);
				if (read < 0) {
					break;
				}
				sb.append(new String(buff, 0, read));
			}
			return sb.toString();
		} finally {
			is.close();
		}
	}
	
	/**
	 * Returns a buffered reader suitable for reading the input stream.
	 *  
	 * @param is            input stream to read
	 * @return              buffered reader
	 * @throws IOException  if an I/O error occurs
	 */
	public static BufferedReader getReader(InputStream is) throws IOException {
		BufferedReader reader =
			new BufferedReader(new InputStreamReader(is, "ISO-8859-1"));
		return reader;
	}
	
	/**
	 * Returns a new <code>double</code> initialized to the value
	 * represented by the specified <code>String</code>.  Any decimal commas
	 * in the string are replaced with dots.
	 * 
	 * @param s  the string to be parsed
	 * @return   the double value represented by the string argument
	 */
	public static double parseDouble(String s) {
		return Double.parseDouble(s.replaceAll(",", "."));
	}
	
	
	/**
	 * Format the specified command line.  Shell invocations, paths and
	 * parameters are removed (e.g. "/bin/bash /etc/rc.d/rc.sysinit" ->
	 * "rc.sysinit").  Paramaters are included for certain commands
	 * (e.g. modprobe and rc).
	 * 
	 * @param cmd  command line
	 * @return     a trimed command line
	 */
	public static String formatCommand(String cmd) {
		if (cmd == null) {
			return null;
		}
		cmd = cmd.replaceFirst(" <defunct>", "");
		if (cmd.matches("\\[.+\\]")) {
			cmd = cmd.substring(1, cmd.length() - 1);
		}
		String[] tokens = cmd.trim().split("\\s+");
		for (int i=0; i<tokens.length; i++) {
			if (i == 0 && tokens.length > 1 &&
				(tokens[i].matches(".*/?sh") || tokens[i].matches(".*/?bash")
				|| tokens[i].matches(".*/?python") || tokens[i].matches(".*/?perl"))) {
				continue;
			} else if (tokens[i].startsWith("-") && tokens.length > 1) {
				continue;
			} else {
				String fcmd = tokens[i];
				if (fcmd.startsWith("/") || fcmd.startsWith("./")
					|| fcmd.startsWith("../")) {
					fcmd = fcmd.substring(fcmd.lastIndexOf('/') + 1);
				}
				StringBuffer sb = new StringBuffer();
				if (PROC_PARAM.contains(fcmd)) {
					for (int j=i+1; j<tokens.length; j++) {
						if (!tokens[j].startsWith("-")) {
							sb.append(" " + formatCommand(tokens[j]));
							break;
						}
					}
				}
				fcmd += sb.toString();
				return fcmd;
			}
		}
		return null;
	}
	
	/**
	 * Returns the text to include in the process description pop-up.
	 * The description includes the PID, command name, start time, duration
	 * and any user-specified description (e.g. script stack trace).
	 * 
	 * @param proc       the process to get description for
	 * @param startTime  process tree start time
	 * @return           a multiline process description text
	 */
	public static String getProcessDesc(Process proc, Date startTime) {
		StringBuffer sb = new StringBuffer();
		sb.append(proc.pid + " " + proc.cmd + "\n");
		Date stime =
			new Date(proc.startTime.getTime() - startTime.getTime());
		sb.append("Start time: " + DESC_TIME_FORMAT.format(stime) + "\n");
		sb.append("Duration: "
			      + DESC_TIME_FORMAT.format(new Date(proc.duration)));
		if (proc.desc != null && proc.desc.length() > 0) {
			sb.append("\n\n" + proc.desc);
		}
		return sb.toString();
	}
	
	
	/**
	 * Whether the running JVM supports PNG encoding.  Some runtime
	 * environments (e.g. those based on GNU Classpath) don't have the
	 * necessary Graphics2D support.
	 * 
	 * @return  whether this JVM supports PNG encoding
	 */
	public static boolean isPNGSupported() {
		String vm = System.getProperty("java.vm.info");
		// disable PNG encoding with jamvm and gcj
		if (vm == null || vm.indexOf("gcj") != -1) {
			return false;
		} else {
			return true;
		}
	}
}
