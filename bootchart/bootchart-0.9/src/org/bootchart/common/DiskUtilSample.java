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

/**
 * Disk I/O utilization sample.
 */
public class DiskUtilSample extends Sample {
	/** Disk utilization [0.0, 1.0]. */
	public double util;
	
	/**
	 * Creates a new sample.
	 * 
	 * @param time   sample time
	 * @param util   disk utilization
	 */
	public DiskUtilSample(Date time, double util) {
		this.time = time != null ? new Date(time.getTime()) : null;
		this.util = util;
	}
	
	/**
	 * Returns the string representation of the sample.
	 * 
	 * @return  string representation
	 */
	public String toString() {
		return TIME_FORMAT.format(time) + "\t" + util;
	}
}
