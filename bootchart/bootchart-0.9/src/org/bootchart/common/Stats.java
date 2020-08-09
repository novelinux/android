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

import java.util.ArrayList;
import java.util.List;

/**
 * Stats encapsulates performance data statistics.
 */
public class Stats {
	/** A list of statistics samples. */
	private List samples;
	
	/**
	 * Creates a new  statistics instance.
	 */
	public Stats() {
		samples = new ArrayList();
	}

	/**
	 * Adds a new statistics sample.
	 * 
	 * @param sample  statistics sample to add
	 */
	public void addSample(Sample sample) {
		samples.add(sample);
	}
	
	/**
	 * Returns a list of statistics samples.
	 * 
	 * @return  a list statistics samples
	 */
	public List getSamples() {
		return samples;
	}
}
