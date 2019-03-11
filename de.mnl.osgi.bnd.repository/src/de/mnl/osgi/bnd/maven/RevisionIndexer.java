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
import aQute.maven.provider.MavenRepository;
import aQute.maven.provider.MetadataParser;
import aQute.maven.provider.MetadataParser.RevisionMetadata;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.osgi.resource.Resource;

public class RevisionIndexer {

    public enum IndexedResource {
        LOCAL, REMOTE
    }

    private MavenRepository mavenRepository;
    private ResourcesRepository osgiRepository;
    private IndexedResource indexedResource;

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
     * @param dependencies the dependencies
     */
    public void indexRevisions(List<ExtRevision> revisions,
            Collection<IPom> dependencies) {
        for (ExtRevision revision : revisions) {
            // Get and add this revision's OSGi information
            Archive archive;
            try {
                archive = mavenRepository
                    .getResolvedArchive(revision.revision(), "jar", "");
                if (archive == null) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            if (archive.isSnapshot()) {
                refreshSnapshot(archive, revision);
            }
            // Get POM for dependencies
            try {
                IPom pom = mavenRepository.getPom(archive.getRevision());
                if (pom != null) {
                    dependencies.add(pom);
                }
            } catch (Exception e) {
                // Ignored
            }
            Resource resource = parseResource(revision, archive);
            if (resource != null) {
                osgiRepository.add(resource);
            }
        }
    }

    private void refreshSnapshot(Archive archive, ExtRevision revision) {
        File metaFile = mavenRepository.toLocalFile(
            revision.metadata(revision.mavenBackingRepository().getId()));
        RevisionMetadata metaData;
        try {
            metaData = MetadataParser.parseRevisionMetadata(metaFile);
        } catch (Exception e) {
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

    private Resource parseResource(ExtRevision revision, Archive archive) {
        ResourceBuilder rb = new ResourceBuilder();
        try {
            File binary = mavenRepository.get(archive).getValue();
            if (indexedResource == IndexedResource.LOCAL) {
                rb.addFile(binary, binary.toURI());
            } else {
                rb.addFile(binary, revision.mavenBackingRepository()
                    .toURI(archive.remotePath));
            }
            addInformationCapability(rb, archive.toString(),
                archive.getRevision().toString(), null);
        } catch (Exception e) {
            return null;
        }
        return rb.build();
    }

}
