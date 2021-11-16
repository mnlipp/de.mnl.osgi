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

import aQute.maven.api.Archive;
import aQute.maven.provider.MavenBackingRepository;
import static de.mnl.osgi.bnd.maven.MavenVersion.toBndMavenVersion;

/**
 * An {@link Archive} with a reference to the maven repository
 * in which it was found.
 * 
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
        super(revision.unbound(), toBndMavenVersion(snapshot), extension,
            classifier);
        this.revision = revision;
    }

    /**
     * Instantiates a new bound archive.
     *
     * @param revision the revision
     * @param snapshot the snapshot
     * @param extension the extension
     * @param classifier the classifier
     */
    public BoundArchive(BoundRevision revision,
            aQute.bnd.version.MavenVersion snapshot, String extension,
            String classifier) {
        super(revision.unbound(), snapshot, extension, classifier);
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
