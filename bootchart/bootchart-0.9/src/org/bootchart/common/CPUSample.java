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
 * CPU statistics sample.
 */
public class CPUSample extends Sample {
	/** User load. */
	public double user;
	/** System load. */
	public double sys;
	/** The percentage of CPU time spent waiting on disk I/O. */
	public double io;

	/**
	 * Creates a new sample.
	 *
	 * @param time  sample time
	 * @param user  user load
	 * @param sys   system load
	 * @param io    IO wait
	 */
	public CPUSample(Date time, double user, double sys, double io) {
		this.time = time != null ? new Date(time.getTime()) : null;
		this.user = user;
		this.sys = sys;
		this.io = io;
	}

	/**
	 * Returns the string representation of the sample.
	 * 
	 * @return  string representation
	 */
	public String toString() {
		String t = time != null ? time.getTime() + "\t" : "";
		return t + user + "\t" + sys + "\t" + io;
	}
}
