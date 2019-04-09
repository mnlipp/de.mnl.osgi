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
import aQute.bnd.service.RepositoryPlugin;
import aQute.maven.api.Archive;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.composition.DefaultDependencyManagementImporter;
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.locator.DefaultModelLocator;
import org.apache.maven.model.management.DefaultDependencyManagementInjector;
import org.apache.maven.model.management.DefaultPluginManagementInjector;
import org.apache.maven.model.normalization.DefaultModelNormalizer;
import org.apache.maven.model.path.DefaultModelPathTranslator;
import org.apache.maven.model.path.DefaultModelUrlNormalizer;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.model.superpom.DefaultSuperPomProvider;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.util.promise.Promise;

/**
 * Provides a composite {@link IMavenRepo} view on several 
 * {@link MavenBackingRepository} instances.
 * <P>
 * The class extends {@link MavenRepository} which lacks some
 * required functionality. Besides, this class has a more
 * appropriate name.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "deprecation" })
public class CompositeMavenRepository implements Closeable {

    /** The namespace used to store the maven dependencies information. */
    public static final String MAVEN_DEPENDENCIES_NS
        = "maven.dependencies.info";
    public static final Pattern COORDS_SPLITTER = Pattern.compile("\\s*;\\s*");
    private final MavenRepository bndMavenRepo;
    private final Reporter reporter;
    private Function<Revision, Optional<Resource>> resourceSupplier
        = resource -> Optional.empty();
    private final Map<Program, List<BoundRevision>> programCache
        = new ConcurrentHashMap<>();
    private final Map<Revision, MavenResource> resourceCache
        = new ConcurrentHashMap<>();
    private final Map<Revision, Model> modelCache
        = new ConcurrentHashMap<>();
    private final BndModelResolver modelResolver;
    private final ModelBuilder modelBuilder;

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
        bndMavenRepo = new MavenRepository(base, repoId, releaseRepos,
            snapshotRepos, executor, reporter);
        this.reporter = reporter;
        modelResolver = new BndModelResolver(bndMavenRepo, reporter);

        // Create the maven model builder. This code is ridiculous,
        // but using maven's CDI pulls in an even more ridiculous
        // number of libraries.
        DefaultModelBuilder builder = new DefaultModelBuilder();
        builder.setProfileSelector(new DefaultProfileSelector());
        DefaultModelProcessor processor = new DefaultModelProcessor();
        processor.setModelLocator(new DefaultModelLocator());
        processor.setModelReader(new DefaultModelReader());
        builder.setModelProcessor(processor);
        builder.setModelValidator(new DefaultModelValidator());
        DefaultSuperPomProvider pomProvider = new DefaultSuperPomProvider();
        pomProvider.setModelProcessor(processor);
        DefaultSuperPomProvider superPomProvider
            = new DefaultSuperPomProvider();
        superPomProvider.setModelProcessor(processor);
        builder.setSuperPomProvider(superPomProvider);
        builder.setModelNormalizer(new DefaultModelNormalizer());
        builder.setInheritanceAssembler(new DefaultInheritanceAssembler());
        StringSearchModelInterpolator modelInterpolator
            = new StringSearchModelInterpolator();
        DefaultPathTranslator pathTranslator = new DefaultPathTranslator();
        modelInterpolator.setPathTranslator(pathTranslator);
        DefaultUrlNormalizer urlNormalizer = new DefaultUrlNormalizer();
        modelInterpolator.setUrlNormalizer(urlNormalizer);
        builder.setModelInterpolator(modelInterpolator);
        DefaultModelUrlNormalizer modelUrlNormalizer
            = new DefaultModelUrlNormalizer();
        modelUrlNormalizer.setUrlNormalizer(urlNormalizer);
        builder.setModelUrlNormalizer(modelUrlNormalizer);
        DefaultModelPathTranslator modelPathTranslator
            = new DefaultModelPathTranslator();
        modelPathTranslator.setPathTranslator(pathTranslator);
        builder.setModelPathTranslator(modelPathTranslator);
        builder
            .setPluginManagementInjector(new DefaultPluginManagementInjector());
        builder.setDependencyManagementInjector(
            new DefaultDependencyManagementInjector());
        builder.setDependencyManagementImporter(
            new DefaultDependencyManagementImporter());
        modelBuilder = builder;
    }

    /**
     * Gets the name of this repository.
     *
     * @return the name
     */
    public String name() {
        return bndMavenRepo.getName();
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
        modelCache.clear();
    }

    @Override
    public void close() throws IOException {
        bndMavenRepo.close();
    }

    /**
    * Returns all backing repositories.
    *
    * @return the list of all repositories
    */
    public List<MavenBackingRepository> backing() {
        List<MavenBackingRepository> result
            = new ArrayList<>(bndMavenRepo.getReleaseRepositories());
        result.addAll(bndMavenRepo.getSnapshotRepositories());
        return result;
    }

    /**
     * Returns all repositories as a stream.
     *
     * @return the repositories as stream
     */
    public Stream<MavenBackingRepository> backingAsStream() {
        return Stream.concat(bndMavenRepo.getReleaseRepositories().stream(),
            bndMavenRepo.getSnapshotRepositories().stream()).distinct();
    }

    /**
     * Retrieves the file from a remote repository into the repositories 
     * local cache directory if it doesn't exist yet.
     * 
     * @param archive The archive to fetch
     * @return the file or null if not found
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstanceofChecksInCatchClause",
        "PMD.AvoidDuplicateLiterals" })
    public Promise<File> retrieve(Archive archive) throws IOException {
        try {
            return bndMavenRepo.get(archive);
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
    }

    /**
     * Get the file object for the archive. The file does not have to exist.
     * The use case for this is to have the {@link File} already while
     * waiting for the {@link Promise} returned by {@link #retrieve(Archive)}
     * to complete.
     * <P>
     * This is required for the implementation of {@link RepositoryPlugin#get}.
     * Besides this use case, it should probably not be used.
     * 
     * @param archive the archive to find the file for
     * @return the File or null if not found
     */
    public File toLocalFile(Archive archive) {
        return bndMavenRepo.toLocalFile(archive);
    }

    /**
     * Gets the file from the local cache directory, retrieving it
     * first if it doesn't exist yet.
     * 
     * @param archive The archive to fetch
     * @return the file or null if not found
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstanceofChecksInCatchClause",
        "PMD.AvoidDuplicateLiterals" })
    public File get(Archive archive) throws IOException {
        try {
            return bndMavenRepo.get(archive).getValue();
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
    private static List<Revision> revisionsFrom(MavenBackingRepository mbr,
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
     * Get the bound revisions of the given program.
     *
     * @param program the program
     * @return the list
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Stream<BoundRevision> findRevisions(Program program)
            throws IOException {
        return rethrow(IOException.class,
            () -> programCache.computeIfAbsent(program,
                prg -> unthrow(() -> backingAsStream())
                    .flatMap(mbr -> unthrow(
                        () -> revisionsFrom(mbr, program).stream()
                            .map(revision -> new BoundRevision(mbr, revision))))
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
    public Optional<BoundRevision> find(Revision revision)
            throws IOException {
        return findRevisions(revision.program)
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
    public Optional<BoundRevision> find(Program program,
            MavenVersionSpecification version) throws IOException {
        if (version instanceof MavenVersion) {
            return find(((MavenVersion) version).of(program));
        }
        MavenVersionRange range = (MavenVersionRange) version;
        return findRevisions(program)
            .filter(rev -> range.includes(rev.version()))
            .max(Comparator.naturalOrder());
    }

    /**
     * Get a model of the specified revision. Dependency versions
     * remain unresolved, i.e. when specified as a range, the range
     * is preserved.
     *
     * @param revision the archive
     * @return the dependencies
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstanceofChecksInCatchClause", "PMD.PreserveStackTrace",
        "PMD.AvoidRethrowingException" })
    public Model model(Revision revision) throws MavenResourceException {
        return rethrow(MavenResourceException.class,
            () -> modelCache.computeIfAbsent(
                revision, key -> unthrow(() -> readModel(key))));
    }

    private Model readModel(Revision revision) throws MavenResourceException {
        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
        try {
            ModelSource modelSource = modelResolver.resolveModel(revision.group,
                revision.artifact, revision.version.toString());
            request.setModelResolver(modelResolver)
                .setModelSource(modelSource)
                .setValidationLevel(
                    ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                .setTwoPhaseBuilding(false);
            ModelBuildingResult result = modelBuilder.build(request);
            return result.getEffectiveModel();
        } catch (UnresolvableModelException | ModelBuildingException e) {
            throw new MavenResourceException(e);
        }
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
    public BoundArchive resolve(BoundRevision revision,
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
        File metaFile = bndMavenRepo.toLocalFile(
            archive.getRevision()
                .metadata(archive.mavenBackingRepository().getId()));
        RevisionMetadata metaData;
        try {
            metaData = MetadataParser.parseRevisionMetadata(metaFile);
        } catch (Exception e) {
            reporter.exception(e, "Problem accessing %s.", archive);
            return;
        }
        File archiveFile = bndMavenRepo.toLocalFile(archive);
        if (archiveFile.lastModified() < metaData.lastUpdated) {
            archiveFile.delete();
        }
        File pomFile = bndMavenRepo.toLocalFile(archive.getPomArchive());
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
    private static void retrieveDependencies(Resource resource,
            Collection<Dependency> dependencies) {
        // Actually, there should be only one such capability per resource.
        for (Capability capability : resource
            .getCapabilities(
                CompositeMavenRepository.MAVEN_DEPENDENCIES_NS)) {
            Map<String, Object> depAttrs = capability.getAttributes();
            depAttrs.values().stream()
                .flatMap(val -> COORDS_SPLITTER.splitAsStream((String) val))
                .map(rev -> {
                    String[] parts = rev.split(":");
                    Dependency dep = new Dependency();
                    dep.setGroupId(parts[0]);
                    dep.setArtifactId(parts[1]);
                    dep.setVersion(parts[2]);
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
    public MavenResource resource(BoundRevision revision,
            BinaryLocation location) {
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
    public Optional<MavenResource> resource(Program program,
            MavenVersionSpecification version, BinaryLocation location)
            throws IOException {
        return find(program, version)
            .map(revision -> resource(revision, location));
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
        private List<Dependency> cachedDependencies;
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
        public BoundRevision boundRevision() throws MavenResourceException {
            try {
                if (cachedRevision == null) {
                    cachedRevision
                        = CompositeMavenRepository.this.find(revision).get();
                }
                return cachedRevision;
            } catch (IOException e) {
                throw new MavenResourceException(e);
            }
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private BoundArchive archive() throws MavenResourceException {
            try {
                if (cachedArchive == null) {
                    Optional<BoundRevision> bound = find(revision);
                    if (!bound.isPresent()) {
                        throw new FileNotFoundException(
                            "Problem resolving archive for " + revision + ".");
                    }
                    cachedArchive = resolve(bound.get(), "jar", "");
                    if (cachedArchive.isSnapshot()) {
                        refreshSnapshot(cachedArchive);
                    }
                }
                return cachedArchive;
            } catch (IOException e) {
                throw new MavenResourceException(e);
            }
        }

        @Override
        public Resource asResource() throws MavenResourceException {
            if (cachedDelegee == null) {
                createResource();
            }
            return cachedDelegee;
        }

        /**
         * Creates a {@link Resource} representation from the manifest
         * of the artifact.  
         *
         * @throws IOException Signals that an I/O exception has occurred.
         * @throws ModelBuildingException the model building exception
         * @throws UnresolvableModelException the unresolvable model exception
         */
        @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
            "PMD.AvoidInstanceofChecksInCatchClause",
            "PMD.CyclomaticComplexity", "PMD.NcssCount" })
        private void createResource() throws MavenResourceException {
            BoundArchive archive = archive();
            File binary;
            try {
                binary = get(archive);
            } catch (IOException e) {
                throw new MavenResourceException(e);
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
                    if (dep.getScope() == null
                        || dep.getScope().equalsIgnoreCase("compile")) {
                        cpDeps.add(dep);
                    } else if (dep.getScope().equalsIgnoreCase("runtime")) {
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

        private String toVersionList(Collection<Dependency> deps) {
            StringBuilder depsList = new StringBuilder("");
            for (Dependency dep : deps) {
                if (depsList.length() > 0) {
                    depsList.append(';');
                }
                depsList.append(dep.getGroupId());
                depsList.append(':');
                depsList.append(dep.getArtifactId());
                depsList.append(':');
                depsList.append(dep.getVersion());
            }
            return depsList.toString();
        }

        @Override
        public List<Capability> getCapabilities(String namespace)
                throws MavenResourceException {
            return asResource().getCapabilities(namespace);
        }

        @Override
        public List<Requirement> getRequirements(String namespace)
                throws MavenResourceException {
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
                } catch (MavenResourceException e) {
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
        public final List<Dependency> dependencies()
                throws MavenResourceException {
            if (cachedDependencies == null) {
                if (cachedDelegee != null) {
                    cachedDependencies = new ArrayList<>();
                    retrieveDependencies(cachedDelegee, cachedDependencies);
                } else {
                    cachedDependencies = CompositeMavenRepository.this
                        .model(revision()).getDependencies().stream()
                        .filter(dep -> !dep.getGroupId().contains("$")
                            && !dep.getArtifactId().contains("$")
                            && !dep.isOptional()
                            && (dep.getScope() == null
                                || dep.getScope().equals("compile")
                                || dep.getScope().equals("runtime")
                                || dep.getScope().equals("provided")))
                        .collect(Collectors.toList());
                }
            }
            return cachedDependencies;
        }

    }
}
