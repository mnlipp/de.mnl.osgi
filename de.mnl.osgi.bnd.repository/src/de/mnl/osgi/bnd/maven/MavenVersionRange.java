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
 * Provides a representation of a maven version range. The implementation 
 * is a small wrapper around
 * {@link org.apache.maven.artifact.versioning.VersionRange}.
 * <P>
 * Because {@link org.apache.maven.artifact.versioning.VersionRange} has 
 * only a private constructor and cannot be extended, the wrapper
 * delegates to an instance of 
 * {@link org.apache.maven.artifact.versioning.VersionRange}.
 */
public class MavenVersionRange {

    private org.apache.maven.artifact.versioning.VersionRange range;

    /**
     * Instantiates a new maven version range from the given representation.
     *
     * @param range the range
     */
    public MavenVersionRange(String range) {
        try {
            this.range = org.apache.maven.artifact.versioning.VersionRange
                .createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns the version range that this instance delegates to.
     *
     * @return the org.apache.maven.artifact.versioning. version range
     */
    public org.apache.maven.artifact.versioning.VersionRange versionRange() {
        return range;
    }

    /**
     * Checks if this version range includes the specified version.
     *
     * @param mavenVersion the maven version
     * @return the result
     */
    public boolean includes(MavenVersion mavenVersion) {
        return range.containsVersion(mavenVersion);
    }

    /**
     * Creates a new maven version range from the given representation.
     *
     * @param version the string representation
     * @return the maven version range
     */
    public static MavenVersionRange parseRange(String version) {
        return new MavenVersionRange(version);
    }

    public boolean wasSingle() {
        if (range.getRestrictions().size() != 1) {
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return range.hashCode();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MavenVersionRange) {
            return range.equals(((MavenVersionRange) obj).range);
        }
        if (obj instanceof org.apache.maven.artifact.versioning.VersionRange) {
            return range.equals(
                (org.apache.maven.artifact.versioning.VersionRange) obj);
        }
        return false;
    }

}
