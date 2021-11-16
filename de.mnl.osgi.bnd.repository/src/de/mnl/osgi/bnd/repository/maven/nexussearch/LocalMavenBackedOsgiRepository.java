/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2017,2019  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public 
 * License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.bnd.repository.maven.nexussearch;

import aQute.bnd.osgi.Processor;
import static aQute.bnd.osgi.repository.BridgeRepository.addInformationCapability;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.version.MavenVersionRange;
import aQute.maven.api.Archive;
import aQute.maven.api.IPom;
import aQute.maven.api.IPom.Dependency;
import aQute.maven.api.MavenScope;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.maven.provider.MetadataParser;
import aQute.maven.provider.MetadataParser.RevisionMetadata;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide an OSGi repository (a collection of {@link Resource}s, see 
 * {@link Repository}), filled with the results from recursively resolving
 * an initial set of artifacts.
 * <P>
 * The resources in this repository are maintained in a local 
 * maven repository.
 */
public abstract class LocalMavenBackedOsgiRepository
        extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        LocalMavenBackedOsgiRepository.class);
    private final String name;
    private final File obrIndexFile;
    private final Set<Revision> toBeProcessed = new HashSet<>();
    private final Set<Revision> processing = new HashSet<>();
    private final Set<Revision> processed = new HashSet<>();

    /**
     * Create a new instance that uses the provided information/resources 
     * to perform its work.
     * 
     * @param name the name
     * @param obrIndexFile the persistent representation of this 
     *     repository's content
     * @throws Exception if a problem occurs
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public LocalMavenBackedOsgiRepository(String name, File obrIndexFile)
            throws Exception {
        this.name = name;
        this.obrIndexFile = obrIndexFile;

        if (!location().exists() || !location().isFile()) {
            refresh();
        } else {
            try (XMLResourceParser parser = new XMLResourceParser(location())) {
                List<Resource> resources = parser.parse();
                addAll(resources);
            }
        }
    }

    /**
     * Return the name of this repository.
     * 
     * @return the name;
     */
    public String name() {
        return name;
    }

    /**
     * Return the representation of this repository in the local file system.
     * 
     * @return the location
     */
    public final File location() {
        return obrIndexFile;
    }

    /**
     * Refresh this repository's content.
     * 
     * @return true if refreshed, false if not refreshed possibly due to error
     * @throws Exception if a problem occurs
     */
    public abstract boolean refresh() throws Exception;

    /**
     * Refresh this repository's content.
     *
     * @param mavenRepository the maven repository
     * @param startArtifacts the collection of artifacts to start with
     * @return true if refreshed, false if not refreshed possibly due to error
     * @throws Exception if a problem occurs
     */
    public boolean refresh(MavenRepository mavenRepository,
            Collection<? extends Revision> startArtifacts) throws Exception {
        set(new HashSet<>()); // Clears this repository
        toBeProcessed.addAll(startArtifacts);
        // Repository information is obtained from both querying the
        // repositories
        // (provides information about existing repositories) and from executing
        // the query (provides information about actually used repositories).
        Set<Resource> collectedResources
            = Collections.newSetFromMap(new ConcurrentHashMap<>());
        synchronized (this) {
            while (true) {
                if (toBeProcessed.isEmpty() && processing.isEmpty()) {
                    break;
                }
                if (!toBeProcessed.isEmpty() && processing.size() < 4) {
                    Revision rev = toBeProcessed.iterator().next();
                    toBeProcessed.remove(rev);
                    processing.add(rev);
                    Processor.getScheduledExecutor().submit(
                        new RevisionProcessor(mavenRepository,
                            collectedResources, rev));
                }
                wait();
            }
        }
        processed.clear();
        // Set this repository's content to the results...
        addAll(collectedResources);
        // ... and persist the content.
        XMLResourceGenerator generator = new XMLResourceGenerator();
        generator.resources(getResources());
        generator.name(name());
        generator.save(obrIndexFile);
        return true;
    }

    /**
     * A callable (allows it to throw an exception) that processes a single
     * Revision.
     */
    private class RevisionProcessor implements Callable<Void> {
        private final MavenRepository mavenRepository;
        private final Revision revision;
        private final Set<Resource> collectedResources;

        public RevisionProcessor(MavenRepository mavenRepository,
                Set<Resource> collectedResources, Revision revision) {
            this.mavenRepository = mavenRepository;
            this.collectedResources = collectedResources;
            this.revision = revision;
        }

        /**
         * Get this revision's dependencies from the POM and enqueue them as
         * to be processed (unless processed already) and create an entry
         * for the resource in this repository.
         */
        @Override
        public Void call() throws Exception {
            try {
                // Get and add this revision's OSGi information (refreshes
                // snapshots)
                Archive archive
                    = mavenRepository.getResolvedArchive(revision, "jar", "");
                if (archive != null) {
                    if (archive.isSnapshot()) {
                        for (MavenBackingRepository mbr : mavenRepository
                            .getSnapshotRepositories()) {
                            if (mbr.getVersion(archive.getRevision()) != null) {
                                // Found backing repository
                                File metaFile = mavenRepository.toLocalFile(
                                    revision.metadata(mbr.getId()));
                                RevisionMetadata metaData
                                    = MetadataParser.parseRevisionMetadata(
                                        metaFile);
                                File archiveFile
                                    = mavenRepository.toLocalFile(archive);
                                if (archiveFile
                                    .lastModified() < metaData.lastUpdated) {
                                    archiveFile.delete();
                                }
                                File pomFile = mavenRepository
                                    .toLocalFile(archive.getPomArchive());
                                if (pomFile
                                    .lastModified() < metaData.lastUpdated) {
                                    pomFile.delete();
                                }
                                break;
                            }
                        }
                    }
                    // Get POM for dependencies
                    IPom pom = mavenRepository.getPom(archive.getRevision());
                    if (pom != null) {
                        // Get pom and add all dependencies as to be processed.
                        addDependencies(pom);
                    }
                    Resource resource = parseResource(archive);
                    if (resource != null) {
                        collectedResources.add(resource);
                    }
                }
                return null;
            } finally {
                // We're done witht his revision.
                synchronized (LocalMavenBackedOsgiRepository.this) {
                    processing.remove(revision);
                    processed.add(revision);
                    LocalMavenBackedOsgiRepository.this.notifyAll();
                }
            }
        }

        private void addDependencies(IPom pom) {
            try {
                Map<Program, Dependency> deps = new LinkedHashMap<>();
                deps.putAll(pom.getDependencies(MavenScope.compile, false));
                deps.putAll(pom.getDependencies(MavenScope.runtime, false));
                synchronized (LocalMavenBackedOsgiRepository.this) {
                    for (Map.Entry<Program, Dependency> entry : deps
                        .entrySet()) {
                        bindToVersion(entry.getValue());
                        try {
                            Revision rev = entry.getValue().getRevision();
                            if (!toBeProcessed.contains(rev)
                                && !processing.contains(rev)
                                && !processed.contains(rev)) {
                                toBeProcessed.add(rev);
                                LOG.debug("Added as dependency {}", rev);
                            }
                            LocalMavenBackedOsgiRepository.this.notifyAll();
                        } catch (Exception e) {
                            LOG.warn("Unbindable dependency {}",
                                entry.getValue().toString());
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to get POM of " + revision + ".", e);
            }
        }

        private void bindToVersion(Dependency dependency) throws Exception {
            if (MavenVersionRange.isRange(dependency.version)) {

                MavenVersionRange range
                    = new MavenVersionRange(dependency.version);
                List<Revision> revisions
                    = mavenRepository.getRevisions(dependency.program);

                for (Iterator<Revision> it = revisions.iterator();
                        it.hasNext();) {
                    Revision rev = it.next();
                    if (!range.includes(rev.version)) {
                        it.remove();
                    }
                }

                if (!revisions.isEmpty()) {
                    Collections.sort(revisions, new MavenRevisionComparator());
                    Revision highest = revisions.get(revisions.size() - 1);
                    dependency.version = highest.version.toString();
                }
            }
        }

        private Resource parseResource(Archive archive) throws Exception {
            ResourceBuilder rb = new ResourceBuilder();
            try {
                File binary = mavenRepository.get(archive).getValue();
                rb.addFile(binary, binary.toURI());
                addInformationCapability(rb, archive.toString(),
                    archive.getRevision().toString(), null);
            } catch (Exception e) {
                return null;
            }
            return rb.build();
        }

    }

    public class MavenRevisionComparator implements Comparator<Revision> {
        @Override
        public int compare(Revision rev1, Revision rev2) {
            int res = rev1.program.compareTo(rev2.program);
            if (res != 0) {
                return res;
            }

            ComparableVersion rev1ver
                = new ComparableVersion(rev1.version.toString());
            ComparableVersion rev2ver
                = new ComparableVersion(rev2.version.toString());
            return rev1ver.compareTo(rev2ver);
        }
    }

}
