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
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
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
    private Function<Revision, Optional<Resource>> resourceSupplier
        = resource -> Optional.empty();
    private final Map<Program, List<BoundRevision>> programCache
        = new ConcurrentHashMap<>();
    private final Map<Revision, MavenResource> resourceCache
        = new ConcurrentHashMap<>();

    /**
     * Use local or remote URL in index.
     */
    public enum BinaryLocation {
        LOCAL, REMOTE
    }

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
     * Sets a function that can provide resource information more
     * efficiently (e.g. from some local persistent cache) than
     * the remote maven repository.
     * <P>
     * Any resource information provided by the function must be
     * complete, i.e. must hold the information from the "bnd.info"
     * namespace and from the "maven.dependencies.info" namespace.
     *
     * @param resourceSupplier the resource supplier
     * @return the composite maven repository
     */
    public CompositeMavenRepository setResourceSupplier(
            Function<Revision, Optional<Resource>> resourceSupplier) {
        this.resourceSupplier = resourceSupplier;
        return this;
    }

    /**
     * Reset any cached information.
     */
    public void reset() {
        programCache.clear();
        resourceCache.clear();
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
     * Get the bound revisions of the given program.
     *
     * @param program the program
     * @return the list
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Stream<BoundRevision> boundRevisions(Program program)
            throws IOException {
        return rethrow(IOException.class,
            () -> programCache.computeIfAbsent(program,
                prg -> unthrow(() -> repositoriesAsStream())
                    .flatMap(mbr -> unthrow(() -> boundRevisions(mbr, program)))
                    .collect(Collectors.toList()))).stream();
    }

    /**
     * Converts a {@link Revision} to a {@link BoundRevision}.
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
     * Gets the dependencies of the specified archive.
     *
     * @param archive the archive
     * @return the dependencies
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstanceofChecksInCatchClause" })
    public Set<Dependency> getDependencies(BoundArchive archive) {
        Set<Dependency> dependencies = new HashSet<>();
        try {
            IPom pom = getPom(archive.getRevision());
            if (pom != null) {
                Collection<IPom.Dependency> cpDeps = pom
                    .getDependencies(MavenScope.compile, false).values();
                Collection<IPom.Dependency> rtDeps = pom.getDependencies(
                    MavenScope.runtime, false).values();
                dependencies.addAll(cpDeps);
                dependencies.addAll(rtDeps);
            }
        } catch (Exception e) {
            if (e instanceof InvocationTargetException
                && ((InvocationTargetException) e)
                    .getTargetException() instanceof IOException) {
                reporter.exception(((InvocationTargetException) e)
                    .getTargetException(), "Problem accessing POM of %s: %s",
                    archive, ((InvocationTargetException) e)
                        .getTargetException().getMessage());
                return null;
            }
            reporter.exception(e, "Problem processing POM of %s: %s", archive,
                e.getMessage());
        }
        return dependencies;
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
            MavenVersion version = MavenVersion.from(revision
                .mavenBackingRepository().getVersion(revision.unbound()));
            return revision.archive(version, extension, classifier);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    /**
     * Retrieves the dependency information from the provided
     * resource. Assumes that the resource was created by this
     * repository, i.e. with capabilities in the
     * "maven.dependencies.info" name space.
     *
     * @param resource the resource
     * @param dependencies the dependencies
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static void retrieveDependencies(Resource resource,
            Collection<Dependency> dependencies) {
        // Actually, there should be only one such capability per resource.
        for (Capability capability : resource
            .getCapabilities(
                CompositeMavenRepository.MAVEN_DEPENDENCIES_NS)) {
            Map<String, Object> depAttrs = capability.getAttributes();
            depAttrs.values().stream()
                .flatMap(val -> RepositoryUtils.itemizeList((String) val))
                .map(rev -> {
                    String[] parts = rev.split(":");
                    IPom.Dependency dep = new IPom.Dependency();
                    dep.program = Program.valueOf(parts[0], parts[1]);
                    dep.version = parts[2];
                    return dep;
                }).forEach(dependencies::add);
        }
    }

    /**
     * Creates a {@link MavenResource} for the given revision. 
     *
     * @param revision the revision
     * @param location which URL to use for the binary in the {@link Resource}
     * @return the resource
     */
    public MavenResource toResource(Revision revision,
            BinaryLocation location) {
        return resourceCache.computeIfAbsent(revision,
            rev -> resourceSupplier.apply(rev)
                .map(resource -> new MavenResourceImpl(revision, resource))
                .orElseGet(() -> new MavenResourceImpl(revision, location)));
    }

    /**
     * Creates a {@link MavenResource} for the given revision. 
     *
     * @param revision the revision
     * @param location which URL to use for the binary in the {@link Resource}
     * @return the resource
     */
    public MavenResource toResource(
            BoundRevision revision, BinaryLocation location) {
        return resourceCache.computeIfAbsent(revision.unbound(),
            rev -> resourceSupplier.apply(rev)
                .map(resource -> new MavenResourceImpl(revision, resource))
                .orElseGet(() -> new MavenResourceImpl(revision, location)));
    }

    /**
     * Creates a {@link MavenResource} for the given program and version. 
     *
     * @param program the program
     * @param version the version
     * @param location which URL to use for the binary in the {@link Resource}
     * @return the resource
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Optional<MavenResource> toResource(Program program, String version,
            BinaryLocation location) throws IOException {
        return toBoundRevision(program, version)
            .map(revision -> toResource(revision, location));
    }

    /**
     * A maven resource that obtains its information
     * lazily from a {@link CompositeMavenRepository}.
     */
    public class MavenResourceImpl implements MavenResource {

        private final Revision revision;
        private BoundRevision cachedRevision;
        private BoundArchive cachedArchive;
        private Resource cachedDelegee;
        private Set<IPom.Dependency> cachedDependencies;
        private final BinaryLocation location;

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(Revision revision, BinaryLocation location) {
            this.revision = revision;
            this.location = location;
        }

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(BoundRevision revision,
                BinaryLocation location) {
            this.revision = revision.unbound();
            this.cachedRevision = revision;
            this.location = location;
        }

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(Revision revision, Resource resource) {
            this.revision = revision;
            this.cachedDelegee = resource;
            // Doesn't matter, resource won't be created (already there).
            this.location = BinaryLocation.REMOTE;
        }

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(BoundRevision revision, Resource resource) {
            this.revision = revision.unbound();
            this.cachedRevision = revision;
            this.cachedDelegee = resource;
            // Doesn't matter, resource won't be created (already there).
            this.location = BinaryLocation.REMOTE;
        }

        @Override
        public Revision revision() {
            return revision;
        }

        @Override
        public BoundRevision boundRevision() throws IOException {
            if (cachedRevision == null) {
                cachedRevision = toBoundRevision(revision).get();
            }
            return cachedRevision;
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private BoundArchive archive() throws IOException {
            if (cachedArchive == null) {
                cachedArchive = getResolvedArchive(revision, "jar", "");
                if (cachedArchive == null) {
                    throw new FileNotFoundException(
                        "Problem resolving archive for " + revision + ".");
                }
                if (cachedArchive.isSnapshot()) {
                    refreshSnapshot(cachedArchive);
                }
            }
            return cachedArchive;
        }

        @Override
        public Resource asResource() throws IOException {
            if (cachedDelegee == null) {
                createResource();
            }
            return cachedDelegee;
        }

        /**
         * Creates a {@link Resource} representation from the manifest
         * of the artifact.  
         */
        @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
            "PMD.AvoidInstanceofChecksInCatchClause",
            "PMD.CyclomaticComplexity", "PMD.NcssCount" })
        private void createResource() throws IOException {
            BoundArchive archive = archive();
            // Trying to clean up the mess caused by the underlying methods
            // all throwing Exception.
            File binary;
            try {
                binary = get(archive).getValue();
            } catch (Exception e) {
                if (e instanceof InvocationTargetException
                    && ((InvocationTargetException) e)
                        .getTargetException() instanceof IOException) {
                    // Should be the only possible exception here
                    throw (IOException) ((InvocationTargetException) e)
                        .getTargetException();
                }
                throw new UndeclaredThrowableException(e);
            }
            ResourceBuilder builder = new ResourceBuilder();
            try {
                if (location == BinaryLocation.LOCAL) {
                    builder.addFile(binary, binary.toURI());
                } else {
                    builder.addFile(binary,
                        cachedArchive.mavenBackingRepository()
                            .toURI(archive().remotePath));
                }
            } catch (Exception e) {
                // That's what the exceptions thrown here come down to.
                throw new IllegalArgumentException(e);
            }
            addInformationCapability(builder, archive().toString(),
                archive().getRevision().toString(), null);
            // Add dependency infos
            if (!dependencies().isEmpty()) {
                CapabilityBuilder cap
                    = new CapabilityBuilder(MAVEN_DEPENDENCIES_NS);
                Set<Dependency> cpDeps = new HashSet<>();
                Set<Dependency> rtDeps = new HashSet<>();
                for (Dependency dep : dependencies()) {
                    if (dep.scope == MavenScope.compile) {
                        cpDeps.add(dep);
                    } else if (dep.scope == MavenScope.runtime) {
                        rtDeps.add(dep);
                    }
                }
                try {
                    if (!cpDeps.isEmpty()) {
                        cap.addAttribute("compile", toVersionList(cpDeps));
                    }
                    if (!rtDeps.isEmpty()) {
                        cap.addAttribute("runtime", toVersionList(rtDeps));
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
                builder.addCapability(cap);
            }
            cachedDelegee = builder.build();
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

        @Override
        public List<Capability> getCapabilities(String namespace)
                throws IOException {
            return asResource().getCapabilities(namespace);
        }

        @Override
        public List<Requirement> getRequirements(String namespace)
                throws IOException {
            return asResource().getRequirements(namespace);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof MavenResource) {
                return revision.equals(((MavenResource) obj).revision());
            }
            if (obj instanceof Resource) {
                try {
                    return asResource().equals(obj);
                } catch (IOException e) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return revision.hashCode();
        }

        @Override
        public String toString() {
            return revision.toString();
        }

        @Override
        @SuppressWarnings("PMD.ConfusingTernary")
        public final Set<IPom.Dependency> dependencies() throws IOException {
            if (cachedDependencies == null) {
                if (cachedDelegee != null) {
                    cachedDependencies = new HashSet<>();
                    retrieveDependencies(cachedDelegee, cachedDependencies);
                } else {
                    cachedDependencies = CompositeMavenRepository.this
                        .getDependencies(archive());
                }
            }
            return cachedDependencies;
        }

    }
}
