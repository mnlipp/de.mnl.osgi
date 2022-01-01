/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019-2021  Michael N. Lipp
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

import aQute.maven.api.Archive;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;

/**
 * An {@link Archive} with a reference to the maven repository
 * in which it was found.
 * 
 * @see BoundRevision 
 */
public class BoundArchive extends Archive {

    private final MavenBackingRepository mavenBackingRepository;

    /**
     * Instantiates a new bound archive.
     *
     * @param mavenBackingRepository the maven backing repository
     * @param unbound the unbound archive
     */
    public BoundArchive(MavenBackingRepository mavenBackingRepository,
            Archive unbound) {
        super(unbound.revision, unbound.snapshot, unbound.extension,
            unbound.classifier);
        this.mavenBackingRepository = mavenBackingRepository;
    }

    /**
     * Instantiates a new bound archive.
     *
     * @param mavenBackingRepository the maven backing repository
     * @param revision the revision
     * @param snapshot the snapshot
     * @param extension the extension
     * @param classifier the classifier
     */
    public BoundArchive(MavenBackingRepository mavenBackingRepository,
            Revision revision, MavenVersion snapshot, String extension,
            String classifier) {
        super(revision, snapshot == null ? null : snapshot.asBndMavenVersion(),
            extension, classifier);
        this.mavenBackingRepository = mavenBackingRepository;
    }

    /**
     * Gets the maven backing repository.
     *
     * @return the mavenBackingRepository
     */
    public MavenBackingRepository mavenBackingRepository() {
        return mavenBackingRepository;
    }

    /**
     * Return the archive's revision, bound to its backing repository.
     *
     * @return the bound revision
     */
    public BoundRevision revision() {
        return new BoundRevision(mavenBackingRepository, revision);
    }

}
