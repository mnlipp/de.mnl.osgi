/*
 * Bnd Nexus Search Plugin
 * Copyright (C) 2017  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.bnd.repository.maven.provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import aQute.bnd.version.MavenVersion;
import aQute.bnd.version.MavenVersionRange;
import aQute.bnd.version.Version;

// Workaround for https://github.com/bndtools/bnd/issues/2285
public class MavenVersionRangeFixed extends MavenVersionRange {
	static final Pattern	RESTRICTION_P	= Pattern.compile(""

			+ "\\s*("											//
			+ "("												//
			+ "(?<li>\\[|\\()\\s*"								//
			+ "(?<low>[^,\\s\\]\\[()]*)\\s*"					//
			+ ",\\s*"											//
			+ "(?<high>[^,\\s\\[\\]()]*)\\s*"					//
			+ "(?<hi>\\]|\\))"									//
			+ ")"												//
			+ "|"												//
			+ "(?<single>[^,\\s\\]\\[()]+)"						//
			+ ")\\s*"											//
			+ "(?<comma>,)?\\s*", Pattern.COMMENTS);

	final boolean			li;
	final boolean			hi;
	final MavenVersion		low;
	final MavenVersion		high;

	MavenVersionRangeFixed		nextOr;

	public MavenVersionRangeFixed(String range) {
		this(RESTRICTION_P.matcher(range == null ? "0" : range));
	}

	private MavenVersionRangeFixed(Matcher m) {
		super (null);
		if (!m.lookingAt())
			throw new IllegalArgumentException("Invalid version range " + m);

		String single = m.group("single");
		if (single != null) {
			li = true;
			low = new MavenVersion(single);
			high = MavenVersion.HIGHEST;
			hi = true;
		} else {
			li = m.group("li").equals("[");
			hi = m.group("hi").equals("]");

			low = MavenVersion.parseMavenString(m.group("low"));
			if (m.group("high").trim().length() == 0) {
				high = new MavenVersion(new Version(999999999, 999999999, 999999999));
			} else {
				high = MavenVersion.parseMavenString(m.group("high"));
			}
		}

		if (m.group("comma") != null) {
			m.region(m.end(), m.regionEnd());
			nextOr = new MavenVersionRangeFixed(m);
		} else
			nextOr = null;
	}

	public boolean includes(MavenVersion mvr) {
		int l = mvr.compareTo(low);
		int h = mvr.compareTo(high);

		boolean lowOk = l > 0 || (li && l == 0);
		boolean highOk = h < 0 || (hi && h == 0);

		if (lowOk && highOk)
			return true;

		if (nextOr != null)
			return nextOr.includes(mvr);

		return false;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb);
		return sb.toString();
	}

	private void toString(StringBuilder sb) {
		if (li)
			sb.append("[");
		else
			sb.append("(");

		sb.append(low);
		sb.append(",");
		sb.append(high);
		if (hi)
			sb.append("]");
		else
			sb.append(")");

		if (nextOr != null) {
			sb.append(",");
			nextOr.toString(sb);
		}
	}

	public static MavenVersionRangeFixed parseRange(String version) {
		try {
			return new MavenVersionRangeFixed(version);
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

	public boolean wasSingle() {
		return (li && !hi && high == MavenVersion.HIGHEST && nextOr == null);
	}

	public static boolean isRange(String version) {
		if (version == null)
			return false;

		version = version.trim();
		return version.startsWith("[") || version.startsWith("(");
	}
}
