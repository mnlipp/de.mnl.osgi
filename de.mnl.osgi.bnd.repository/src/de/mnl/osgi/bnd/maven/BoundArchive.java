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
import aQute.maven.provider.MavenBackingRepository;

/**
 * An {@link Archive} with a reference to the maven repository
 * in which it was found.
 * <P>
 * @see BoundRevision 
 */
public class BoundArchive extends Archive {

    private final BoundRevision revision;

    /**
     * Instantiates a new bound archive.
     *
     * @param revision the revision
     * @param snapshot the snapshot
     * @param extension the extension
     * @param classifier the classifier
     */
    public BoundArchive(BoundRevision revision, MavenVersion snapshot,
            String extension, String classifier) {
        super(revision.revision(), snapshot, extension, classifier);
        this.revision = revision;
    }

    /**
     * Instantiates a new bound archive.
     *
     * @param mavenRepository the maven repository
     * @param str the string representation
     */
    public BoundArchive(MavenBackingRepository mavenRepository, String str) {
        super(str);
        this.revision = new BoundRevision(mavenRepository, super.revision);
    }

    /**
     * Create a {@link BoundArchive} from an (unbound) {@link Archive}.
     *
     * @param mavenRepository the maven repository
     * @param archive the archive
     * @return the bound archive
     */
    public static BoundArchive fromArchive(
            MavenBackingRepository mavenRepository, Archive archive) {
        return new BoundArchive(
            new BoundRevision(mavenRepository, archive.revision),
            archive.snapshot, archive.extension, archive.classifier);
    }

    /**
     * Gets the maven backing repository.
     *
     * @return the mavenBackingRepository
     */
    public final MavenBackingRepository mavenBackingRepository() {
        return revision.mavenBackingRepository();
    }

    /**
     * Gets the bound revision.
     *
     * @return the bound revision
     */
    public BoundRevision getBoundRevision() {
        return revision;
    }

}
