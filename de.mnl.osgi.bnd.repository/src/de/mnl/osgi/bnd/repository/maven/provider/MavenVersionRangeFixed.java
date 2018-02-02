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

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import aQute.bnd.version.MavenVersion;
import aQute.bnd.version.MavenVersionRange;

// Workaround for https://github.com/bndtools/bnd/issues/2285
public class MavenVersionRangeFixed extends MavenVersionRange {

	private VersionRange range;
	
	public MavenVersionRangeFixed(String range) {
		super(null);
		try {
			this.range = VersionRange.createFromVersionSpec(range);
		} catch (InvalidVersionSpecificationException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public boolean includes(MavenVersion mvr) {
		return range.containsVersion(new DefaultArtifactVersion(mvr.toString()));
	}

	public String toString() {
		return range.toString();
	}


	public static MavenVersionRangeFixed parseRange(String version) {
		try {
			return new MavenVersionRangeFixed(version);
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

}
