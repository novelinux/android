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

/**
 * BootStats encapsulates boot statistics.  This includes global CPU and
 * disk I/O statistics and a process tree with process accounting.
 */
public class BootStats {
	/** CPU statistics.*/
	public Stats cpuStats;
	/** Disk I/O utilization and throughput statistics.*/
	public Stats diskStats;
	/** The process tree.*/
	public ProcessTree procTree;
	
	/**
	 * Creates a new boot statistics instance.
	 * 
	 * @param cpuStats   CPU statistics
	 * @param diskStats  disk utilization and throughput I/O statistics
	 * @param procTree   the process tree
	 */
	public BootStats(Stats cpuStats, Stats diskStats, ProcessTree procTree) {
		this.cpuStats = cpuStats;
		this.diskStats = diskStats;
		this.procTree = procTree;
	}
}
