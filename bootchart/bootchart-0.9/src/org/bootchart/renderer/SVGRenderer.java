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
package org.bootchart.renderer;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import org.bootchart.common.BootStats;
import org.bootchart.common.CPUSample;
import org.bootchart.common.Common;
import org.bootchart.common.DiskTPutSample;
import org.bootchart.common.DiskUtilSample;
import org.bootchart.common.FileOpenSample;
import org.bootchart.common.Process;
import org.bootchart.common.ProcessSample;
import org.bootchart.common.ProcessTree;
import org.bootchart.common.Sample;
import org.bootchart.common.Stats;
import org.bootchart.parser.linux.ProcDiskStatParser;


/**
 * PNGRenderer renders the boot chart as a PNG image.
 */
public class SVGRenderer extends Renderer {
	//private static final Logger log = Logger.getLogger(SVGRenderer.class.getName());
	
	/** Chart SVG template file. */
	private static final File CHART_SVG_TEMPLATE = new File("svg/bootchart.svg.template");
	/** Process SVG template file. */
	private static final File PROCESS_SVG_TEMPLATE = new File("svg/process.svg.template");
	
	/** SVG style sheet. */
	private static final File SVG_CSS_FILE = new File("svg/style.css");
	
	/** Whether to compress SVG output. */
	private static final boolean COMPRESS_SVG = true;
	
	private static final DateFormat BOOT_TIME_FORMAT =
		new SimpleDateFormat("m:ss", Common.LOCALE);
	private static final int MIN_IMG_W = 800;
	
	
	/**
	 * Whether to inline the CSS file.  Some SVG renderers (e.g. ksvg) do not
	 * support CSS so the style properties are parsed from the CSS file and
	 * inlined in the SVG document.
	 */
	private static final boolean INLINE_CSS = true;
	
	
	/**
	 * Render the chart.
	 * 
	 * @param headers       header properties to include in the title banner
	 * @param bootStats     boot statistics
	 * @param os            the output stream to write t
	 * @throws IOException  if an I/O error occurs
	 */
	public void render(Properties headers, BootStats bootStats, OutputStream os)
		throws IOException {
		
		Stats diskStats = bootStats.diskStats;
		Stats cpuStats = bootStats.cpuStats;
		ProcessTree procTree = bootStats.procTree;
		
		long dur = procTree.duration;
		int secW = 25;
		int w = (int)(dur / 1000 * secW);
		int procH = 16;
		int headerH = 300;
		int h = procH * procTree.numProc + headerH;
		
		String dimension = "width=\"" + Math.max(w, MIN_IMG_W) + "px\" height=\"" + (h + 1) + "px\"";
		/*
		 * Load the bootchart SVG template.  Its arguments are:
		 *  0: Dimension
		 *  1: Style sheet
		 *  2: Title
		 *  3: System information
		 *  4: OS information
		 *  5: CPU information
		 *  6: Kernel cmdline
		 *  7: Boot time
		 *  8: CPU/IO load time ticks
		 *  9: IO chart
		 * 10: CPU load chart
		 * 11: Disk utilization time ticks
		 * 12: Disk utilization chart
		 * 13: Disk throughput chart
		 * 14: Files opened chart
		 * 15: Process axis labels
		 * 16: Process time ticks
		 * 17: Process chart (formatted using the process template)
		 */
		String chartTemplate = Common.loadFile(CHART_SVG_TEMPLATE);
		
		String style = "";
		if (!INLINE_CSS) {
			style = Common.loadFile(SVG_CSS_FILE);
			// indent
			style = style.replaceAll("\n", "\n\t\t\t");
			style = style.trim();
		}
		
		String title = "";
		String uname = "";
		String release = "";
		String cpu = "";
		String kopt = "";
		if (headers != null) {
			title   = encodeArg(headers.getProperty("title", ""));
			uname   = "uname: " + encodeArg(headers.getProperty("system.uname", ""));
			release = "release: " + encodeArg(headers.getProperty("system.release", ""));
			cpu     = "CPU: " + encodeArg(headers.getProperty("system.cpu", ""));
			cpu     = cpu.replaceFirst("model name\\s*:\\s*", "");
			if (headers.getProperty("profile.process") != null) {
				kopt = "application: " + encodeArg(headers.getProperty("profile.process"));
			} else {
				kopt = "kernel options: " + encodeArg(headers.getProperty("system.kernel.options", ""));
			}
		}
		
		Date dDur = new Date(Math.round(dur / 1000.0) * 1000);
		String bootTime = "time: " + BOOT_TIME_FORMAT.format(dDur);
		
		/*
		 * Render CPU/IO chart
		 */
		
		// time ticks
		int barH = 50;
		StringBuffer ticksBuff = new StringBuffer();
		for (int i=0; i<=dur/1000; i++) {
			int x = i * secW;
			String tclass = (i % 5 == 0) ? "class=\"Bold\" " : "";
			ticksBuff.append("<line " + tclass + "x1=\"" + x + "\" "
				+ "y1=\"0\" x2=\"" + x + "\" y2=\"" + barH + "\"/>\n");
		}
		String cpuTicks = ticksBuff.toString();
		cpuTicks = cpuTicks.replaceAll("\n", "\n\t\t\t");
		cpuTicks = cpuTicks.trim();
		ticksBuff = null;
		
		// IO wait
		StringBuffer ioPoints = new StringBuffer();
		StringBuffer cpuPoints = new StringBuffer();
		if (cpuStats != null) {
			int lastX = 0;
			for (Iterator i = cpuStats.getSamples().iterator(); i.hasNext();) {
				CPUSample sample = (CPUSample)i.next();
				int posX = 
					(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
				int posY = barH - (int)((sample.user + sample.sys + sample.io) * barH);
				if (ioPoints.length() == 0) {
					ioPoints.append(posX + "," + barH);
				}
				ioPoints.append(" " + posX + "," + posY);
				lastX = posX;
			}
			ioPoints.append(" " + lastX + "," + barH);
			
			// CPU load
			lastX = 0;
			for (Iterator i = cpuStats.getSamples().iterator(); i.hasNext();) {
				CPUSample sample = (CPUSample)i.next();
				int posX = 
					(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
				int posY = barH - (int)((sample.user + sample.sys) * barH);
				if (cpuPoints.length() == 0) {
					cpuPoints.append(posX + "," + barH);
				}
				cpuPoints.append(" " + posX + "," + posY);
				lastX = posX;
			}
			cpuPoints.append(" " + lastX + "," + barH);
		}
		
		
		/*
		 * Render disk utilization chart
		 */
		StringBuffer diskUtilPoints = new StringBuffer();
		StringBuffer diskReadPoints = new StringBuffer();
		StringBuffer fileOpenPoints = new StringBuffer();
		
		if (diskStats != null) {
			// Disk utilization
			int lastX = 0;
			for (Iterator i = diskStats.getSamples().iterator(); i.hasNext();) {
				Sample s = (Sample)i.next();
				if (!(s instanceof DiskUtilSample)) {
					continue;
				}
				DiskUtilSample sample = (DiskUtilSample)s;
				Date endTime =
					new Date(procTree.startTime.getTime() + dur);
				if (sample.time.compareTo(procTree.startTime) < 0
					|| sample.time.compareTo(endTime) > 0) {
					continue;
				}
				int posX =
					(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
				
				int posY = barH - (int)(sample.util * barH);
				lastX = posX;
				if (diskUtilPoints.length() == 0) {
					diskUtilPoints.append(posX + "," + barH);
				}
				diskUtilPoints.append(" " + posX + "," + posY);
			}
			diskUtilPoints.append(" " + lastX + "," + barH);
			
			// Disk throughput
			Point lastP = null;
			double maxTPut = ProcDiskStatParser.getMaxDiskTPut(diskStats.getSamples());
			for (Iterator i = diskStats.getSamples().iterator(); i.hasNext();) {
				Sample s = (Sample)i.next();
				if (!(s instanceof DiskTPutSample)) {
					continue;
				}
				DiskTPutSample sample = (DiskTPutSample)s;
				Date endTime =
					new Date(procTree.startTime.getTime() + dur);
				if (sample.time.compareTo(procTree.startTime) < 0
					|| sample.time.compareTo(endTime) > 0) {
					continue;
				}
				int posX =
					(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
				
				double tput = (sample.read + sample.write) / maxTPut;
				int posY = barH - (int)(tput * barH);
				
				if (lastP != null) {
					diskReadPoints.append("\t\t\t<line x1=\"" + lastP.x + "\" y1=\""
						+ lastP.y + "\" x2=\"" + posX + "\" y2=\"" + posY + "\"/>\n");
					// diskReadPoints.append("\t\t\t<circle cx=\"" + posX + "\" cy=\""
					//	+ posY + "\" r=\"2\"/>\n");
				}
				lastP = new Point(posX, posY);
			}
			for (Iterator i=diskStats.getSamples().iterator(); i.hasNext(); ) {
				Sample s = (Sample)i.next();
				if (!(s instanceof DiskTPutSample)) {
					continue;
				}
				DiskTPutSample sample = (DiskTPutSample)s;
				if (sample.read + sample.write == maxTPut) {
					int posX =
						(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
					int posY = barH - (int)(1.0 * barH);
					String label = (int)Math.round((sample.read + sample.write) / 1024.0) + " MB/s";
					diskReadPoints.append("\t\t\t<text class=\"DiskLabel\" "
						+ "x=\"" + posX + "\" y=\"" + posY + "\" "
						+ "dx=\"-" + (label.length()/3) + "em\" dy=\"-2px\">" + label + "</text>\n");
					break;
				}
			}
			
			// Files opened
			lastP = null;
			int maxFiles = FileOpenSample.getMaxFileOpens(diskStats.getSamples());
			for (Iterator i = diskStats.getSamples().iterator(); i.hasNext();) {
				Sample s = (Sample)i.next();
				if (!(s instanceof FileOpenSample)) {
					continue;
				}
				FileOpenSample sample = (FileOpenSample)s;
				Date endTime =
					new Date(procTree.startTime.getTime() + dur);
				if (sample.time.compareTo(procTree.startTime) < 0
					|| sample.time.compareTo(endTime) > 0) {
					continue;
				}
				int posX =
					(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
				
				double fopenRatio = (double)sample.fileOpens / maxFiles;
				int posY = barH - (int)(fopenRatio * barH);
				
				if (lastP != null) {
					fileOpenPoints.append("\t\t\t<line x1=\"" + lastP.x + "\" y1=\""
						+ lastP.y + "\" x2=\"" + posX + "\" y2=\"" + posY + "\"/>\n");
					//fileOpenPoints.append("\t\t\t<circle cx=\"" + posX + "\" cy=\""
					//	+ posY + "\" r=\"2\"/>\n");
				}
				lastP = new Point(posX, posY);
			}
			for (Iterator i=diskStats.getSamples().iterator(); i.hasNext(); ) {
				Sample s = (Sample)i.next();
				if (!(s instanceof FileOpenSample)) {
					continue;
				}
				FileOpenSample sample = (FileOpenSample)s;
				if (sample.fileOpens == maxFiles) {
					int posX =
						(int)((sample.time.getTime() - procTree.startTime.getTime()) * w / dur);
					int posY = barH - (int)(1.0 * barH);
					String label = sample.fileOpens + "/s";
					
					fileOpenPoints.append("\t\t\t<text class=\"FileOpenLabel\" "
						+ "x=\"" + posX + "\" y=\"" + posY + "\" "
						+ "dx=\"-" + (label.length()/3) + "em\" dy=\"-5px\">" + label + "</text>\n");
					break;
				}
			}
		}
		
		/*
		 * Render the process tree
		 */
 		// time ticks
		int treeH = procTree.numProc * procH;
		ticksBuff = new StringBuffer();
		StringBuffer axisBuff = new StringBuffer();
		for (int i=0; i<=dur/1000; i++) {
			int x = i * secW;
			String tclass = (i % 5 == 0) ? "class=\"Bold\" " : "";
			ticksBuff.append("<line " + tclass + "x1=\"" + x + "\" "
				+ "y1=\"0\" x2=\"" + x + "\" y2=\"" + treeH + "\"/>\n");
			if (i > 0 && i % 5 == 0) {
				String label = i + "s";
				int len = label.length();
				axisBuff.append("<text class=\"AxisLabel\" x=\""
					+ x + "\" y=\"0\" dx=\"" + (-len/4.0) + "em\" dy=\"-3\">"
					+ label + "</text>\n");
			}
		}
		/*
		// process delimiter lines
		for (int i=0; i<=procTree.numProc; i++) {
			int y = i * procH;
			ticksBuff.append("<line x1=\"0\" y1=\"" + y + "\" x2=\"" + w + "\" y2=\"" + y + "\"/>\n");
		}
		*/
		String procTicks = ticksBuff.toString();
		String axisLabels = axisBuff.toString();
		procTicks = procTicks.replaceAll("\n", "\n\t\t\t");
		axisLabels = axisLabels.replaceAll("\n", "\n\t\t\t");
		procTicks = procTicks.trim();
		axisLabels = axisLabels.trim();
		
		Rectangle rect = new Rectangle(0, 0, w, procTree.numProc * procH);
		StringBuffer procTreeSVG = new StringBuffer();
		renderProcessList(procTree.processTree, -1, -1, procTree, 0, procH, rect, procTreeSVG);
		
		// workaround for a librsvg segv
		if (ioPoints.length() == 0) {
			ioPoints.append("0,0");
		}
		if (cpuPoints.length() == 0) {
			cpuPoints.append("0,0");
		}
		if (diskUtilPoints.length() == 0) {
			diskUtilPoints.append("0,0");
		}
		if (diskReadPoints.length() == 0) {
			diskReadPoints.append("0,0");
		}
		if (fileOpenPoints.length() == 0) {
			fileOpenPoints.append("0,0");
		}
		
		// assebmle all template arguments
		Object[] chartArgs = {
			dimension, style, title, uname, release, cpu, kopt, bootTime,
			cpuTicks, ioPoints.toString(), cpuPoints.toString(),
			cpuTicks, diskUtilPoints.toString(), diskReadPoints.toString().trim(),
			fileOpenPoints.toString().trim(),
			axisLabels, procTicks, procTreeSVG.toString()
		};
		// format the template
		String svgContent =
			MessageFormat.format(chartTemplate, chartArgs);
		
		if (INLINE_CSS) {
			File cssFile = SVG_CSS_FILE;
			svgContent = CSSInliner.inline(svgContent, cssFile);
		}
		
		// write the compressed SVG content
		if (COMPRESS_SVG) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			GZIPOutputStream gzos = new GZIPOutputStream(baos);
			gzos.write(svgContent.getBytes());
			gzos.close();
			os.write(baos.toByteArray());
		} else {
			os.write(svgContent.getBytes());
		}
	}

	private int renderProcessList(List processTree, int px, int py,
		ProcessTree procTree, int y, int procH, Rectangle rect, StringBuffer svgContent) {
		
		//Process lastProc = null;
		for (Iterator i = processTree.iterator(); i.hasNext();) {
			Process proc = (Process) i.next();
			/*
			if (lastProc != null
				&& (lastProc.startTime.getTime() + lastProc.duration <= proc.startTime.getTime())) {
				// stack processes horizontally
				//y -= procH;
			}
			*/
			String procSVG = renderProcess(proc, px, py, procTree, y, procH, rect);
			procSVG = procSVG.replaceAll("\n", "\n\t\t\t");
			procSVG = procSVG.trim();
			svgContent.append(procSVG);
			
			int px2 =
				rect.x + (int)((proc.startTime.getTime() - procTree.startTime.getTime()) * rect.width / procTree.duration);
			int py2 = y + procH;
			y = renderProcessList(proc.childList, px2, py2, procTree, y + procH, procH, rect, svgContent);
			//lastProc = proc;
		}
		return y;
	}


	static String processTemplate = null;
	private String renderProcess(Process proc, int px, int py,
		ProcessTree procTree, int y, int procH, Rectangle rect) {
		/*
		 * Load the process SVG template.  Its arguments are:
		 *  0: Position ("x, y")
		 *  1: Timeline
		 *  2: Border width and height
		 *  4: Parent process connector line
		 *  4: Process label position
		 *  5: Process label text
		 */
		if (processTemplate == null) {
			try {
				processTemplate = Common.loadFile(PROCESS_SVG_TEMPLATE);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
		int x =
			rect.x + (int)((proc.startTime.getTime() - procTree.startTime.getTime()) * rect.width / procTree.duration);
		int w = (int)((proc.duration) * rect.width / procTree.duration);
	
		String position = x + "," + y;
		String border = "width=\"" + w + "\" height=\"" + procH + "\"";
		
		StringBuffer tbuff = new StringBuffer();
		tbuff.append("<rect class=\"Sleeping\" x=\"0\" y=\"0\" width=\"" + w
			+ "\" height=\"" + procH + "\"/>\n");
		
		
		int lastTx = -1;
		for (Iterator i=proc.samples.iterator(); i.hasNext(); ) {
			ProcessSample sample = (ProcessSample)i.next();
			if (sample.time.before(proc.startTime) ||
				sample.time.after(new Date(proc.startTime.getTime() + proc.duration - procTree.samplePeriod))) {
				continue;
			}
			int tx =
				(int)Math.round(((sample.time.getTime() - proc.startTime.getTime()) * rect.width / (double)procTree.duration));
			int tw =
				(int)Math.round(procTree.samplePeriod * rect.width / (double)procTree.duration);
			if (lastTx != -1 && Math.abs(lastTx - tx) <= 3) {
				tw += tx - lastTx;
				tx = lastTx;
			}
			lastTx = tx + tw;
			int state = sample.state;
			double cpu = sample.cpu.user + sample.cpu.sys;
			String tclass = "Sleeping";
			String opacity = "";
			
			switch (state) {
				case Process.STATE_WAITING:
					tclass = "UnintSleep";
					break;
				case Process.STATE_RUNNING:
					double alpha = cpu;
					tclass = "Running";
					opacity = "fill-opacity=\"" + alpha + "\" ";
					break;
				case Process.STATE_SLEEPING:
					tclass = null;
					break;
				case Process.STATE_STOPPED:
					tclass = "Traced";
					break;
				case Process.STATE_ZOMBIE:
					tclass = "Zombie";
					break;
				default:;
			}

			if (tclass != null) {
				tbuff.append("<rect class=\"" + tclass + "\" "
					+ opacity + "x=\"" + tx + "\" y=\"0\" width=\"" + tw
					+ "\" height=\"" + procH + "\"/>\n");
			}
		}
		String timeline = tbuff.toString();
		timeline = timeline.replaceAll("\n", "\n\t\t");
		timeline = timeline.trim();

		// parent process connector lines
		StringBuffer connector = new StringBuffer();
		if (px != -1 && py != -1) {
			px -= x;
			py -= y;
			if (px == 0) {
				int depOffX = 3;
				int depOffY = procH / 4;
				Point p1 = new Point(0, procH/2);
				Point p2 = new Point(px - depOffX, procH/2);
				connector.append(getConnLineSVG(p1, p2) + "\n");
				p1 = new Point(px - depOffX, procH/2);
				p2 = new Point(px - depOffX, py - depOffY);
				connector.append(getConnLineSVG(p1, p2) + "\n");
				p1 = new Point(px - depOffX, py - depOffY);
				p2 = new Point(px, py - depOffY);
				connector.append(getConnLineSVG(p1, p2) + "\n");
			} else {
				Point p1 = new Point(0, procH/2);
				Point p2 = new Point(px, procH/2);
				connector.append(getConnLineSVG(p1, p2) + "\n");
				p1 = new Point(px, procH/2);
				p2 = new Point(px, py);
				connector.append(getConnLineSVG(p1, p2) + "\n");
			}
		}
		
		String labelPos = "";
		if (w < 200 && x + w + 200 < rect.x + rect.width) {
			labelPos = "dx=\"2\" dy=\""+ (procH - 1) + "\" x=\"" + w + "\" y=\"0\"";
		} else {
			labelPos = "dx=\"2\" dy=\""+ (procH - 1) + "\" x=\"0\" y=\"0\"";
		}
		String labelText = proc.cmd;
		String titleText = Common.getProcessDesc(proc, procTree.startTime);

		Object[] procArgs =
			{position, timeline, border, connector, labelPos, labelText, titleText};
		String procContent =
			MessageFormat.format(processTemplate, procArgs);
		return procContent;
	}
	
	/**
	 * Encode SVG argument.
	 * 
	 * @param arg  SVG argument
	 * @return     encoded argument
	 */
	private static String encodeArg(String arg) {
		return arg.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}
	
	/**
	 * Returns the SVG markup for a process connector line.
	 * 
	 * @param p1  starting line point
	 * @param p2  ending line point
	 * @return    SVG markup for the line
	 */
	private static String getConnLineSVG(Point p1, Point p2) {
		String style = "style=\"stroke-dasharray: 3,3;\" "; // librsvg workaround
		String svg = "<line " + style + "x1=\"" + p1.x + "\" y1=\"" + p1.y + "\" "
			+ "x2=\"" + p2.x + "\" y2=\"" + p2.y + "\"/>";
		return svg;
	}
	
	/*
	 * inherit javadoc
	 */
	public String getFileSuffix() {
		return COMPRESS_SVG ? "svgz" : "svg";
	}
	
}
