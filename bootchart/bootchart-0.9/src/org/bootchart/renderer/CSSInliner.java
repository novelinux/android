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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bootchart.common.Common;


/**
 * CSSInliner enables inlining of a CSS file into an SVG document.  It is
 * used as a workaround for SVG renderers which do not support CSS (e.g.
 * ksvg).  Note that the CSS parser and inliner are very simple and will only
 * work for class properties without any cascading.
 */
public class CSSInliner  {
	//private static final Logger log = Logger.getLogger(CSSInliner.class.getName());
	
	/** CSS class pattern. */
	private static final Pattern CLASS_PATTERN =
		Pattern.compile("(\\S+)\\s*\\{([^\\}]*)\\}");
	
	/**
	 * Parses the specified CSS file and inlines all style properties in the
	 * SVG content string.
	 * 
	 * @param svg      SVG contents
	 * @param cssFile  CSS file to inline
	 * @return         the SVG contents with inlines style
	 * @throws FileNotFoundException  if the CSS file cannot be found
	 * @throws IOException            if an I/O error occurs
	 */
	public static String inline(String svg, File cssFile)
		throws FileNotFoundException, IOException {
		Properties props = parseCSS(cssFile);
		for (Iterator i=props.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry entry = (Map.Entry)i.next();
			svg = svg.replaceAll("class=\"" + entry.getKey() + "\"",
				                 "style=\"" + entry.getValue() + "\"");
		}
		return svg;
	}
	
	/**
	 * Parses the CSS file and creates a <code>Properties</code> instance
	 * containing class names as keys and style properties as values.
	 * 
	 * @param file  the CSS file to parse
	 * @return      style properties
	 * @throws FileNotFoundException  if the CSS file cannot be found
	 * @throws IOException            if an I/O error occurs
	 */
	private static Properties parseCSS(File file)
		throws FileNotFoundException, IOException {
		String css = Common.loadFile(file);
		Properties props = new Properties();
		Matcher matcher = CLASS_PATTERN.matcher(css);
		while (matcher.find()) {
			String cssClass = matcher.group(1);
			if (cssClass.startsWith(".")) {
				cssClass = cssClass.substring(1);
			}
			String style = matcher.group(2);
			style = style.replaceAll("\\s+", " ").trim();
			String oldStyle = props.getProperty(cssClass);
			if (oldStyle != null) {
				props.setProperty(cssClass, oldStyle + " " + style);
			} else {
				props.setProperty(cssClass, style);
			}
		}
		return props;
	}
	
}
