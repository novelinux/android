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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import org.bootchart.common.BootStats;
import org.jibble.epsgraphics.EpsGraphics2D;



/**
 * EPSRenderer renders the boot chart as an EPS (encapsulated postscript)
 * image.
 */
public class EPSRenderer extends ImageRenderer {
	private static final Logger log = Logger.getLogger(EPSRenderer.class.getName());
	
	/** Whether to compress EPS output. */
	public static final boolean COMPRESS_EPS = true;
	
	/*
	 * inherit javadoc
	 */
	public void render(Properties headers, BootStats bootStats, OutputStream os)
		throws IOException {
		g = new EpsGraphics2D();
		allowAlpha = false;
	 	super.render(headers, bootStats, null);
		log.fine("Writing image");
		
		if (COMPRESS_EPS) {
			GZIPOutputStream gzOs = new GZIPOutputStream(os);
			gzOs.write(g.toString().getBytes());
			gzOs.close();
		} else {
			os.write(g.toString().getBytes());
		}
	}
	
	/*
	 * inherit javadoc
	 */
	public String getFileSuffix() {
		return COMPRESS_EPS ? "eps.gz" : "eps";
	}
}
