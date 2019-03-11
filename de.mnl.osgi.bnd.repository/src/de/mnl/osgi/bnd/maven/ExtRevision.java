/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2018 Michael N. Lipp
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

package de.mnl.osgi.bnd.maven;

import aQute.bnd.version.MavenVersion;
import aQute.maven.api.Archive;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;

/**
 * A revision with the a reference to the maven repository
 * in which it was found.
 * <P>
 * For some incomprehensible reason, {@link Revision} has only an
 * invisible constructor. So it cannot be extended.
 */
public class ExtRevision {

    private MavenBackingRepository mavenBackingRepository;
    private Revision revision;

    public ExtRevision(MavenBackingRepository mavenBackingRepository,
            Revision revision) {
        this.mavenBackingRepository = mavenBackingRepository;
        this.revision = revision;
    }

    /**
     * Gets the maven backing repository.
     *
     * @return the mavenBackingRepository
     */
    public final MavenBackingRepository mavenBackingRepository() {
        return mavenBackingRepository;
    }

    /**
     * Gets the revision.
     *
     * @return the revision
     */
    public final Revision revision() {
        return revision;
    }

    /**
     * Checks if is snapshot.
     *
     * @return true, if is snapshot
     * @see aQute.maven.api.Revision#isSnapshot()
     */
    public boolean isSnapshot() {
        return revision.isSnapshot();
    }

    /**
     * Get an archive from this revision.
     *
     * @param extension the extension
     * @param classifier the classifier
     * @return the archive
     * @see aQute.maven.api.Revision#archive(java.lang.String, java.lang.String)
     */
    public Archive archive(String extension, String classifier) {
        return revision.archive(extension, classifier);
    }

    /**
     * Get an archive from this revision.
     *
     * @param version the version
     * @param extension the extension
     * @param classifier the classifier
     * @return the archive
     */
    public Archive archive(MavenVersion version, String extension,
            String classifier) {
        return revision.archive(version, extension, classifier);
    }

    /**
     * Returns the Metadata.
     *
     * @return the string
     * @see aQute.maven.api.Revision#metadata()
     */
    public String metadata() {
        return revision.metadata();
    }

    /**
     * Returns the metadata for the given id.
     *
     * @param id the id
     * @return the string
     * @see aQute.maven.api.Revision#metadata(java.lang.String)
     */
    public String metadata(String id) {
        return revision.metadata(id);
    }

    /**
     * @return
     * @see aQute.maven.api.Revision#toString()
     */
    public String toString() {
        return revision.toString() + "[" + mavenBackingRepository.toString()
            + "]";
    }

    /**
     * Return the pom archive.
     *
     * @return the archive
     * @see aQute.maven.api.Revision#pomArchive()
     */
    public Archive pomArchive() {
        return revision.pomArchive();
    }

    /**
     * Gets the pom archive.
     *
     * @return the pom archive
     * @see aQute.maven.api.Revision#getPomArchive()
     */
    public Archive getPomArchive() {
        return revision.getPomArchive();
    }

    /**
     * Compare to other revision. Ignores repository.
     *
     * @param other the other
     * @return the int
     * @see aQute.maven.api.Revision#compareTo(aQute.maven.api.Revision)
     */
    public int compareTo(Revision other) {
        return revision.compareTo(other);
    }

    /**
     * Hash code. Ignores repository.
     *
     * @return the int
     * @see aQute.maven.api.Revision#hashCode()
     */
    public int hashCode() {
        return revision.hashCode();
    }

    /**
     * Equals. Ignores respository.
     *
     * @param obj the obj
     * @return true, if successful
     * @see aQute.maven.api.Revision#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        return revision.equals(obj);
    }

}
