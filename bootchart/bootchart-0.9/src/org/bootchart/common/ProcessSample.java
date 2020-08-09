package org.bootchart.common;

import java.util.Date;

/**
 * Process statistics.
 */
public class ProcessSample extends Sample {
	/** Process state. */
	public int state;
	/** CPU statistics. */
	public CPUSample cpu;
	/** Disk utlization statistics. */
	public DiskUtilSample diskUtil;
	/** Disk troughput statistics. */
	public DiskTPutSample diskTPut;
	
	/**
	 * Creates a new process sample.
	 * 
	 * @param time      sample time
	 * @param state     proces state
	 * @param cpu       CPU sample
	 * @param diskUtil  disk utilization sample
	 * @param diskTPut  disk throughput sample
	 */
	public ProcessSample(Date time, int state, CPUSample cpu,
		                 DiskUtilSample diskUtil, DiskTPutSample diskTPut) {
		this.time = time != null ? new Date(time.getTime()) : null;
		this.state = state;
		this.cpu = cpu;
		this.diskUtil = diskUtil;
		this.diskTPut = diskTPut;
	}
	
	/**
	 * Returns the string representation of the sample.
	 * 
	 * @return  string representation
	 */
	public String toString() {
		return time.getTime() + "\t" + state + "\t" + cpu + "\t" + diskUtil + "\t" + diskTPut;
	}
}
