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
import java.util.Date;
import java.util.logging.Logger;

import org.bootchart.common.CPUSample;
import org.bootchart.common.Common;
import org.bootchart.common.Stats;


/**
 * ProcStatParser parses log files produced by logging the output of
 * <code>/proc/stat</code>.  The samples contain information about CPU times:
 * user, nice, system and idle; 2.6 kernels also include io_wait, irq and
 * softirq.
 */
public class ProcStatParser {
	private static final Logger log = Logger.getLogger(ProcStatParser.class.getName());

	/**
	 * Parses the <code>proc_stat.log</code> file.  The output from
	 * <code>/proc/stat</code> is used to collect the CPU statistics.
	 * 
	 * @param is            the input stream to read from
	 * @return              CPU statistics ({@link CPUSample} samples)
	 * @throws IOException  if an I/O error occurs
	 */
	public static Stats parseLog(InputStream is)
		throws IOException {
		BufferedReader reader = Common.getReader(is);
		String line = reader.readLine();

		int numSamples = 0;
		Stats cpuStats = new Stats();
		
		// CPU times {user, nice, system, idle, io_wait, irq, softirq}
		long[] ltimes = null;
		
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
			Date time = null;
			if (line.matches("^\\d+$")) {
				time = new Date(Long.parseLong(line) * 10);
				numSamples++;
			} else {
				line = reader.readLine();
				continue;
			}
			line = reader.readLine();
			String[] tokens = line.split("\\s+");
			// {user, nice, system, idle, io_wait, irq, softirq}
			long[] times = new long[7];
			for (int i=1; i<tokens.length; i++) {
				if (i - 1 < times.length) {
					times[i - 1] = Long.parseLong(tokens[i]);
				}
			}
			if (ltimes != null) {
				// user + nice
				long user = (times[0] + times[1]) - (ltimes[0] + ltimes[1]);
				// system + irq + softirq
				long system = (times[2] + times[5] + times[6]) -
				              (ltimes[2] + ltimes[5] + ltimes[6]);
				long idle = times[3] - ltimes[3];
				long iowait = times[4] - ltimes[4];
				double sum = user + system + idle + iowait;
				sum = Math.max(sum, 1); // avoid an ArithmeticException
				CPUSample sample =
					new CPUSample(time, user/sum, system/sum, iowait/sum);
				cpuStats.addSample(sample);
			}
			ltimes = times;
			if (numSamples > Common.MAX_PARSE_SAMPLES) {
				break;
			}
			// skip the rest of statistics lines
			while (line != null && line.trim().length() > 0) {
				line = reader.readLine();
			}
		}
		log.fine("Parsed " + cpuStats.getSamples().size() + " /proc/stat samples");
		return cpuStats;
	}
}
