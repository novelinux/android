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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

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
 * ImageRenderer renders the boot chart as a Java 2D
 * <code>BufferedImage</code>.  Subclasses may then encode the image in
 * different formats (e.g. PNG or EPS).
 */
public abstract class ImageRenderer extends Renderer {
	private static String fontFamily = "SansSerif";
	
	private static final Logger log = Logger.getLogger(ImageRenderer.class.getName());
	/** Process tree background color. */
	private static final Color BACK_COLOR = new Color(255, 255, 255, 255);
	/** Process tree border color. */
	private static final Color BORDER_COLOR = new Color(160, 160, 160, 255);
	/** Second tick line color. */
	private static final Color TICK_COLOR = new Color(235, 235, 235, 255);
	/** 5-second tick line color. */
	private static final Color TICK_COLOR_BOLD = new Color(220, 220, 220, 255);
	/** Text color. */
	private static final Color TEXT_COLOR = new Color(0, 0, 0, 255);
	
	/** Title text font. */
	private static final Font TITLE_FONT = new Font(fontFamily, Font.BOLD, 18);
	/** Default text font. */
	private static final Font TEXT_FONT = new Font(fontFamily, Font.PLAIN, 12);
	/** Axis label font. */
	private static final Font AXIS_FONT = new Font(fontFamily, Font.PLAIN, 11);
	/** Legend font. */
	private static final Font LEGEND_FONT = new Font(fontFamily, Font.PLAIN, 12);
	
	/** CPU load chart color. */
	private static final Color CPU_COLOR = new Color(102, 140, 178, 255);
	/** IO wait chart color. */
	private static final Color IO_COLOR = new Color(194, 122, 122, 128);
	/** Disk throughput color. */
	private static final Color DISK_TPUT_COLOR = new Color(50, 180, 50, 255);
	/** CPU load chart color. */
	private static final Color FILE_OPEN_COLOR = new Color(50, 180, 180, 255);
	
	/** Process border color. */
	private static final Color PROC_BORDER_COLOR = new Color(180, 180, 180, 255);
	/** Waiting process color. */
	private static final Color PROC_COLOR_D = new Color(194, 122, 122, 64);
	/** Running process color. */
	private static final Color PROC_COLOR_R = CPU_COLOR;
	/** Sleeping process color. */
	private static final Color PROC_COLOR_S = new Color(240, 240, 240, 255);
	/** Stopped process color. */
	private static final Color PROC_COLOR_T = new Color(240, 128, 128, 255);
	/** Zombie process color. */
	private static final Color PROC_COLOR_Z = new Color(180, 180, 180, 255);

	/** Process label color. */
	private static final Color PROC_TEXT_COLOR = new Color(48, 48, 48, 255);
	/** Process label font. */
	private static final Font PROC_TEXT_FONT = new Font(fontFamily, Font.PLAIN, 12);

	/** Signature color. */
	private static final Color SIG_COLOR = new Color(0, 0, 0, 80);
	/** Signature font. */
	private static final Font SIG_FONT = new Font(fontFamily, Font.BOLD, 14);
	/** Signature text. */
	//private static final String SIGNATURE = "www.bootchart.org";
	private static final String SIGNATURE = "";
	
	/** Disk chart line stoke. */
	private static final Stroke DISK_STROKE = new BasicStroke(1.5f);
	
	/** Process dependency line color. */
	private static final Color DEP_COLOR = new Color(192, 192, 192, 255);
	/** Process dependency line stroke. */
	private static final Stroke DEP_STROKE =
		new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 
						10.0f, new float[]{2.0f, 2.0f}, 0.0f);
	
	/** Boot duration time format. */
	private static final DateFormat BOOT_TIME_FORMAT =
		new SimpleDateFormat("m:ss", Common.LOCALE);
	
	/** Minimum image width. */
	private static final int MIN_IMG_W = 800;
	/** Maximum image dimenstion (to avoid OOM exceptions). */
	private static final int MAX_IMG_DIM = 4096;
	
	
	protected Graphics g = null;
	protected BufferedImage img = null;
	/**
	 * Whether to allow usage of transparency.  Certain renderers (e.g. EPS)
	 * will produce better results if the colors aren't transparent.
	 */
	protected boolean allowAlpha = true;

	
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
		
		int headerH = 280;
		int barH = 55;
		// offsets
		int offX = 10;
		int offY = 10;
		
		int secW = 25; // the width of a second
		int w = (int)(procTree.duration * secW / 1000) + 2*offX;
		int procH = 16; // the height of a process
		int h = procH * procTree.numProc + headerH + 2*offY;
		
		while (w > MAX_IMG_DIM && secW > 1) {
			secW /= 2;
			w = (int)(procTree.duration * secW / 1000) + 2*offX;
		}
		while (h > MAX_IMG_DIM) {
			procH = procH * 3 / 4;
			h = procH * procTree.numProc + headerH + 2*offY;
		}
		
		w = Math.min(w, MAX_IMG_DIM);
		h = Math.min(h, MAX_IMG_DIM);
		
		log.fine("Creating image: (" + w + ", " + h + ")");
		if (g == null) {
			// some renderers require custom Graphics2D initialization
			int type =
				allowAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
			img = new BufferedImage(Math.max(w, MIN_IMG_W), h, type);
			g = img.createGraphics();
		}
		
		if (g instanceof Graphics2D) {
			// set best quality rendering
			Map renderHints = new HashMap();
			renderHints.put(RenderingHints.KEY_ANTIALIASING,
				            RenderingHints.VALUE_ANTIALIAS_ON);
			renderHints.put(RenderingHints.KEY_COLOR_RENDERING,
				            RenderingHints.VALUE_COLOR_RENDER_QUALITY);
			renderHints.put(RenderingHints.KEY_DITHERING,
	                        RenderingHints.VALUE_DITHER_DISABLE);
			renderHints.put(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
			renderHints.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			((Graphics2D)g).addRenderingHints(renderHints);
		}

		setColor(g, Color.WHITE);
		g.fillRect(0, 0, Math.max(w, MIN_IMG_W), h);

		// draw the title and headers
		drawHeader(headers, offX, procTree.duration);

		int rectX = offX;
		int rectY = headerH + offY;
		int rectW = w - 2 * offX;
		int rectH = h - 2 * offY - headerH;
		
		// render bar legend
		g.setFont(LEGEND_FONT);
		int legY = rectY - 2*barH - 6*offY;
		int legX = offX;
		int legS = 10;
		if (hasSamples(cpuStats, CPUSample.class)) {
			String cpuLabel = "CPU (user+sys)";
			setColor(g, CPU_COLOR);
			g.fillRect(legX, legY - legS, legS, legS);
			setColor(g, PROC_BORDER_COLOR);
			g.drawRect(legX, legY - legS, legS, legS);
			setColor(g, TEXT_COLOR);
			g.drawString(cpuLabel, legX + legS + 5, legY);
			legX += 120;
			setColor(g, IO_COLOR);
			g.fillRect(legX, legY - legS, legS, legS);
			setColor(g, PROC_BORDER_COLOR);
			g.drawRect(legX, legY - legS, legS, legS);
			setColor(g, TEXT_COLOR);
			g.drawString("I/O (wait)", legX + legS + 5, legY);
			legX += 120;
		}
		
		legX = offX;
		if (hasSamples(diskStats, DiskTPutSample.class)) {
			legY = rectY - barH - 4*offY;
			legX = offX;
			setColor(g, DISK_TPUT_COLOR);
			g.fillRect(legX, legY - legS/2, legS + 1, 3);
			g.fillArc(legX + legS/2 - 2, legY - legS/2 - 1, 5, 5, 0, 360);
			setColor(g, TEXT_COLOR);
			g.drawString("Disk throughput", legX + legS + 5, legY);
			legX += 120;
		}
		if (hasSamples(diskStats, DiskUtilSample.class)) {
			setColor(g, IO_COLOR);
			g.fillRect(legX, legY - legS, legS, legS);
			setColor(g, PROC_BORDER_COLOR);
			g.drawRect(legX, legY - legS, legS, legS);
			setColor(g, TEXT_COLOR);
			g.drawString("Disk utilization", legX + legS + 5, legY);
			legX += 120;
		}
		if (hasSamples(diskStats, FileOpenSample.class)) {
			setColor(g, FILE_OPEN_COLOR);
			g.fillRect(legX, legY - legS/2, legS + 1, 3);
			g.fillArc(legX + legS/2 - 2, legY - legS/2 - 1, 5, 5, 0, 360);
			setColor(g, PROC_BORDER_COLOR);
			setColor(g, TEXT_COLOR);
			g.drawString("Files opened", legX + legS + 5, legY);
			legX += 120;
		}
		int maxLegX = legX;

		// process states
		legY = rectY - 17;
		legX = offX;
		String procR = "Running (%cpu)";
		setColor(g, PROC_COLOR_R);
		g.fillRect(legX, legY - legS, legS, legS);
		setColor(g, PROC_BORDER_COLOR);
		g.drawRect(legX, legY - legS, legS, legS);
		setColor(g, TEXT_COLOR);
		g.drawString(procR, legX + legS + 5, legY);
		legX += 120;
		
		String procD = "Unint.sleep (I/O)";
		setColor(g, TEXT_COLOR);
		g.drawString(procD, legX + legS + 5, legY);
		setColor(g, PROC_COLOR_D);
		g.fillRect(legX, legY - legS, legS, legS);
		setColor(g, PROC_BORDER_COLOR);
		g.drawRect(legX, legY - legS, legS, legS);
		legX += 120;
		
		String procS = "Sleeping";
		setColor(g, PROC_COLOR_S);
		g.fillRect(legX, legY - legS, legS, legS);
		setColor(g, PROC_BORDER_COLOR);
		g.drawRect(legX, legY - legS, legS, legS);
		setColor(g, TEXT_COLOR);
		g.drawString(procS, legX + legS + 5, legY);
		legX += 120;
		
		String procZ = "Zombie";
		setColor(g, PROC_COLOR_Z);
		g.fillRect(legX, legY - legS, legS, legS);
		setColor(g, PROC_BORDER_COLOR);
		g.drawRect(legX, legY - legS, legS, legS);
		setColor(g, TEXT_COLOR);
		g.drawString(procZ, legX + legS + 5, legY);
		legX += g.getFontMetrics(LEGEND_FONT).stringWidth(procZ) + offX;
		legX += 120;

		// render I/O wait
		int barY = rectY - 4*offY - barH - offX - 5;
		setColor(g, BORDER_COLOR);
		g.drawRect(rectX, barY - barH, rectW, barH);
		for (int i = 0; i <= rectW; i += secW) {
			if ((i / secW) % 5 == 0) {
				setColor(g, TICK_COLOR_BOLD);
			} else {
				setColor(g, TICK_COLOR);
			}
			g.drawLine(rectX + i, barY - barH, rectX + i, barY);
		}
		
		if (cpuStats != null) {
			Point lastPoint = null;
			int[] xPoints = new int[cpuStats.getSamples().size() + 2];
			int[] yPoints = new int[cpuStats.getSamples().size() + 2];
			int pi = 0;
	
			setColor(g, IO_COLOR);
			lastPoint = null;
			for (Iterator i = cpuStats.getSamples().iterator(); i.hasNext();) {
				CPUSample sample = (CPUSample)i.next();
				int posX = rectX
					+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
						* rectW / procTree.duration);
				if (posX < rectX || posX > rectX + rectW) {
					//log.warning("Cropped sample " + pi + ": " + sample);
					continue;
				}
				int posY = barY - (int) ((sample.user + sample.sys + sample.io) * barH);
				if (lastPoint != null) {
					g.drawLine(lastPoint.x, lastPoint.y, posX, posY);
				}
				lastPoint = new Point(posX, posY);
				xPoints[pi] = posX;
				;
				yPoints[pi] = posY;
				pi++;
			}
			xPoints[pi] = xPoints[Math.max(0, pi-1)];
			yPoints[pi] = barY;
			pi++;
			xPoints[pi] = xPoints[0];
			yPoints[pi] = barY;
			pi++;
			g.fillPolygon(xPoints, yPoints, pi);
	
			// render CPU load
			setColor(g, CPU_COLOR);
			lastPoint = null;
			pi = 0;
	
			for (Iterator i = cpuStats.getSamples().iterator(); i.hasNext();) {
				CPUSample sample = (CPUSample) i.next();
				int posX = offX
					+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
						* rectW / procTree.duration);
				if (posX < rectX || posX > rectX + rectW) {
					//log.warning("Cropped sample " + pi + ": " + sample);
					continue;
				}
				int posY = barY - (int) ((sample.user + sample.sys) * barH);
				if (lastPoint != null) {
					g.drawLine(lastPoint.x, lastPoint.y, posX, posY);
				}
				lastPoint = new Point(posX, posY);
				xPoints[pi] = posX;
				yPoints[pi] = posY;
				pi++;
			}
			xPoints[pi] = xPoints[Math.max(0, pi-1)];
			yPoints[pi] = barY;
			pi++;
			xPoints[pi] = xPoints[0];
			yPoints[pi] = barY;
			pi++;
			g.fillPolygon(xPoints, yPoints, pi);
		}
		
		
		if (diskStats != null) {
			Point lastPoint = null;
			int[] xPoints = new int[diskStats.getSamples().size() + 2];
			int[] yPoints = new int[diskStats.getSamples().size() + 2];
			int pi = 0;
			
	  		// render I/O utilization
			barY = rectY - 2*offY - offY - 5;
			setColor(g, BORDER_COLOR);
			g.drawRect(rectX, barY - barH, rectW, barH);
			for (int i = 0; i <= rectW; i += secW) {
				if ((i / secW) % 5 == 0) {
					setColor(g, TICK_COLOR_BOLD);
				} else {
					setColor(g, TICK_COLOR);
				}
				g.drawLine(rectX + i, barY - barH, rectX + i, barY);
			}
			setColor(g, IO_COLOR);
			lastPoint = null;
			
			for (Iterator i=diskStats.getSamples().iterator(); i.hasNext();) {
				Sample s = (Sample)i.next();
				if (!(s instanceof DiskUtilSample)) {
					continue;
				}
				DiskUtilSample sample = (DiskUtilSample)s;
				Date endTime =
					new Date(procTree.startTime.getTime() + procTree.duration);
				if (sample.time.compareTo(procTree.startTime) < 0
					|| sample.time.compareTo(endTime) > 0) {
					continue;
				}
				int posX = rectX
					+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
						* rectW / procTree.duration);
				if (posX < rectX || posX > rectX + rectW) {
					//log.warning("Cropped sample " + pi + ": " + sample);
					continue;
				}
				int posY = barY - (int)(sample.util * barH);
				if (lastPoint != null) {
					g.drawLine(lastPoint.x, lastPoint.y, posX, posY);
				}
				lastPoint = new Point(posX, posY);
				xPoints[pi] = posX;
				yPoints[pi] = posY;
				pi++;
			}
			xPoints[pi] = xPoints[Math.max(0, pi-1)];
			yPoints[pi] = barY;
			pi++;
			xPoints[pi] = xPoints[0];
			yPoints[pi] = barY;
			pi++;
			g.fillPolygon(xPoints, yPoints, pi);
	
			if (g instanceof Graphics2D) {
				((Graphics2D)g).setStroke(DISK_STROKE);
			}
			// render disk throughput
			lastPoint = null;
			setColor(g, DISK_TPUT_COLOR);
			double maxTPut = ProcDiskStatParser.getMaxDiskTPut(diskStats.getSamples());
			for (Iterator i = diskStats.getSamples().iterator(); i.hasNext();) {
				Sample s = (Sample)i.next();
				if (!(s instanceof DiskTPutSample)) {
					continue;
				}
				DiskTPutSample sample = (DiskTPutSample)s;
				Date endTime =
					new Date(procTree.startTime.getTime() + procTree.duration);
				if (sample.time.compareTo(procTree.startTime) < 0
					|| sample.time.compareTo(endTime) > 0) {
					continue;
				}
				int posX = rectX
					+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
						* rectW / procTree.duration);
				if (posX < rectX || posX > rectX + rectW) {
					//log.warning("Cropped sample: " + sample);
					continue;
				}
				double tput = (sample.read + sample.write) / maxTPut;
				int posY = barY - (int) (tput * barH);
				if (lastPoint != null) {
					g.drawLine(lastPoint.x, lastPoint.y, posX, posY);
				}
				lastPoint = new Point(posX, posY);
				//int r = 3;
				//g.fillArc(posX - r/2, posY - r/2, r, r, 0, 360);
			}
			for (Iterator i=diskStats.getSamples().iterator(); i.hasNext(); ) {
				Sample s = (Sample)i.next();
				if (!(s instanceof DiskTPutSample)) {
					continue;
				}
				DiskTPutSample sample = (DiskTPutSample)s;
				if (sample.read + sample.write == maxTPut) {
					int posX = rectX
						+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
							* rectW / procTree.duration);
					if (posX < rectX || posX > rectX + rectW) {
						//log.warning("Cropped sample: " + sample);
						continue;
					}
					int posY = barY - (int) (1.0 * barH);
					if (posX < maxLegX) {
						posY += 15;
						posX += 30;
					}
					String label = (int)Math.round((sample.read + sample.write) / 1024.0) + " MB/s";
					setColor(g, opaque(DISK_TPUT_COLOR));
					g.drawString(label, posX - 20, posY - 3);
					break;
				}
			}
			
			// render file opens
			lastPoint = null;
			setColor(g, FILE_OPEN_COLOR);
			int maxFiles = FileOpenSample.getMaxFileOpens(diskStats.getSamples());
			for (Iterator i = diskStats.getSamples().iterator(); i.hasNext();) {
				Sample s = (Sample)i.next();
				if (!(s instanceof FileOpenSample)) {
					continue;
				}
				FileOpenSample sample = (FileOpenSample)s;
				Date endTime =
					new Date(procTree.startTime.getTime() + procTree.duration);
				if (sample.time.compareTo(procTree.startTime) < 0
					|| sample.time.compareTo(endTime) > 0) {
					continue;
				}
				int posX = rectX
					+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
						* rectW / procTree.duration);
				if (posX < rectX || posX > rectX + rectW) {
					//log.warning("Cropped sample: " + sample);
					continue;
				}
				double fopenRatio = (double)sample.fileOpens / maxFiles;
				int posY = barY - (int) (fopenRatio * barH);
				if (lastPoint != null) {
					g.drawLine(lastPoint.x, lastPoint.y, posX, posY);
				}
				lastPoint = new Point(posX, posY);
				//int r = 3;
				//g.fillArc(posX - r/2, posY - r/2, r, r, 0, 360);
			}
			for (Iterator i=diskStats.getSamples().iterator(); i.hasNext(); ) {
				Sample s = (Sample)i.next();
				if (!(s instanceof FileOpenSample)) {
					continue;
				}
				FileOpenSample sample = (FileOpenSample)s;
				if (sample.fileOpens == maxFiles) {
					int posX = rectX
						+ (int) ((sample.time.getTime() - procTree.startTime.getTime())
							* rectW / procTree.duration);
					if (posX < rectX || posX > rectX + rectW) {
						//log.warning("Cropped sample: " + sample);
						continue;
					}
					int posY = barY - (int) (1.0 * barH);
					if (posX < maxLegX) {
						posY += 15;
						posX += 30;
					}
					String label = sample.fileOpens + "/s";
					setColor(g, opaque(FILE_OPEN_COLOR));
					g.drawString(label, posX - 15, posY - 3);
					break;
				}
			}
			
			if (g instanceof Graphics2D) {
				((Graphics2D)g).setStroke(new BasicStroke());
			}
		}

		// render processes
		setColor(g, BACK_COLOR);
		g.fillRect(rectX, rectY, rectW, rectH);

		// draw process tree second ticks
		g.setFont(AXIS_FONT);
		FontMetrics fm = g.getFontMetrics(TEXT_FONT);
		for (int i = 0; i <= rectW; i += secW) {
			setColor(g, TICK_COLOR);
			g.drawLine(rectX + i, rectY, rectX + i, rectY + rectH);
			if ((i / secW) % 5 == 0) {
				setColor(g, TICK_COLOR_BOLD);
				g.drawLine(rectX + i, rectY, rectX + i, rectY + rectH);
				setColor(g, TEXT_COLOR);
				String label = (i / secW) + "s";
				g.drawString(label, rectX + i - fm.stringWidth(label) / 2,
					rectY - 2);
			}
		}

		/*
		// draw process tree lines
		setColor(g, TICK_COLOR);
		for (int i = 0; i <= rectH; i += procH) {
			g.drawLine(rectX, rectY + i, rectX + rectW, rectY + i);
		}
		*/
		
		g.setFont(PROC_TEXT_FONT);
		if (procTree.processTree != null) {
			drawProcessList(procTree.processTree, -1, -1, procTree,
				rectY, procH, new Rectangle(rectX, rectY, rectW, rectH));
		}

		setColor(g, BORDER_COLOR);
		g.drawRect(rectX, rectY, rectW, rectH);
		
		setColor(g, SIG_COLOR);
		g.setFont(SIG_FONT);
		g.drawString(SIGNATURE, offX + 5, h - offY - 5);
		
		// crop image
		String title = headers.getProperty("title");
		if (title == null) {
			title = "";
		}
		int titleW = g.getFontMetrics(TITLE_FONT).stringWidth(title) + 2*offX;
		if (img != null && w < MIN_IMG_W) {
			log.fine("Cropping image");
			img = img.getSubimage(0, 0, Math.max(w, titleW), h);
		}
	}
	
	private void drawHeader(Properties headers, int offX, long duration) {
		setColor(g, TEXT_COLOR);
		g.setFont(TITLE_FONT);
		int headerY = g.getFontMetrics(TITLE_FONT).getMaxAscent() + 2;
		if (headers != null && headers.getProperty("title") != null) {
			g.drawString(headers.getProperty("title"), offX, headerY);
		}
		g.setFont(TEXT_FONT);
		headerY += 2;
		int hoff = 1;
		if (headers != null) {
			String uname = "uname: " + headers.getProperty("system.uname", "");
			g.drawString(uname, offX, headerY + (hoff++) * (TEXT_FONT.getSize() + 2));
			String rel = "release: " + headers.getProperty("system.release", "");
			g.drawString(rel, offX, headerY + (hoff++) * (TEXT_FONT.getSize() + 2));
			String cpu = "CPU: " + headers.getProperty("system.cpu", "");
			cpu = cpu.replaceFirst("model name\\s*:\\s*", "");
			g.drawString(cpu, offX, headerY + (hoff++) * (TEXT_FONT.getSize() + 2));
			if (headers.getProperty("profile.process") != null) {
				String app = "application: " + headers.getProperty("profile.process", "");
				g.drawString(app, offX, headerY + (hoff++) * (TEXT_FONT.getSize() + 2));
			} else {
				String kopt = "kernel options: " + headers.getProperty("system.kernel.options", "");
				g.drawString(kopt, offX, headerY + (hoff++) * (TEXT_FONT.getSize() + 2));
			}
		}
		Date dur = new Date(Math.round(duration / 1000.0) * 1000);
		String bootTime = "time: " + BOOT_TIME_FORMAT.format(dur);
		g.drawString(bootTime, offX, headerY + (hoff++) * (TEXT_FONT.getSize() + 2));
	}

	private int drawProcessList(List processList, int px, int py,
		ProcessTree procTree, int y, int procH, Rectangle rect) {
		//Process lastProc = null;
		for (Iterator i = processList.iterator(); i.hasNext();) {
			Process proc = (Process) i.next();
			/*
			if (lastProc != null
				&& (lastProc.startTime.getTime() + lastProc.duration <= proc.startTime
					.getTime())) {
				y -= procH;
			}
			*/
			int offY = procH;
			drawProcess(proc, px, py, procTree, y, procH, rect);
			int px2 = rect.x
				+ (int) ((proc.startTime.getTime() - procTree.startTime.getTime())
					* rect.width / procTree.duration);
			int py2 = y + procH;
			y = drawProcessList(proc.childList, px2, py2,	procTree, y + offY, procH, rect);
			//lastProc = proc;
		}
		return y;
	}


	private void drawProcess(Process proc, int px, int py,
		ProcessTree procTree, int y, int procH, Rectangle rect) {
		int x = rect.x
			+ (int) ((proc.startTime.getTime() - procTree.startTime.getTime())
				* rect.width / procTree.duration);
		int w = (int) ((proc.duration) * rect.width / procTree.duration);
		
		setColor(g, PROC_COLOR_S);
		g.fillRect(x, y, w, procH);
		
		if (g instanceof Graphics2D) {
			((Graphics2D)g).setStroke(DEP_STROKE);
		}
		setColor(g, DEP_COLOR);
		if (px != -1 && py != -1) {
			if (Math.abs(px - x) < 3) {
				int depOffX = 3;
				int depOffY = procH / 4;
				g.drawLine(x, y + procH / 2, px - depOffX, y + procH / 2);
				g.drawLine(px - depOffX, y + procH / 2, px - depOffX, py - depOffY);
				g.drawLine(px - depOffX, py - depOffY, px, py - depOffY);
			} else {
				g.drawLine(x, y + procH / 2, px, y + procH / 2);
				g.drawLine(px, y + procH / 2, px, py);
			}
		}
		if (g instanceof Graphics2D) {
			((Graphics2D)g).setStroke(new BasicStroke());
		}

		int lastTx = -1;
		for (Iterator i=proc.samples.iterator(); i.hasNext(); ) {
			ProcessSample sample = (ProcessSample)i.next();
			Date endTime = new Date(
				proc.startTime.getTime() + proc.duration - procTree.samplePeriod);
			if (sample.time.before(proc.startTime) ||
				sample.time.after(endTime)) {
				//log.warning("Cropped sample: " + sample);
				continue;
			}
			int tx = rect.x
				+ (int)Math.round(((sample.time.getTime() - procTree.startTime
					.getTime())
					* rect.width / (double)procTree.duration));
			int tw = (int)Math.round(procTree.samplePeriod * rect.width
				/ (double)procTree.duration);
			if (lastTx != -1 && Math.abs(lastTx - tx) <= tw) {
				tw -= lastTx - tx;
				tx = lastTx;
			}
			lastTx = tx + tw;
			int state = sample.state;
			double cpu = sample.cpu.user + sample.cpu.sys;
			
			boolean fillRect = false;
			switch (state) {
				case Process.STATE_WAITING:
					setColor(g, PROC_COLOR_D);
					fillRect = true;
					break;
				case Process.STATE_RUNNING:
					int alpha = (int)(cpu * 255);
					alpha = Math.max(0, Math.min(alpha, 255));
					Color c = new Color(PROC_COLOR_R.getRed(),
						PROC_COLOR_R.getGreen(), PROC_COLOR_R.getBlue(), alpha);
					setColor(g, c, PROC_COLOR_S);
					fillRect = true;
					break;
				case Process.STATE_STOPPED:
					setColor(g, PROC_COLOR_T);
					fillRect = true;
					break;
				case Process.STATE_ZOMBIE:
					setColor(g, PROC_COLOR_Z);
					fillRect = true;
					break;
				case Process.STATE_SLEEPING:
				default:
					fillRect = false;
					break;
			}
			if (fillRect) {
				g.fillRect(tx, y, tw, procH);
			}
		}
		
		setColor(g, PROC_BORDER_COLOR);
		g.drawRect(x, y, w, procH);

		setColor(g, PROC_TEXT_COLOR);
		FontMetrics fm = g.getFontMetrics(PROC_TEXT_FONT);
		//String label = proc.pid + " " + proc.cmd + " " + proc.ppid;
		String label = proc.cmd;
		int strW = fm.stringWidth(label);
		int sx = x + w / 2 - strW / 2;
		if (strW + 10 > w) {
			sx = x + w + 5;
		}
		if (sx + strW > rect.x + rect.width) {
			sx = x - strW - 5;
		}
		g.drawString(label, sx, y + procH - 2);
	}
	
	/**
	 * Sets the current color.  If <code>allowAlpha</code> is not set (e.g.
	 * for the EPS renderer), an opaque color is used.
	 * 
	 * @param g        graphics context
	 * @param col      the color to set
	 * @param backCol  background color
	 */
	private void setColor(Graphics g, Color col, Color backCol) {
		if (!allowAlpha) {
			int alpha = col.getAlpha();
			int red = col.getRed();
			red = red + (int)((backCol.getRed() - red) * (backCol.getRed() - alpha)/255.0);
			int green = col.getGreen();
			green = green + (int)((backCol.getGreen() - green) * (backCol.getGreen() - alpha)/255.0);
			int blue = col.getBlue();
			blue = blue + (int)((backCol.getBlue() - blue) * (backCol.getBlue() - alpha)/255.0);
			col = new Color(red, green, blue, 255);
		}
		g.setColor(col);
	}
	
	private void setColor(Graphics g, Color col) {
		setColor(g, col, Color.WHITE);
	}
	
	private static Color opaque(Color col) {
		return new Color(col.getRed(), col.getGreen(), col.getBlue(), 255);
	}
	
	/**
	 * Checks whether the statistics instance contains any samples of the
	 * specified class.
	 * 
	 * @param stats        statistics instance
	 * @param sampleClass  the class to look for
	 * @return             <code>true</code> if the statistics instance
	 *                     contains any samples of the specified class,
	 *                     <code>false</code> otherwise
	 */
	private static boolean hasSamples(Stats stats, Class sampleClass) {
		if (stats == null || stats.getSamples() == null || stats.getSamples().isEmpty()) {
			return false;
		}
		for (Iterator i=stats.getSamples().iterator(); i.hasNext(); ) {
			Sample sample = (Sample)i.next();
			if (sample.getClass().equals(sampleClass)) {
				return true;
			}
		}
		return false;
	}
	
	public abstract String getFileSuffix();
}
