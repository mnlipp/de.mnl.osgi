/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2017  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.bnd.maven;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.Restriction;

/**
 * Provides a representation of a maven version range. The implementation is a
 * small wrapper around
 * {@link org.apache.maven.artifact.versioning.VersionRange}.
 */
public class MavenVersionRange {

    private org.apache.maven.artifact.versioning.VersionRange range;

    public MavenVersionRange(String range) {
        try {
            this.range = org.apache.maven.artifact.versioning.VersionRange
                .createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public boolean includes(MavenVersion mvr) {
        return range.containsVersion(mvr);
    }

    public static MavenVersionRange parseRange(String version) {
        try {
            return new MavenVersionRange(version);
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public boolean wasSingle() {
        if (range.getRestrictions()
            .size() != 1) {
            return false;
        }
        Restriction rstrct = range.getRestrictions().get(0);
        return rstrct.getLowerBound() == null && rstrct.getUpperBound() == null;
    }

    public static boolean isRange(String version) {
        if (version == null) {
            return false;
        }
        version = version.trim();
        return version.startsWith("[") || version.startsWith("(");
    }

    @Override
    public String toString() {
        return range.toString();
    }
}
