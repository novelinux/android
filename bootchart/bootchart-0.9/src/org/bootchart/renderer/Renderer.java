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

import org.bootchart.common.BootStats;


/**
 * Renderable is a common interface for different boot chart renderers.
 */
public abstract class Renderer {
	//private static final Logger log = Logger.getLogger(Renderer.class.getName());
	
	/**
	 * Render the chart and output to an output stream.
	 * 
	 * @param headers       header properties to include in the title banner
	 * @param bootStats     boot statistics
	 * @param os            the output stream to write to
	 * @throws IOException  if an I/O error occurs
	 */
	public abstract void render(Properties headers, BootStats bootStats, OutputStream os) throws IOException;
	
	/**
	 * Returns the file suffix to use for the rendered image.
	 * 
	 * @return  file suffix
	 */
	public abstract String getFileSuffix();
}
