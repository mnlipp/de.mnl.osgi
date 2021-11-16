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

import java.util.Comparator;
import java.util.List;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;

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
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class MavenVersionRange extends MavenVersionSpecification {

    public static final MavenVersionRange ALL;
    public static final MavenVersionRange NONE;
    private static final ArtifactVersion ZERO = new DefaultArtifactVersion("0");
    private VersionRange range;

    static {
        VersionRange range;
        try {
            range = VersionRange.createFromVersionSpec("[0,)");
        } catch (InvalidVersionSpecificationException e) {
            // Won't happen (checked).
            range = null;
        }
        ALL = new MavenVersionRange(range);
        try {
            range = VersionRange.createFromVersionSpec("[,0)");
        } catch (InvalidVersionSpecificationException e) {
            // Won't happen (checked).
            range = null;
        }
        NONE = new MavenVersionRange(range);
    }

    /**
     * Instantiates a new maven version range from the given range.
     *
     * @param range the range
     */
    public MavenVersionRange(VersionRange range) {
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
            this.range = VersionRange.createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns the version range that this instance delegates to.
     *
     * @return the org.apache.maven.artifact.versioning. version range
     */
    public VersionRange versionRange() {
        return range;
    }

    /**
     * Returns the complementary version rang.
     *
     * @return the maven version range
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public MavenVersionRange complement() {
        List<Restriction> restrictions = range.getRestrictions();
        restrictions.sort(Comparator.comparing(Restriction::getLowerBound));
        ArtifactVersion lastVersion = new DefaultArtifactVersion("0");
        boolean lastUpperInclusive = false;
        StringBuilder cmpl = new StringBuilder();
        for (Restriction rstrct : restrictions) {
            ArtifactVersion rstrctLower = rstrct.getLowerBound();
            if (rstrctLower == null) {
                rstrctLower = ZERO;
            }
            int cmp = lastVersion.compareTo(rstrctLower);
            if (cmp < 0 || cmp == 0
                && !(lastUpperInclusive || rstrct.isLowerBoundInclusive())) {
                // Not overlap or continuation.
                if (cmpl.length() > 0) {
                    cmpl.append(',');
                }
                cmpl.append(lastUpperInclusive ? '(' : '[');
                cmpl.append(lastVersion.toString());
                cmpl.append(',');
                cmpl.append(rstrct.getLowerBound().toString());
                cmpl.append(rstrct.isLowerBoundInclusive() ? ')' : ']');
            }
            lastVersion = rstrct.getUpperBound();
            lastUpperInclusive = rstrct.isUpperBoundInclusive();
            if (lastVersion == null) {
                // Any restriction with open upper end is final
                // (cannot add range to maximum range).
                break;
            }
        }
        if (lastVersion == null) {
            // Open ended, check if it was "all" ("[0,)")
            if (cmpl.length() == 0) {
                cmpl.append("[,0)");
            }
        } else {
            // Not open ended, so we must provide the last restriction.
            if (cmpl.length() > 0) {
                cmpl.append(',');
            }
            cmpl.append(lastUpperInclusive ? '(' : '[');
            cmpl.append(lastVersion.toString());
            cmpl.append(",)");
        }
        return new MavenVersionRange(cmpl.toString());
    }

    /**
     * Checks if this version range includes the specified version
     * or range. A range is included if it is fully included.
     *
     * @param mavenVersion the maven version
     * @return the result
     */
    public boolean includes(MavenVersionSpecification mavenVersion) {
        if (mavenVersion instanceof MavenVersion) {
            return range.containsVersion((MavenVersion) mavenVersion);
        }
        return restrict((MavenVersionRange) mavenVersion).range
            .getRestrictions().isEmpty();
    }

    /**
     * Creates and returns a new VersionRange that is a restriction 
     * of this version range and the specified version range.
     *
     * @see VersionRange#restrict
     *
     * @param restriction the restriction
     * @return the maven version range
     */
    public MavenVersionRange restrict(MavenVersionRange restriction) {
        return new MavenVersionRange(range.restrict(restriction.range));
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
     * @deprecated Use {@link MavenVersionSpecification#isRange(String)} instead
     */
    public static boolean isRange(String version) {
        return MavenVersionSpecification.isRange(version);
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
        if (obj instanceof VersionRange) {
            return range.equals((VersionRange) obj);
        }
        return false;
    }

}
