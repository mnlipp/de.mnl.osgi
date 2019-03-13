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

import static aQute.bnd.osgi.repository.BridgeRepository.addInformationCapability;

import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.maven.api.Archive;
import aQute.maven.api.IPom;
import aQute.maven.api.MavenScope;
import aQute.maven.provider.MavenRepository;
import aQute.maven.provider.MetadataParser;
import aQute.maven.provider.MetadataParser.RevisionMetadata;
import de.mnl.osgi.bnd.repository.maven.provider.MavenGroupRepository;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.osgi.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a processor for adding a maven resource to
 * an OSGi repository.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class RevisionIndexer {

    /**
     * Use local or remote URL in index.
     */
    public enum IndexedResource {
        LOCAL, REMOTE
    }

    private static final Logger LOG = LoggerFactory.getLogger(
        MavenGroupRepository.class);

    private final MavenRepository mavenRepository;
    private final ResourcesRepository osgiRepository;
    private final IndexedResource indexedResource;

    /**
     * Instantiates a new revision indexer.
     *
     * @param mavenRepository the maven repository
     * @param osgiRepository the osgi repository
     * @param indexedResource the indexed resource
     */
    public RevisionIndexer(MavenRepository mavenRepository,
            ResourcesRepository osgiRepository,
            IndexedResource indexedResource) {
        this.mavenRepository = mavenRepository;
        this.osgiRepository = osgiRepository;
        this.indexedResource = indexedResource;
    }

    /**
     * Indexes the given revisions and adds the results to
     * the OSGi repository. The dependencies of the indexed
     * revisions are collected in {@code dependencies}.  
     *
     * @param revisions the revisions
     * @param dependencyHandler the dependency handler
     */
    public void indexRevisions(List<BoundRevision> revisions,
            Consumer<Collection<IPom.Dependency>> dependencyHandler) {
        for (BoundRevision revision : revisions) {
            indexRevision(revision, dependencyHandler);
        }
    }

    /**
     * Indexes the given revision and adds the results to
     * the OSGi repository. The dependencies of the indexed
     * revisions are collected in {@code dependencies}.  
     *
     * @param revision the revision
     * @param dependencyHandler the dependency handler
     */

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void indexRevision(BoundRevision revision,
            Consumer<Collection<IPom.Dependency>> dependencyHandler) {
        // Get and add this maven revision's OSGi information
        Archive archive;
        try {
            archive = mavenRepository.getResolvedArchive(revision.revision(),
                "jar", "");
            if (archive == null) {
                return;
            }
        } catch (Exception e) {
            LOG.error("Problem resolving archive for {}.", revision, e);
            return;
        }
        if (archive.isSnapshot()) {
            refreshSnapshot(archive, revision);
        }
        // Get POM for dependencies
        try {
            IPom pom = mavenRepository.getPom(archive.getRevision());
            if (pom != null) {
                Collection<IPom.Dependency> deps = pom
                    .getDependencies(MavenScope.compile, false).values();
                if (!deps.isEmpty()) {
                    dependencyHandler.accept(deps);
                }
                deps = pom.getDependencies(MavenScope.runtime, false)
                    .values();
                if (!deps.isEmpty()) {
                    dependencyHandler.accept(deps);
                }
            }
        } catch (Exception e) {
            LOG.error("Problem processing POM of {}.", revision, e);
        }
        Resource resource = parseResource(revision, archive);
        if (resource != null) {
            synchronized (osgiRepository) {
                osgiRepository.add(resource);
            }
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void refreshSnapshot(Archive archive, BoundRevision revision) {
        File metaFile = mavenRepository.toLocalFile(
            revision.metadata(revision.mavenBackingRepository().getId()));
        RevisionMetadata metaData;
        try {
            metaData = MetadataParser.parseRevisionMetadata(metaFile);
        } catch (Exception e) {
            LOG.error("Problem accessing {}.", revision, e);
            return;
        }
        File archiveFile = mavenRepository.toLocalFile(archive);
        if (archiveFile.lastModified() < metaData.lastUpdated) {
            archiveFile.delete();
        }
        File pomFile = mavenRepository.toLocalFile(archive.getPomArchive());
        if (pomFile.lastModified() < metaData.lastUpdated) {
            pomFile.delete();
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Resource parseResource(BoundRevision revision, Archive archive) {
        ResourceBuilder builder = new ResourceBuilder();
        try {
            File binary = mavenRepository.get(archive).getValue();
            if (indexedResource == IndexedResource.LOCAL) {
                builder.addFile(binary, binary.toURI());
            } else {
                builder.addFile(binary, revision.mavenBackingRepository()
                    .toURI(archive.remotePath));
            }
            addInformationCapability(builder, archive.toString(),
                archive.getRevision().toString(), null);
        } catch (Exception e) {
            LOG.error("Problem accessing {}.", revision, e);
            return null;
        }
        return builder.build();
    }

}
