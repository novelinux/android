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
package org.bootchart.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.bootchart.Main;
import org.bootchart.renderer.EPSRenderer;
import org.bootchart.renderer.PNGRenderer;
import org.bootchart.renderer.SVGRenderer;

/**
 * TopParser parses log files produced by <code>top</code>.  The log files
 * are produced when running <code>top</code> in batch mode.
 */
public class RenderServlet extends HttpServlet {
	private static final Logger log = Logger.getLogger(RenderServlet.class.getName());
	
	/** Temp file directory. */
	private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
	
	private static final String LOG_FORMAT_ERROR =
		"Error: invalid log file provided.  " +
		"Please visit the project page to check for possible upgrades.";
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		PrintWriter writer = response.getWriter();
		writer.println("<html>");
		writer.println("<form method=\"post\" action=\"render\" enctype=\"multipart/form-data\">");
		writer.println("<input type=\"file\" name=\"log\" size=\"32\" /><br />");
		writer.println("<br />");
		writer.println("Image format:");
		writer.println("<label for=\"pngformat\"><input type=\"radio\" name=\"format\" id=\"pngformat\" value=\"png\" checked=\"checked\" />PNG</label>");
		writer.println("<label for=\"svgformat\"><input type=\"radio\" name=\"format\" id=\"svgformat\" value=\"svg\" />SVG</label>");
		writer.println("<label for=\"epsformat\"><input type=\"radio\" name=\"format\" id=\"epsformat\" value=\"eps\" />EPS</label><br /><br />");
		writer.println("<input type=\"submit\" value=\"Render Chart\" />");
		writer.println("</form>");
		writer.println("</html>");
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		File logTmpFile = null;
		
		try {
			DiskFileUpload fu = new DiskFileUpload();
	        // maximum size before a FileUploadException will be thrown
	        fu.setSizeMax(32 * 1024 * 1024);
	        fu.setSizeThreshold(4 * 1024 * 1024);
	        fu.setRepositoryPath(TEMP_DIR);
	
	        String format = "png";
	        List fileItems = fu.parseRequest(request);
	        File tmpFile = File.createTempFile("file.", ".tmp");
	        String tmpName = tmpFile.getName().substring(5, tmpFile.getName().length()-4);
	        tmpFile.delete();
	        
	        for (Iterator i=fileItems.iterator(); i.hasNext(); ) {
		        FileItem fi = (FileItem)i.next();
		        String name = fi.getName();
		        if (name == null || name.length() == 0) {
		        	if ("format".equals(fi.getFieldName())) {
		        		format = fi.getString();
		        	}
		        	continue;
		        }
		        if (name.indexOf("bootchart") != -1) {
		        	String suffix = "";
			        if (name.endsWith(".tar.gz")) {
			        	suffix = ".tar.gz";
			        } else {
			        	suffix = name.substring(name.lastIndexOf('.'));
			        }
		        	logTmpFile = new File(TEMP_DIR, "bootchart." + tmpName + suffix);
			        fi.write(logTmpFile);
		        }
	        }
	        
	        if (logTmpFile == null || !logTmpFile.exists()) {
	        	writeError(response, LOG_FORMAT_ERROR, null);
	        	log.severe("No log tarball provided");
	        	return;
	        }

	        // Render PNG by default
	        if (format == null) {
	        	format = "png";
	        }
	        boolean prune = true;
			
			new File(TEMP_DIR + "/images").mkdirs();
			String tmpImgFileName = TEMP_DIR + "/images/" + "bootchart." + tmpName;
			File tmpImgFile = null;
			try {
				tmpImgFileName =
					Main.render(logTmpFile, format, prune, tmpImgFileName);
				tmpImgFile = new File(tmpImgFileName);
				FileInputStream fis = new FileInputStream(tmpImgFileName);
				OutputStream os = response.getOutputStream();
				String contentType = "application/octet-stream";
				String suffix = "";
				if ("png".equals(format)) {
					contentType = "image/png";
					suffix = new PNGRenderer().getFileSuffix();
				} else if ("svg".equals(format)) {
					contentType = "image/svg+xml";
					suffix = new SVGRenderer().getFileSuffix();
				} else if ("eps".equals(format)) {
					contentType = "image/eps";
					suffix = new EPSRenderer().getFileSuffix();
				}
				response.setContentType(contentType);
				response.setHeader("Content-disposition",
					"attachment; filename=bootchart." + suffix);
				byte[] buff = new byte[4096];
				while (true) {
				    int read = fis.read(buff);
				    if (read < 0) {
				        break;
				    }
				    os.write(buff, 0, read);
				    os.flush();
				}
				fis.close();
			} finally {
				if (tmpImgFile != null && !tmpImgFile.delete()) {
					tmpImgFile.deleteOnExit();
				}
			}
			
		} catch (Exception e) {
			writeError(response, null, e);
			log.log(Level.SEVERE, "", e);
		}
		
		if (logTmpFile != null && !logTmpFile.delete()) {
			logTmpFile.deleteOnExit();
		}
	}
	
	/**
	 * Writes the specified error and/or exception and sets the HTTP status
	 * code to 505 (server error).
	 * 
	 * @param response      HTTP servlet response
	 * @param msg           error message (or <code>null</code>)
	 * @param e             exception (or <code>null</code>)
	 * @throws IOException  if an I/O error occurs
	 */
	private static void writeError(HttpServletResponse response, String msg, Exception e)
		throws IOException {
		StringBuffer sb = new StringBuffer();
		if (msg != null) {
			sb.append(msg);
		}
		if (e != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(sw.toString());
		}
		response.setContentType("text/plain");
		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		response.getWriter().println(sb.toString());
	}

}
