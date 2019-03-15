/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019  Michael N. Lipp
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

import aQute.bnd.version.Version;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Provides a model of an artifact version which can be used as a maven version.
 * <P>
 * The maven <a href="https://maven.apache.org/pom.html">POM reference</a> does
 * not define a format for versions. This is presumably intentional as it allows
 * artifacts with arbitrary versioning schemes to be referenced in a POM.
 * <P>
 * Maven tooling, on the other hand side, defines a rather <a href=
 * "http://books.sonatype.com/mvnref-book/reference/pom-relationships-sect-pom-syntax.html#pom-reationships-sect-versions">restrictive
 * version number pattern</a> for maven projects. Non-compliant version numbers
 * are parsed as qualifier-only versions.
 * <P>
 * The parsing methods of this class make an attempt to interpret a version
 * number as a
 * &lt;major&gt;/&lt;minor&gt;/&lt;micro/incremental&gt;/&lt;qualifier&gt;
 * pattern, even if it does not match the restrictive maven project version
 * number pattern. The string representation of an instance of this class is
 * always the original, unparsed (or "literal") representation because due to
 * the permissive parsing algorithm used, the original representation cannot
 * faithfully be reconstructed from the parsed components.
 * <P>
 * Contrary to bnd's {@link aQute.bnd.version.MavenVersion}, this 
 * implementation inherits from {@link ArtifactVersion}, i.e. from the
 * version as implemented by maven.
 */
@SuppressWarnings("PMD.GodClass")
public class MavenVersion implements ArtifactVersion {

    /** The usual format of a verson string. */
    public static final String VERSION_STRING
        = "(\\d{1,15})(\\.(\\d{1,9})(\\.(\\d{1,9}))?)?([-\\.]?([-_\\.\\da-zA-Z]+))?";

    /** The usual format for a snapshot timestamp. */
    public static final SimpleDateFormat SNAPSHOT_TIMESTAMP
        = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.ROOT);

    static {
        SNAPSHOT_TIMESTAMP.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final Pattern VERSION = Pattern.compile(VERSION_STRING);

    /** The snapshot identifier. */
    public static final String SNAPSHOT = "SNAPSHOT";

    /** The Constant HIGHEST. */
    public static final MavenVersion HIGHEST
        = new MavenVersion(Version.HIGHEST);

    /** The Constant LOWEST. */
    public static final MavenVersion LOWEST = new MavenVersion("0");

    // Used as "container" for the components of the maven version number
    private final Version version;
    // Some maven versions are too odd to be restored after parsing, keep
    // original.
    private final String literal;
    // Used for comparison, cached for efficiency.
    private final ComparableVersion comparable;

    private final boolean snapshot;

    /**
     * Creates a new instance. The maven version is parsed by an instance
     * of {@link DefaultArtifactVersion}. The parsing is thus fully
     * maven compliant.
     *
     * @param maven the version
     */
    public MavenVersion(String maven) {
        this.literal = maven;
        this.comparable = new ComparableVersion(literal);
        DefaultArtifactVersion artVer = new DefaultArtifactVersion(maven);
        this.version
            = new Version(artVer.getMajorVersion(), artVer.getMinorVersion(),
                artVer.getIncrementalVersion(), artVer.getQualifier());
        this.snapshot = version.isSnapshot();
    }

    /**
     * Creates a new maven version from an osgi version. The version components
     * are copied, the string representation is built from the components as
     * "&lt;major&gt;.&lt;minor&gt;.&lt;micro&gt;.&lt;qualifier&gt;"
     *
     * @param osgiVersion the osgi version
     */
    public MavenVersion(Version osgiVersion) {
        this.version = osgiVersion;
        StringBuilder qual = new StringBuilder("");
        if (this.version.getQualifier() != null) {
            qual.append('-');
            qual.append(this.version.getQualifier());
        }
        this.literal = osgiVersion.getWithoutQualifier().toString() + qual;
        this.comparable = new ComparableVersion(literal);
        this.snapshot = osgiVersion.isSnapshot();
    }

    /**
     * Creates a new maven version from an osgi version and an unparsed 
     * literal. The version components are copied, the literal is used
     * as string representation, the snapshot property is taken from
     * the argument.
     *
     * @param osgiVersion the osgi version
     * @param literal the literal
     * @param isSnapshot whether it is a snapshot version
     */
    public MavenVersion(Version osgiVersion, String literal,
            boolean isSnapshot) {
        this.literal = literal;
        this.comparable = new ComparableVersion(literal);
        this.version = osgiVersion;
        this.snapshot = isSnapshot;
    }

    /**
     * Parses the string as a maven version, but allows a dot as separator
     * before the qualifier.
     * <P>
     * Leading sequences of digits followed by a dot or dash are converted to
     * the major, minor and incremental version components. A dash or a dot that
     * is not followed by a digit or the third dot is interpreted as the start
     * of the qualifier.
     * <P>
     * In particular, version numbers such as "1.2.3.4.5" are parsed as major=1,
     * minor=2, incremental=3 and qualifier="4.5". This is closer to the
     * (assumed) semantics of such a version number than the parsing implemented
     * in maven tooling, which interprets the complete version as a qualifier in
     * such cases.
     *
     * @param versionStr the version string
     * @return the maven version
     * @throws IllegalArgumentException if the version cannot be parsed
     */
    public static final MavenVersion parseString(String versionStr) {
        if (versionStr == null) {
            versionStr = "0";
        } else {
            versionStr = versionStr.trim();
            if (versionStr.isEmpty()) {
                versionStr = "0";
            }
        }
        Matcher matcher = VERSION.matcher(versionStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid syntax for version: " + versionStr);
        }
        int major = Integer.parseInt(matcher.group(1));
        @SuppressWarnings("PMD.ConfusingTernary")
        int minor = (matcher.group(3) != null)
            ? Integer.parseInt(matcher.group(3))
            : 0;
        @SuppressWarnings("PMD.ConfusingTernary")
        int micro = (matcher.group(5) != null)
            ? Integer.parseInt(matcher.group(5))
            : 0;
        String qualifier = matcher.group(7);
        Version version = new Version(major, minor, micro, qualifier);
        return new MavenVersion(version);
    }

    /**
     * Similar to {@link #parseString(String)}, but returns {@code null} if the
     * version cannot be parsed.
     * 
     * @param versionStr the version string
     * @return the maven version
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public static final MavenVersion parseMavenString(String versionStr) {
        try {
            return new MavenVersion(versionStr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a new {@link MavenVersion} from a 
     * the given representation, see {@link #MavenVersion(String)}.
     *
     * @param maven the maven version string
     * @return the maven version
     */
    public static final MavenVersion from(String maven) {
        return new MavenVersion(maven);
    }

    /**
     * Creates a new {@link MavenVersion} from a 
     * bnd {@link aQute.bnd.version.MavenVersion}.
     * Propagates {@code null} values.
     *
     * @param bndVer the bnd maven version
     * @return the maven version
     */
    public static final MavenVersion
            from(aQute.bnd.version.MavenVersion bndVer) {
        if (bndVer == null) {
            return null;
        }
        return new MavenVersion(bndVer.getOSGiVersion(), bndVer.toString(),
            bndVer.isSnapshot());
    }

    /**
     * Converts this version to a
     * bnd {@link aQute.bnd.version.MavenVersion}.
     * Propagates {@code null} pointers.
     *
     * @return the a qute.bnd.version. maven version
     */
    public static aQute.bnd.version.MavenVersion
            toBndMavenVersion(MavenVersion version) {
        if (version == null) {
            return null;
        }
        return new aQute.bnd.version.MavenVersion(version.version);
    }

    /**
     * Converts this version to a
     * bnd {@link aQute.bnd.version.MavenVersion}.
     *
     * @return the a qute.bnd.version. maven version
     */
    public aQute.bnd.version.MavenVersion asBndMavenVersion() {
        return new aQute.bnd.version.MavenVersion(version);
    }

    /**
     * This method is required by the {@link ArtifactVersion} interface.
     * However, because instances of this class are intended to be immutable, it
     * is not implemented. Use one of the other {@code parse...} methods
     * instead.
     *
     * @param version the version to parse
     * @throws UnsupportedOperationException in any case
     */
    @Override
    public void parseVersion(String version) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.maven.artifact.versioning.ArtifactVersion#getMajorVersion()
     */
    @Override
    public int getMajorVersion() {
        return version.getMajor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.maven.artifact.versioning.ArtifactVersion#getMinorVersion()
     */
    @Override
    public int getMinorVersion() {
        return version.getMinor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.artifact.versioning.ArtifactVersion#
     * getIncrementalVersion()
     */
    @Override
    public int getIncrementalVersion() {
        return version.getMicro();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.maven.artifact.versioning.ArtifactVersion#getBuildNumber()
     */
    @Override
    public int getBuildNumber() {
        return new DefaultArtifactVersion(literal).getBuildNumber();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.artifact.versioning.ArtifactVersion#getQualifier()
     */
    @Override
    public String getQualifier() {
        return version.getQualifier();
    }

    /**
     * Gets the comparable.
     *
     * @return the comparable
     */
    public ComparableVersion getComparable() {
        return comparable;
    }

    /**
     * Gets the osgi version.
     *
     * @return the osgi version
     */
    public Version getOsgiVersion() {
        return version;
    }

    /**
     * If the qualifier ends with -SNAPSHOT or for an OSGI version with a
     * qualifier that is SNAPSHOT.
     *
     * @return true, if is snapshot
     */
    public boolean isSnapshot() {
        return snapshot;
    }

    /**
     * Compares maven version numbers according to the rules defined in the
     * <a href=
     * "https://maven.apache.org/pom.html#Version_Order_Specification">POM
     * reference</a>.
     *
     * @param other the other
     * @return the int
     */
    @Override
    public int compareTo(ArtifactVersion other) {
        if (other instanceof MavenVersion) {
            return comparable.compareTo(((MavenVersion) other).comparable);
        }
        return comparable.compareTo(new ComparableVersion(other.toString()));
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return literal;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public int hashCode() {
        @SuppressWarnings("PMD.AvoidFinalLocalVariable")
        final int prime = 31;
        int result = 1;
        result = prime * result + ((literal == null) ? 0 : literal.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MavenVersion other = (MavenVersion) obj;
        return literal.equals(other.literal);
    }

    /**
     * To snapshot.
     *
     * @return the maven version
     */
    public MavenVersion toSnapshot() {
        Version newv = new Version(version.getMajor(), version.getMinor(),
            version.getMicro(), SNAPSHOT);
        return new MavenVersion(newv);
    }

    /**
     * To snapshot.
     *
     * @param epoch the epoch
     * @param build the build
     * @return the maven version
     */
    public MavenVersion toSnapshot(long epoch, String build) {
        return toSnapshot(toDateStamp(epoch, build));
    }

    /**
     * To snapshot.
     *
     * @param timestamp the timestamp
     * @param build the build
     * @return the maven version
     */
    public MavenVersion toSnapshot(String timestamp, String build) {
        if (build != null) {
            timestamp += "-" + build;
        }
        return toSnapshot(timestamp);
    }

    /**
     * To snapshot.
     *
     * @param dateStamp the date stamp
     * @return the maven version
     */
    public MavenVersion toSnapshot(String dateStamp) {
        // -SNAPSHOT == 9 characters
        String clean = literal.substring(0, literal.length() - 9);
        String result = clean + "-" + dateStamp;

        return new MavenVersion(result);
    }

    /**
     * Validate.
     *
     * @param value the value
     * @return the string
     */
    public static String validate(String value) {
        if (value == null) {
            return "Version is null";
        }
        if (!VERSION.matcher(value).matches()) {
            return "Not a version";
        }
        return null;
    }

    /**
     * To date stamp.
     *
     * @param epoch the epoch
     * @return the string
     */
    public static String toDateStamp(long epoch) {
        String datestamp;
        synchronized (SNAPSHOT_TIMESTAMP) {
            datestamp = SNAPSHOT_TIMESTAMP.format(new Date(epoch));
        }
        return datestamp;

    }

    /**
     * To date stamp.
     *
     * @param epoch the epoch
     * @param build the build
     * @return the string
     */
    public static String toDateStamp(long epoch, String build) {
        StringBuilder str = new StringBuilder(toDateStamp(epoch));
        if (build != null) {
            str.append('-');
            str.append(build);
        }
        return str.toString();
    }

    /**
     * Cleanup version.
     *
     * @param version the version
     * @return the string
     */
    public static String cleanupVersion(String version) {
        return aQute.bnd.version.MavenVersion.cleanupVersion(version);
    }

}
