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
import java.util.Date;
import java.util.List;

/**
 * Process encapsulation.
 */
public class Process implements Comparable {
	/** Undefined state. */
	public static final int STATE_UNDEFINED = 0;
	/** Running state. */
	public static final int STATE_RUNNING   = 1;
	/** Sleeping state. */
	public static final int STATE_SLEEPING  = 2;
	/** Uninterruptible sleep. */
	public static final int STATE_WAITING   = 3;
	/** Stopped or traced. */
	public static final int STATE_STOPPED   = 4;
	/** Zombie state (defunct). */
	public static final int STATE_ZOMBIE    = 5;
	
	/** Process ID. */
	public int pid;
	
	/** Command line. */
	public String cmd;
	
	/** Process decription (e.g. PID and command, script stack trace, etc.). */
	public String desc;

	/** Process start time. */
	public Date startTime;
	
	/** Process duration in milliseconds. */
	public long duration;

	/** Parent process. */
	public Process parent;
	
	/** Parent process ID. */
	public int ppid;

	/** A list of children <code>Process</code>es. */
	public List childList;
	
	/** A list of process statistics samples. */
	public List samples;
	
	/**
	 * Whether the process is active.  A process is active if it contains at
	 * least one non-sleeping sample.  Idle processes are optionally
	 * filtered out.
	 */
	public boolean active = false;
	
	
	/**
	 * Created a new process.
	 * 
	 * @param pid     process ID
	 * @param cmd     command line
	 */
	public Process(int pid, String cmd) {
		this.pid = pid;
		this.cmd = cmd;
		this.ppid = -1;
		this.duration = -1;
		this.childList = new ArrayList();
		this.samples = new ArrayList();
	}
	
	/**
	 * Returns a string representation of the process.
	 * 
	 * @return  string representation
	 */
	public String toString() {
		return (pid + " " + cmd);
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		if (!(o instanceof Process)) {
			throw new ClassCastException();
		}
		int p1 = pid;
		String c1 = cmd;
		int p2 = ((Process)o).pid;
		String c2 = ((Process)o).cmd;
		
		if (p1 == p2) {
			return c1.compareTo(c2);
		} else {
			return p1 - p2;
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object o) {
		if (!(o instanceof Process)) {
			throw new ClassCastException();
		}
		return hashCode() == o.hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return (pid + ":" + cmd).hashCode();
	}
}
