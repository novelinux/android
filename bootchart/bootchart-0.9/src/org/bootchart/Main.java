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

package org.bootchart;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.compress.tar.TarEntry;
import org.apache.commons.compress.tar.TarInputStream;
import org.bootchart.common.BootStats;
import org.bootchart.common.Common;
import org.bootchart.common.ProcessTree;
import org.bootchart.common.PsStats;
import org.bootchart.common.Stats;
import org.bootchart.parser.HeaderParser;
import org.bootchart.parser.linux.PacctParser;
import org.bootchart.parser.linux.PidNameParser;
import org.bootchart.parser.linux.ProcDiskStatParser;
import org.bootchart.parser.linux.ProcPsParser;
import org.bootchart.parser.linux.ProcStatParser;
import org.bootchart.parser.linux.PsParser;
import org.bootchart.renderer.EPSRenderer;
import org.bootchart.renderer.PNGRenderer;
import org.bootchart.renderer.Renderer;
import org.bootchart.renderer.SVGRenderer;


/**
 * Bootchart main class.
 */
public class Main {
	private static final Logger log = Logger.getLogger(Main.class.getName());
	
	/** Default image format. */
	private static final String DEFAULT_FORMAT =
		Common.isPNGSupported() ? "png" : "svg";

	/**
	 * Main.
	 * 
	 * @param args        command arguments
	 * @throws Exception  if an error occurs
	 */
	public static void main(String[] args) throws Exception {
		long time = System.currentTimeMillis();

		Options options = getOptions();
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			printUsage(options);
			System.exit(1);
		}
		
		if (cmd.hasOption("h")) {
			printUsage(options);
			System.exit(0);
		}
		if (cmd.hasOption("v")) {
			System.out.println(Common.VERSION);
			System.exit(0);
		}

		boolean prune = true;
		File outputDir = new File(".");
		String format = DEFAULT_FORMAT;
		
		if (cmd.hasOption("o")) {
			outputDir = new File(cmd.getOptionValue("o"));
		}
		if (cmd.hasOption("f")) {
			format = cmd.getOptionValue("f");
		}
		if (cmd.hasOption("n")) {
			prune = false;
		}

		List inputFiles = new ArrayList();
		String[] fileArgs = cmd.getArgs();
		for (int i=0; i<fileArgs.length; i++) {
			File inputFile = new File(fileArgs[i]);
			if (!inputFile.exists()) {
				System.err.println(inputFile + " not found");
				System.exit(1);
			}
			if (inputFile.isDirectory()) {
				File[] dirFiles =
					inputFile.listFiles(new Common.LogFileFilter());
				inputFiles.addAll(Arrays.asList(dirFiles));
			} else {
				inputFiles.add(inputFile);
			}
		}

		if (fileArgs.length == 0) {
			File logTarball = new File("/var/log/bootchart.tgz");
			File logDir = new File("/var/log/bootchart");
			if (logTarball.exists()) {
				inputFiles.add(logTarball);
			} else if (logDir.exists()) {
				inputFiles.add(logDir);
			} else {
				System.err.println(logTarball + " not found");
				System.exit(1);
			}
		}
		
		for (Iterator i=inputFiles.iterator(); i.hasNext(); ) {
			File file = (File)i.next();
			System.out.println("Parsing " + file.getPath());
			String name = file.getName();
			name = name.replaceFirst("\\.?tgz$", "");
			name = name.replaceFirst("\\.?gz$", "");
			name = name.replaceFirst("\\.?tar$", "");
			String fileName = outputDir + "/" + name;
			render(file, format, prune, fileName);
		}

		time = System.currentTimeMillis() - time;
		log.fine("Bootchart took " + time + "ms");
	}
	
	/**
	 * Parses the bootchart log tarball from the log tarball or directory and
	 * renders the chart to the output stream.
	 * 
	 * @param logFile        log tarball or directory
	 * @param format         image format (png, svg or eps)
	 * @param prune          whether to prune the tree
	 * @param fileName       file name prefix
	 * @return               rendered image file path
	 * @throws IOException               if an I/O error is thrown
	 */
	public static String render(File logFile, String format, boolean prune,
		                        String fileName) throws IOException {

		if (!logFile.exists()) {
			return null;
		}
		TarInputStream tis = null;
		File[] files = null;
		boolean isLogTarball; // whether it's a tarball or dir
		if (logFile.isFile()) {
			isLogTarball = true;
			InputStream is = new FileInputStream(logFile);
			if (logFile.getName().endsWith("gz")) {
				is = new GZIPInputStream(is);
			}
			tis = new TarInputStream(is);
		} else {
			isLogTarball = false;
			files = logFile.listFiles();
			Arrays.sort(files);
		}
		
		Properties headers = new Properties();
		Map pidNameMap = null;
		Map forkMap = null;
		Stats cpuStats = null;
		Stats diskStats = null;
		PsStats psStats = null;
		BootStats bootStats = null;
		int numCpu = 1;
		
		String monitoredApp = null; // used for profiling specific processes
		
		boolean hasMoreFiles = true;
		int fileI = 0;
		while (hasMoreFiles) {
			String logName = null;
			InputStream is = null;
			TarEntry tarEntry = null;
			if (isLogTarball) {
				tarEntry = tis.getNextEntry();
				if (tarEntry == null) {
					hasMoreFiles = false;
					break;
				}
				logName = tarEntry.getName();
				is = tis;
			} else {
				logName = files[fileI].getName();
				is = new FileInputStream(files[fileI]);
				fileI++;
				if (fileI >= files.length) {
					hasMoreFiles = false;
				}
			}
			log.fine("Parsing log file: " + logName);

			if (logName.equals("header")) {
				// read the headers
				headers = HeaderParser.parseLog(is);
				if (headers != null) {
					monitoredApp = headers.getProperty("profile.process");
				}
				numCpu = HeaderParser.getNumCPUs(headers);
			
			} else if (logName.equals("ps.log")) {
				// read ps log file
				psStats = PsParser.parseLog(is, pidNameMap, forkMap);
			
			} else if (logName.equals("proc_ps.log")) {
				// read the /proc/[PID]/stat log file
				psStats = ProcPsParser.parseLog(is, pidNameMap, forkMap);
				
			} else if (logName.equals("proc_stat.log")) {
				// read the /proc/stat log file
				cpuStats =  ProcStatParser.parseLog(is);
				
			} else if (logName.equals("proc_diskstats.log")) {
				// read the /proc/diskstats log file
				diskStats =  ProcDiskStatParser.parseLog(is, numCpu);
				
			} else if (logName.equals("kernel_pacct")) {
				// parse process forking PID mappings
				forkMap = PacctParser.parseLog(is);

			} else if (logName.equals("init_pidname.log")) {
				// map pids to command names (useful for Gentoo, where most init
				// processes are sourced and thus shown as "rc boot" or
				// "rc default")
				pidNameMap = PidNameParser.parseLog(is);
				
			} else {
				log.warning("Unknown log file: " + logName);
				if (logName.length() > 32) {
					// We're getting garbage from TarInputStream.  Break out since
					// a malformed tarball can cause spinning.
					hasMoreFiles = false;
					break;
				}
			}
		}
		
		long opTime = 0;
		if (bootStats == null) {
			ProcessTree procTree = new ProcessTree(null, monitoredApp, prune);
			if (psStats == null || psStats.processList.size() == 0) {
				log.warning("No process samples");
			} else {
				opTime = System.currentTimeMillis();
				procTree = new ProcessTree(psStats, monitoredApp, prune);
				opTime = System.currentTimeMillis() - opTime;
				log.fine("Tree generation and pruning took " + opTime + " ms");
				
				if (procTree.processTree.size() == 0 || procTree.duration == 0) {
					log.warning("No processes found");
				}
			}
			//log.fine(procTree.toString());
			bootStats = new BootStats(cpuStats, diskStats, procTree);
		}
		
		Renderer renderer = null;
		opTime = System.currentTimeMillis();
		if ("png".equals(format)) {
			renderer = new PNGRenderer();
		} else if ("svg".equals(format)) {
			renderer = new SVGRenderer();
		} else if ("eps".equals(format)) {
			renderer = new EPSRenderer();
		} else {
			throw new IllegalArgumentException("Invalid format: " + format);
		}
		fileName += "." + renderer.getFileSuffix();
		FileOutputStream fos = new FileOutputStream(fileName);
		renderer.render(headers, bootStats, fos);
		fos.close();
		log.fine("Wrote image: " + fileName);
		System.out.println("Wrote image: " + fileName);
		opTime = System.currentTimeMillis() - opTime;
		log.fine("Render time: " + opTime + " ms)");
		return fileName;
	}

	/**
	 * Returns the command line options.
	 * 
	 * @return  CLI options
	 */
	private static Options getOptions() {
		Options options = new Options();
		Option opt = null;
		options.addOption("h", "help", false,
			              "print this message");
		options.addOption("v", "version", false,
			              "print version and exit");
		
		opt = new Option("f", "format", true,
			             "image format (png | eps | svg; default: " + DEFAULT_FORMAT + ")");
		opt.setArgName("format");
		options.addOption(opt);
		
		opt = new Option("o", "output-dir", true,
			             "output directory where images are stored (default: .)");
		opt.setArgName("dir");
		options.addOption(opt);
		
		options.addOption("n", "no-prune", false,
			              "do not prune the process tree");
		return options;
	}
	
	/**
	 * Prints the usage help message.
	 * 
	 * @param options  CLI options
	 */
	private static void printUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("bootchart [OPTION]... [FILE]...", options);
	}
}
