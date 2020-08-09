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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bootchart.common.Common;
import org.bootchart.common.DiskTPutSample;
import org.bootchart.common.DiskUtilSample;
import org.bootchart.common.Sample;
import org.bootchart.common.Stats;


/**
 * ProcDiskStatParser parses log files produced by logging the output of
 * <code>/proc/diskstats</code>.  The samples contain information about disk
 * IO activity.
 */
public class ProcDiskStatParser {
	private static final Logger log = Logger.getLogger(ProcDiskStatParser.class.getName());

	private static final String DISK_REGEX = "hd.|sd.";
	
	/** DiskStatSample encapsulates a /proc/diskstat sample. */
	private static class DiskStatSample {
		long[] values  = new long[3]; // {rsect, wsect, use}
		long[] changes = new long[3]; // {rsect, wsect, use}
	}
	
	/**
	 * Parses the <code>proc_diskstats.log</code> file.  The output from
	 * <code>/proc/diskstat</code> is used to collect the disk statistics.
	 * 
	 * @param is      the input stream to read from
	 * @param numCpu  number of processors
	 * @return        disk statistics ({@link DiskUtilSample} and
	 *                {@link DiskTPutSample} samples)
	 * @throws IOException  if an I/O error occurs
	 */
	public static Stats parseLog(InputStream is, int numCpu)
		throws IOException {
		BufferedReader reader = Common.getReader(is);
		String line = reader.readLine();

		int numSamples = 0;
		Stats diskStats = new Stats();
		// last time
		Date ltime = null;
		// a map of /proc/diskstat values for each disk
		Map diskStatMap = new HashMap();
		
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
			
			// read stats for all disks
			line = reader.readLine();
			while (line != null && line.trim().length() > 0) {
				line = line.trim();
				// {major minor name rio rmerge rsect ruse wio wmerge wsect wuse running use aveq}
				String[] tokens = line.split("\\s+");
				if (tokens.length != 14 || !tokens[2].matches(DISK_REGEX)) {
					line = reader.readLine();
					continue;
				}
				String disk = tokens[2];
				
				long rsect = Long.parseLong(tokens[5]);
				long wsect = Long.parseLong(tokens[9]);
				long use = Long.parseLong(tokens[12]);
				DiskStatSample sample = (DiskStatSample)diskStatMap.get(disk);
				if (sample == null) {
					sample = new DiskStatSample();
					diskStatMap.put(disk, sample);
				}
				if (ltime != null) {
					sample.changes[0] = rsect - sample.values[0];
					sample.changes[1] = wsect - sample.values[1];
					sample.changes[2] = use - sample.values[2];
				}
				sample.values = new long[]{rsect, wsect, use};
				line = reader.readLine();
			}
			if (ltime != null) {
				long interval = time.getTime() - ltime.getTime();
				interval = Math.max(interval, 1);
				
				// sum up changes for all disks
				long[] sums = new long[3];
				for (Iterator i=diskStatMap.entrySet().iterator(); i.hasNext(); ) {
					Map.Entry entry = (Map.Entry)i.next();
					DiskStatSample sample = (DiskStatSample)entry.getValue();
					for (int j=0; j<3; j++) {
						sums[j] += sample.changes[j];
					}
				}
				
				// sector size is 512 B
				double readTPut = sums[0] / 2.0 * 1000.0 / interval;
				double writeTPut = sums[1] / 2.0 * 1000.0/ interval;
				// number of ticks (1000/s), reduced to one CPU
				double util = (double)sums[2] / interval / numCpu;
				util = Math.max(0.0, Math.min(1.0, util));
				
				DiskTPutSample tputSample = new DiskTPutSample(time, readTPut, writeTPut);
				DiskUtilSample utilSample = new DiskUtilSample(time, util);
				diskStats.addSample(tputSample);
				diskStats.addSample(utilSample);
			}
			ltime = time;
			if (numSamples > Common.MAX_PARSE_SAMPLES) {
				break;
			}
		}
		log.fine("Parsed " + diskStats.getSamples().size() + " /proc/diskstats samples");
		return diskStats;
	}
	
	/**
	 * Returns the maximum throughput seen in the iostat sample list.
	 * 
	 * @param ioSampleList  iostat sample list
	 * @return              maximum throughput
	 */
	public static double getMaxDiskTPut(List ioSampleList) {
		double maxTPut = 0.0;
		for (Iterator i = ioSampleList.iterator(); i.hasNext();) {
			Sample sample = (Sample)i.next();
			if (sample instanceof DiskTPutSample) {
				DiskTPutSample tputSample = (DiskTPutSample)sample;
				double tput = tputSample.read + tputSample.write;
				if (tput > maxTPut) {
					maxTPut = tput;
				}
			}
		}
		return maxTPut;
	}
}
