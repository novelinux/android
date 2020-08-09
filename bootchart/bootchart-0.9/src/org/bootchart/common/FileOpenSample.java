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

import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Disk I/O utilization sample.
 */
public class FileOpenSample extends Sample {
	/** Number of file opens. */
	public int fileOpens;
	
	/**
	 * Creates a new sample.
	 * 
	 * @param time       sample time
	 * @param fileOpens  number of file open operations
	 */
	public FileOpenSample(Date time, int fileOpens) {
		this.time = time != null ? new Date(time.getTime()) : null;
		this.fileOpens = fileOpens;
	}
	
	/**
	 * Returns the string representation of the sample.
	 * 
	 * @return  string representation
	 */
	public String toString() {
		return TIME_FORMAT.format(time) + "\t" + fileOpens;
	}
	
	/**
	 * Returns the maximum number of file opens seen in the sample list.
	 * 
	 * @param ioSampleList  iostat sample list
	 * @return              maximum number of file opens
	 */
	public static int getMaxFileOpens(List ioSampleList) {
		int maxFiles = 0;
		for (Iterator i = ioSampleList.iterator(); i.hasNext();) {
			Sample sample = (Sample)i.next();
			if (sample instanceof FileOpenSample) {
				FileOpenSample fopenSample = (FileOpenSample)sample;
				if (fopenSample.fileOpens > maxFiles) {
					maxFiles = fopenSample.fileOpens;
				}
			}
		}
		return maxFiles;
	}
}
