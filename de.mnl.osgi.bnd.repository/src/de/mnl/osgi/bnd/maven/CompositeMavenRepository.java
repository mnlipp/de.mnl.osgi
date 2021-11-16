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

import aQute.bnd.service.RepositoryPlugin;
import aQute.maven.api.Archive;
import aQute.maven.api.IMavenRepo;
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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.model.superpom.DefaultSuperPomProvider;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.osgi.util.promise.Promise;

/**
 * Provides a composite {@link IMavenRepo} view on several 
 * {@link MavenBackingRepository} instances.
 * The class replaces {@link MavenRepository} which lacks some
 * required functionality. (Besides, this class has a more
 * appropriate name.)
 * <P>
 * The information about artifacts is provided as a maven
 * {@link Model}. It is evaluated using the maven libraries and
 * should therefore be consistent with the model information
 * used in other maven repository based tools.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "deprecation" })
public class CompositeMavenRepository implements Closeable {

    public static final Pattern COORDS_SPLITTER = Pattern.compile("\\s*;\\s*");
    private final MavenRepository bndMavenRepo;
    private final Reporter reporter;
    private final Map<Program, List<BoundRevision>> programCache
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
        // number of libraries. To get an idea why, read
        // https://lairdnelson.wordpress.com/2017/03/06/maven-and-the-project-formerly-known-as-aether/
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
        builder.setProfileInjector(new DefaultProfileInjector());
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
     * Reset any cached information.
     */
    public void reset() {
        programCache.clear();
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
     * @throws IOException Signals that an I/O exception has occurred.
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
     * @throws IOException Signals that an I/O exception has occurred.
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
            throw new RuntimeException(
                "Cannot evaluate revisions for " + program + " from " + mbr, e);
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
     * @throws MavenResourceException the maven resource exception
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
    protected void refreshSnapshot(BoundArchive archive) {
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

}
