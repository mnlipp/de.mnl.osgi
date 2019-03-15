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

import aQute.bnd.version.MavenVersion;
import aQute.maven.api.Archive;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;

/**
 * A revision with a reference to the maven repository
 * in which it was found.
 * <P>
 * This class resorts to a delegation pattern as second best solution,
 * because {@link Revision} has only an invisible constructor and can 
 * therefore not be extended. The drawback is that the information
 * about the repository doesn't propagate, i.e. you cannot retrieve
 * it from an ordinary {@link Archive} related to this 
 * {@link BoundRevision}. If you need the information, you have to use 
 * a {@link BoundArchive} instead. 
 * <P>
 * @see <a href="https://github.com/bndtools/bnd/issues/3058">Related 
 * bnd issue</a>
 */
public class BoundRevision implements Comparable<BoundRevision> {

    private final MavenBackingRepository mavenBackingRepository;
    private final Revision unbound;

    /**
     * Instantiates a new bound revision.
     *
     * @param mavenBackingRepository the maven backing repository
     * @param revision the revision
     */
    public BoundRevision(MavenBackingRepository mavenBackingRepository,
            Revision revision) {
        this.mavenBackingRepository = mavenBackingRepository;
        this.unbound = revision;
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
    public Revision unbound() {
        return unbound;
    }

    /**
     * Returns the group id.
     *
     * @return the id
     */
    public String groupId() {
        return unbound.group;
    }

    /**
     * Returns the artifact id.
     *
     * @return the id
     */
    public String artifactId() {
        return unbound.artifact;
    }

    /**
     * Returns the version.
     *
     * @return the version
     */
    public MavenVersion version() {
        return unbound.version;
    }

    /**
     * Checks if is snapshot.
     *
     * @return true, if is snapshot
     * @see aQute.maven.api.Revision#isSnapshot()
     */
    public boolean isSnapshot() {
        return unbound.isSnapshot();
    }

    /**
     * Get an archive from this revision.
     *
     * @param extension the extension
     * @param classifier the classifier
     * @return the archive
     * @see aQute.maven.api.Revision#archive(java.lang.String, java.lang.String)
     */
    public BoundArchive archive(String extension, String classifier) {
        return new BoundArchive(this, null, extension, classifier);
    }

    /**
     * Get an archive from this revision.
     *
     * @param version the version
     * @param extension the extension
     * @param classifier the classifier
     * @return the archive
     */
    public BoundArchive archive(MavenVersion version, String extension,
            String classifier) {
        return new BoundArchive(this, version, extension, classifier);
    }

    /**
     * Returns the Metadata.
     *
     * @return the string
     * @see aQute.maven.api.Revision#metadata()
     */
    public String metadata() {
        return unbound.metadata();
    }

    /**
     * Returns the metadata for the given id.
     *
     * @param id the id
     * @return the string
     * @see aQute.maven.api.Revision#metadata(java.lang.String)
     */
    @SuppressWarnings("PMD.ShortVariable")
    public String metadata(String id) {
        return unbound.metadata(id);
    }

    /**
     * Returns the revision with the origin repository attached in
     * square brackets.
     * 
     * @return the string representation
     * @see aQute.maven.api.Revision#toString()
     */
    public String toString() {
        return unbound.toString() + "[" + mavenBackingRepository.toString()
            + "]";
    }

    /**
     * Return the pom archive.
     *
     * @return the archive
     * @see aQute.maven.api.Revision#pomArchive()
     */
    public Archive pomArchive() {
        return unbound.pomArchive();
    }

    /**
     * Gets the pom archive.
     *
     * @return the pom archive
     * @see aQute.maven.api.Revision#getPomArchive()
     */
    public Archive getPomArchive() {
        return unbound.getPomArchive();
    }

    /**
     * Compare to other revision. Ignores repository.
     *
     * @param other the other
     * @return the int
     * @see aQute.maven.api.Revision#compareTo(aQute.maven.api.Revision)
     */
    public int compareTo(Revision other) {
        return unbound.compareTo(other);
    }

    /**
     * Compare to other revision. Ignores repository.
     *
     * @param other the other
     * @return the int
     * @see aQute.maven.api.Revision#compareTo(aQute.maven.api.Revision)
     */
    public int compareTo(BoundRevision other) {
        return unbound.compareTo(other.unbound());
    }

    /**
     * Hash code. Ignores repository.
     *
     * @return the int
     * @see aQute.maven.api.Revision#hashCode()
     */
    public int hashCode() {
        return unbound.hashCode();
    }

    /**
     * Equals. Ignores respository.
     *
     * @param obj the obj
     * @return true, if successful
     * @see aQute.maven.api.Revision#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (obj instanceof BoundRevision) {
            return unbound.equals(((BoundRevision) obj).unbound);
        }
        return unbound.equals(obj);
    }

}
