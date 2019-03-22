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

import aQute.bnd.osgi.resource.CapabilityBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.IPom;
import aQute.maven.api.IPom.Dependency;
import aQute.maven.api.MavenScope;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.maven.provider.MetadataParser;
import aQute.maven.provider.MetadataParser.RevisionMetadata;
import aQute.service.reporter.Reporter;

import static de.mnl.osgi.bnd.maven.RepositoryUtils.rethrow;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.unthrow;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.osgi.resource.Resource;

/**
 * Provides a composite {@link IMavenRepo} view on several 
 * {@link MavenBackingRepository} instances.
 * <P>
 * The class extends {@link MavenRepository} which lacks some
 * required functionality. Besides, this class has a more
 * appropriate name.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CompositeMavenRepository extends MavenRepository
        implements IMavenRepo, Closeable {

    /** The namespace used to store the maven dependencies information. */
    public static final String MAVEN_DEPENDENCIES_NS
        = "maven.dependencies.info";
    private final Reporter reporter;

    /**
     * Instantiates a new composite maven repository.
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
        this.reporter = reporter;
    }

    /**
     * Returns all backing repositories.
     *
     * @return the list of all repositories
     */
    public List<MavenBackingRepository> allRepositories() {
        List<MavenBackingRepository> result
            = new ArrayList<>(getReleaseRepositories());
        result.addAll(getSnapshotRepositories());
        return result;
    }

    /**
     * Returns all repositories as a stream.
     *
     * @return the repositories as stream
     */
    public Stream<MavenBackingRepository> repositoriesAsStream() {
        return Stream.concat(getReleaseRepositories().stream(),
            getSnapshotRepositories().stream()).distinct();
    }

    /**
     * Wrapper for {@link MavenBackingRepository#getRevisions(Program, List)}
     * that returns the result instead of accumulating it and maps the
     * (too general) exception.
     *
     * @param mbr the backing repository
     * @param program the program
     * @return the revisions
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.LinguisticNaming", "PMD.AvoidRethrowingException",
        "PMD.AvoidCatchingGenericException",
        "PMD.AvoidThrowingRawExceptionTypes", "PMD.AvoidDuplicateLiterals" })
    private static List<Revision> getRevisions(MavenBackingRepository mbr,
            Program program) throws IOException {
        List<Revision> result = new ArrayList<>();
        try {
            mbr.getRevisions(program, result);
        } catch (IOException e) {
            // Should be the only reason.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * Wrapper for {@link MavenBackingRepository#getRevisions(Program, List)}
     * that returns the result instead of accumulating it, maps the
     * (too general) exception and converts the {@link Revision}s to
     * {@link BoundRevision}s.
     *
     * @param mbr the mbr
     * @param program the program
     * @return the stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private static Stream<BoundRevision> boundRevisions(
            MavenBackingRepository mbr, Program program) throws IOException {
        return getRevisions(mbr, program).stream()
            .map(revision -> new BoundRevision(mbr, revision));
    }

    /**
     * Returns all known revisions of the specified program 
     * as {@link BoundRevision}s.
     *
     * @param program the program
     * @return the bound revisions
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.ExceptionAsFlowControl")
    public Stream<BoundRevision> boundRevisions(Program program)
            throws IOException {
        return rethrow(IOException.class, () -> repositoriesAsStream()
            .flatMap(mbr -> unthrow(() -> boundRevisions(mbr, program))));
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
        return boundRevisions(revision.program)
            .filter(rev -> rev.unbound().equals(revision)).findFirst();
    }

    /**
     * Converts a {@link Program} and a version, which
     * may be a range, to a {@link BoundRevision}.
     *
     * @param program the program
     * @param version the version
     * @return the bound revision
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Optional<BoundRevision> toBoundRevision(Program program,
            String version) throws IOException {
        if (!MavenVersionRange.isRange(version)) {
            return toBoundRevision(program.version(version));
        }
        MavenVersionRange range = new MavenVersionRange(version);
        return boundRevisions(program)
            .filter(rev -> range.includes(rev.version()))
            .max(Comparator.naturalOrder());
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
            DependencyHandler dependencyHandler, BinaryLocation location) {
        BoundArchive archive;
        try {
            archive = getResolvedArchive(revision, "jar", "");
            if (archive == null) {
                return Optional.empty();
            }
        } catch (Exception e) {
            reporter.exception(e, "Problem resolving archive for %s.",
                revision);
            return Optional.empty();
        }
        if (archive.isSnapshot()) {
            refreshSnapshot(archive);
        }
        return Optional
            .ofNullable(parseResource(archive, location, dependencyHandler));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * aQute.maven.provider.MavenRepository#getResolvedArchive(aQute.maven.api.
     * Revision, java.lang.String, java.lang.String)
     */
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
            MavenVersion version = MavenVersion.from(revision
                .mavenBackingRepository().getVersion(revision.unbound()));
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void refreshSnapshot(BoundArchive archive) {
        File metaFile = toLocalFile(
            archive.getRevision()
                .metadata(archive.mavenBackingRepository().getId()));
        RevisionMetadata metaData;
        try {
            metaData = MetadataParser.parseRevisionMetadata(metaFile);
        } catch (Exception e) {
            reporter.exception(e, "Problem accessing %s.", archive);
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

    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.UselessParentheses", "PMD.AvoidInstanceofChecksInCatchClause" })
    private Resource parseResource(BoundArchive archive,
            BinaryLocation location, DependencyHandler dependencyHandler) {
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
            // Add dependency infos
            // Get POM for dependencies
            IPom pom = getPom(archive.getRevision());
            if (pom != null) {
                CapabilityBuilder cap = null;
                Collection<IPom.Dependency> rtDeps = pom.getDependencies(
                    MavenScope.runtime, false).values();
                Collection<IPom.Dependency> cpDeps = pom
                    .getDependencies(MavenScope.compile, false).values();
                if (!cpDeps.isEmpty() || !rtDeps.isEmpty()) {
                    cap = new CapabilityBuilder(MAVEN_DEPENDENCIES_NS);
                    if (!cpDeps.isEmpty()) {
                        dependencyHandler.handle(cpDeps);
                        cap.addAttribute("compile", toVersionList(cpDeps));
                    }
                    if (!rtDeps.isEmpty()) {
                        dependencyHandler.handle(rtDeps);
                        cap.addAttribute("runtime", toVersionList(rtDeps));
                    }
                    builder.addCapability(cap);
                }
            }
        } catch (Exception e) {
            if (e instanceof InvocationTargetException
                && ((InvocationTargetException) e)
                    .getTargetException() instanceof FileNotFoundException) {
                reporter.exception(e, "POM of %s not found: %s", archive,
                    ((InvocationTargetException) e).getTargetException()
                        .getMessage());
            }
            reporter.exception(e, "Problem processing POM of %s: %s", archive,
                e.getMessage());
            return null;
        }
        return builder.build();
    }

    private String toVersionList(Collection<IPom.Dependency> deps) {
        StringBuilder depsList = new StringBuilder("");
        for (IPom.Dependency dep : deps) {
            if (depsList.length() > 0) {
                depsList.append(',');
            }
            depsList.append(dep.program.toString());
            depsList.append(':');
            depsList.append(dep.version);
        }
        return depsList.toString();
    }

    /**
     * Use local or remote URL in index.
     */
    public enum BinaryLocation {
        LOCAL, REMOTE
    }

    /**
     * A functional interface used to report the dependency
     * information to a caller.
     */
    @FunctionalInterface
    public interface DependencyHandler {

        /**
         * Handles the dependency information.
         *
         * @param dependencies the dependencies
         */
        void handle(Collection<Dependency> dependencies);
    }
}
