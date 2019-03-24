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

    public static final MavenVersionRange ALL;
    private org.apache.maven.artifact.versioning.VersionRange range;

    static {
        org.apache.maven.artifact.versioning.VersionRange range;
        try {
            range = org.apache.maven.artifact.versioning.VersionRange
                .createFromVersionSpec("[0,)");
        } catch (InvalidVersionSpecificationException e) {
            // Won't happen (checked).
            range = null;
        }
        ALL = new MavenVersionRange(range);
    }

    /**
     * Instantiates a new maven version range from the given range.
     *
     * @param range the range
     */
    public MavenVersionRange(
            org.apache.maven.artifact.versioning.VersionRange range) {
        this.range = range;
    }

    /**
     * Instantiates a new maven version range from the given representation.
     * If {@code version} is {@code} null it is considered to be
     * the "all inclusive range" ("[0,)").
     *
     * @param range the range
     */
    public MavenVersionRange(String range) {
        if (range == null) {
            this.range = ALL.range;
            return;
        }
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

    /**
     * Checks if is the provided version representation is a range.
     * If {@code version} is {@code} null it is considered to be
     * the "all inclusive range" ("[0,)").
     *
     * @param version the version
     * @return true, if is range
     */
    public static boolean isRange(String version) {
        if (version == null) {
            return true;
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
