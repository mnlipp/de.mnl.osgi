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
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.version.MavenVersion;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.IPom;
import aQute.maven.api.MavenScope;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.maven.provider.MetadataParser;
import aQute.maven.provider.MetadataParser.RevisionMetadata;
import aQute.service.reporter.Reporter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.osgi.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a common {@link IMavenRepo} view on several 
 * {@link MavenBackingRepository} instances.
 * <P>
 * The class extends {@link MavenRepository} which lacks some
 * required functionality.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CompositeMavenRepository extends MavenRepository
        implements IMavenRepo, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(
        CompositeMavenRepository.class);

    /**
     * Instantiates a new merged maven repository.
     *
     * @param base the base
     * @param repoId the repository id
     * @param releaseRepos the backing release repositories
     * @param snapshotRepos the backing snapshot repositories
     * @param executor an executor
     * @param reporter the reporter
     * @throws Exception the exception
     */
    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidDuplicateLiterals" })
    public CompositeMavenRepository(File base, String repoId,
            List<MavenBackingRepository> releaseRepos,
            List<MavenBackingRepository> snapshotRepos, Executor executor,
            Reporter reporter)
            throws Exception {
        super(base, repoId, releaseRepos, snapshotRepos, executor, reporter);
    }

    /**
     * Returns all repositories.
     *
     * @return the list
     */
    public List<MavenBackingRepository> allRepositories() {
        List<MavenBackingRepository> result
            = new ArrayList<>(getReleaseRepositories());
        result.addAll(getSnapshotRepositories());
        return result;
    }

    /**
     * Returns all known revisions as {@link BoundRevision}s.
     *
     * @param program the program
     * @return the bound revisions
     * @throws Exception the exception
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public List<BoundRevision> boundRevisions(Program program)
            throws Exception {
        List<BoundRevision> revisions = new ArrayList<>();

        for (MavenBackingRepository mbr : getReleaseRepositories()) {
            addRevisions(mbr, program, revisions);
        }
        for (MavenBackingRepository mbr : getSnapshotRepositories()) {
            if (!getReleaseRepositories().contains(mbr)) {
                addRevisions(mbr, program, revisions);
            }
        }
        return revisions;
    }

    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    private void addRevisions(MavenBackingRepository mbr, Program program,
            List<BoundRevision> revisions) throws Exception {
        List<Revision> revs = new ArrayList<>();
        mbr.getRevisions(program, revs);
        for (Revision rev : revs) {
            revisions.add(new BoundRevision(mbr, rev));
        }
    }

    /**
     * Converts a {@link Revision} to an {@link BoundRevision}.
     *
     * @param revision the revision
     * @return the bound revision
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public Optional<BoundRevision> toBoundRevision(Revision revision)
            throws IOException {
        for (MavenBackingRepository mbr : getReleaseRepositories()) {
            Optional<BoundRevision> checkResult
                = toBoundRevision(mbr, revision);
            if (checkResult.isPresent()) {
                return checkResult;
            }
        }
        for (MavenBackingRepository mbr : getSnapshotRepositories()) {
            if (!getReleaseRepositories().contains(mbr)) {
                Optional<BoundRevision> checkResult
                    = toBoundRevision(mbr, revision);
                if (checkResult.isPresent()) {
                    return checkResult;
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidRethrowingException", "PMD.AvoidThrowingRawExceptionTypes",
        "PMD.AvoidCatchingGenericException", "PMD.AvoidDuplicateLiterals" })
    private Optional<BoundRevision> toBoundRevision(MavenBackingRepository mbr,
            Revision revision) throws IOException {
        List<Revision> revs = new ArrayList<>();
        try {
            mbr.getRevisions(revision.program, revs);
        } catch (IOException e) {
            // Should be the only reason.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (revs.contains(revision)) {
            return Optional.of(new BoundRevision(mbr, revision));
        }
        return Optional.empty();
    }

    @Override
    public BoundArchive getResolvedArchive(Revision revision, String extension,
            String classifier) throws IOException {
        Optional<BoundRevision> bound = toBoundRevision(revision);
        if (bound.isPresent()) {
            return getResolvedArchive(bound.get(), extension, classifier);
        }
        return null;
    }

    /**
     * Gets the resolved archive. "Resolving" an archive means finding
     * the binaries with the specified extension and classifier belonging
     * to the given version. While this can be done with straight forward
     * name mapping for releases, snapshots have a timestamp that has to
     * be looked up in the backing repository.
     *
     * @param revision the revision
     * @param extension the extension
     * @param classifier the classifier
     * @return the resolved archive
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidThrowingRawExceptionTypes", "PMD.AvoidRethrowingException" })
    public BoundArchive getResolvedArchive(BoundRevision revision,
            String extension, String classifier) throws IOException {
        if (!revision.isSnapshot()) {
            return revision.archive(extension, classifier);
        }
        try {
            MavenVersion version = revision.mavenBackingRepository()
                .getVersion(revision.unbound());
            if (version != null) {
                return revision.archive(version, extension, classifier);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Converts the manifest information of a revision to a
     * {@link Resource}. Any dependencies found in the revisions's
     * POM are reported using the specified {@code dependencyHandler}.  
     *
     * @param revision the revision
     * @param dependencyHandler the dependency handler
     * @param location which URL to use for the binary in the {@link Resource}
     * @return the resource
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Optional<Resource> toResource(BoundRevision revision,
            Consumer<Collection<IPom.Dependency>> dependencyHandler,
            BinaryLocation location) {
        BoundArchive archive;
        try {
            archive = getResolvedArchive(revision, "jar", "");
            if (archive == null) {
                return Optional.empty();
            }
        } catch (Exception e) {
            LOG.error("Problem resolving archive for {}.", revision, e);
            return Optional.empty();
        }
        if (archive.isSnapshot()) {
            refreshSnapshot(archive);
        }
        // Get POM for dependencies
        try {
            IPom pom = getPom(archive.getRevision());
            if (pom != null) {
                Collection<IPom.Dependency> deps = pom
                    .getDependencies(MavenScope.compile, false).values();
                if (!deps.isEmpty()) {
                    dependencyHandler.accept(deps);
                }
                deps = pom.getDependencies(MavenScope.runtime, false).values();
                if (!deps.isEmpty()) {
                    dependencyHandler.accept(deps);
                }
            }
        } catch (Exception e) {
            LOG.error("Problem processing POM of {}.", revision, e);
        }
        return Optional.ofNullable(parseResource(archive, location));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void refreshSnapshot(BoundArchive archive) {
        File metaFile = toLocalFile(
            archive.getRevision()
                .metadata(archive.mavenBackingRepository().getId()));
        RevisionMetadata metaData;
        try {
            metaData = MetadataParser.parseRevisionMetadata(metaFile);
        } catch (Exception e) {
            LOG.error("Problem accessing {}.", archive, e);
            return;
        }
        File archiveFile = toLocalFile(archive);
        if (archiveFile.lastModified() < metaData.lastUpdated) {
            archiveFile.delete();
        }
        File pomFile = toLocalFile(archive.getPomArchive());
        if (pomFile.lastModified() < metaData.lastUpdated) {
            pomFile.delete();
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Resource parseResource(BoundArchive archive,
            BinaryLocation location) {
        ResourceBuilder builder = new ResourceBuilder();
        try {
            File binary = get(archive).getValue();
            if (location == BinaryLocation.LOCAL) {
                builder.addFile(binary, binary.toURI());
            } else {
                builder.addFile(binary, archive.mavenBackingRepository()
                    .toURI(archive.remotePath));
            }
            addInformationCapability(builder, archive.toString(),
                archive.getRevision().toString(), null);
        } catch (Exception e) {
            LOG.error("Problem accessing {}.", archive, e);
            return null;
        }
        return builder.build();
    }

    /**
     * Use local or remote URL in index.
     */
    public enum BinaryLocation {
        LOCAL, REMOTE
    }

}
