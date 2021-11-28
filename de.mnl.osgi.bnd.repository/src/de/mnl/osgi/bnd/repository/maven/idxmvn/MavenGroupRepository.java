/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019-2021 Michael N. Lipp
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

package de.mnl.osgi.bnd.repository.maven.idxmvn;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.maven.api.Archive;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.BoundArchive;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository.BinaryLocation;
import de.mnl.osgi.bnd.maven.MavenResource;
import de.mnl.osgi.bnd.maven.MavenResourceException;
import de.mnl.osgi.bnd.maven.MavenVersion;
import de.mnl.osgi.bnd.maven.MavenVersionRange;
import de.mnl.osgi.bnd.maven.MavenVersionSpecification;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.rethrow;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.runIgnoring;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.unthrow;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.model.Dependency;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A repository with artifacts from a single group.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.TooManyFields",
    "PMD.ExcessiveImports" })
public class MavenGroupRepository extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        MavenGroupRepository.class);

    /** Indexing state. */
    private enum IndexingState {
        NONE, INDEXING, INDEXED, EXCLUDED, EXCL_BY_DEP
    }

    private final String groupId;
    private boolean requested;
    private final IndexedMavenRepository indexedRepository;
    private final HttpClient client;
    private final Reporter reporter;
    private Path groupDir;
    private Path groupPropsPath;
    private Path groupIndexPath;
    private final Properties groupProps;
    private VersionSpecification[] versionSpecs = new VersionSpecification[0];
    private final ConcurrentMap<Archive, IndexingState> indexingState
        = new ConcurrentHashMap<>();
    private ResourcesRepository backupRepo;
    private Writer indexingLog;
    private Map<Revision, List<String>> loggedMessages
        = new ConcurrentHashMap<>();

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Pattern hrefPattern = Pattern.compile(
        "<[aA]\\s+(?:[^>]*?\\s+)?href=(?<quote>[\"'])"
            + ":?(?<href>[a-zA-Z].*?)\\k<quote>");

    /**
     * Instantiates a new representation of group data backed
     * by the specified directory. 
     *
     * @param groupId the maven groupId indexed by this repository
     * @param directory the directory used to persist data
     * @param requested if it is a requested group id
     * @param indexedRepository the indexed maven repository
     * @param client the client used for remote access
     * @param reporter the reporter
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.ConfusingTernary",
        "PMD.AvoidCatchingGenericException", "PMD.AvoidDuplicateLiterals" })
    public MavenGroupRepository(String groupId, Path directory,
            boolean requested, IndexedMavenRepository indexedRepository,
            HttpClient client, Reporter reporter) throws IOException {
        this.groupId = groupId;
        this.requested = requested;
        this.indexedRepository = indexedRepository;
        this.client = client;
        this.reporter = reporter;

        // Prepare directory and files
        updatePaths(directory);
        groupProps = new Properties();
        if (groupPropsPath.toFile().canRead()) {
            try (InputStream input = Files.newInputStream(groupPropsPath)) {
                groupProps.load(input);
                versionSpecs = VersionSpecification.parse(groupProps);
            }
        }

        // Prepare OSGi repository
        if (groupIndexPath.toFile().canRead()) {
            try (XMLResourceParser parser
                = new XMLResourceParser(groupIndexPath.toFile())) {
                addAll(parser.parse());
            } catch (Exception e) { // NOPMD
                reporter.warning("Cannot parse %s, ignored: %s", groupIndexPath,
                    e.getMessage());
            }
        }

        // Re-use exiting resources for faster checks.
        for (Capability cap : findProvider(
            newRequirementBuilder("bnd.info").build())) {
            indexingState.put(
                Archive.valueOf((String) cap.getAttributes().get("from")),
                IndexingState.INDEXED);
        }
        LOG.debug("Created group repository for {}.", groupId);
    }

    private void updatePaths(Path directory) {
        if (!directory.toFile().exists()) {
            directory.toFile().mkdir();
        }
        groupDir = directory;
        groupPropsPath = groupDir.resolve("group.properties");
        groupIndexPath = groupDir.resolve("index.xml");
    }

    private IndexingState indexingState(Archive archive) {
        return indexingState.getOrDefault(archive, IndexingState.NONE);
    }

    /**
     * Checks if is requested.
     *
     * @return the requested
     */
    public final boolean isRequested() {
        return requested;
    }

    /**
     * Writes all changes to persistent storage and removes
     * any backup information prepared by {@link #reset(Path, boolean)}.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void flush() throws IOException {
        boolean indexChanged = true;
        if (backupRepo != null) {
            Set<Resource> oldSet = new HashSet<>(backupRepo.getResources());
            Set<Resource> newSet = new HashSet<>(getResources());
            if (newSet.equals(oldSet)) {
                indexChanged = false;
            }
        }
        if (indexChanged) {
            XMLResourceGenerator generator = new XMLResourceGenerator();
            generator.resources(getResources());
            generator.name(indexedRepository.mavenRepository().name());
            try {
                generator.save(groupIndexPath.toFile());
            } catch (IOException e) {
                reporter.exception(e, "Cannot save %s.", groupIndexPath);
            }
        }
        backupRepo = null;
        if (indexingLog != null) {
            rethrow(IOException.class, () -> loggedMessages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream())
                .forEach(msg -> unthrow(() -> indexingLog
                    .write(msg + System.lineSeparator()))));
            indexingLog.close();
            indexingLog = null;
        }
        loggedMessages.clear();
        indexingState.clear();
    }

    /**
     * Removes the group directory if empty.
     *
     * @return true, if removed
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public boolean removeIfRedundant() throws IOException {
        if (isRequested() || !groupProps.isEmpty()
            || !getResources().isEmpty()) {
            return false;
        }
        // Nothing in this group
        Files.walk(groupDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
        return true;
    }

    /**
     * Returns the group id.
     *
     * @return the groupId
     */
    @SuppressWarnings("PMD.ShortMethodName")
    public final String id() {
        return groupId;
    }

    /**
     * Clears this repository and updates the path and the requested 
     * flag. Keeps the current content as backup for reuse in a 
     * subsequent call to {@link #reload()}.
     *
     * @param directory this group's directory
     * @param requested whether this is a requested group
     */
    public void reset(Path directory, boolean requested) {
        synchronized (this) {
            // Update basic properties
            this.requested = requested;
            if (!groupDir.equals(directory)) {
                updatePaths(directory);
                backupRepo = null;
            } else {
                // Save current content and clear.
                backupRepo = new ResourcesRepository(getResources());
            }
            set(Collections.emptyList());
            // Clear and reload properties
            groupProps.clear();
            if (groupPropsPath.toFile().canRead()) {
                try (InputStream input = Files.newInputStream(groupPropsPath)) {
                    groupProps.load(input);
                    versionSpecs = VersionSpecification.parse(groupProps);
                } catch (IOException e) {
                    reporter.warning("Problem reading %s (ignored): %s",
                        groupPropsPath, e.getMessage());
                }
            }
            // Clear remaining caches.
            indexingState.clear();
        }
    }

    /**
     * Reload the repository. Requested repositories retrieve
     * the list of known artifactIds from the remote repository 
     * and add the versions. For versions already in the repository, 
     * the backup information is re-used.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.AvoidReassigningLoopVariables",
        "PMD.AvoidCatchingGenericException", "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.AvoidThrowingRawExceptionTypes", "PMD.PreserveStackTrace" })
    public void reload() throws IOException {
        if (indexedRepository.logIndexing()) {
            indexingLog = Files.newBufferedWriter(
                groupDir.resolve("indexing.log"), Charset.defaultCharset());
        } else {
            // Don't keep out-dated log files, it's irritating.
            groupDir.resolve("indexing.log").toFile().delete();
        }
        loggedMessages.clear();
        if (!isRequested()) {
            // Will be filled with dependencies only
            return;
        }
        // Actively filled.
        synchronized (this) {
            if (backupRepo == null) {
                backupRepo = new ResourcesRepository(getResources());
            }
        }
        try {
            CompletableFuture<?>[] artifactLoaders = findArtifactIds()
                .stream().map(
                    artifactId -> loadProgram(
                        Program.valueOf(groupId, artifactId)))
                .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(artifactLoaders).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Void> loadProgram(Program program) {
        // Get revisions of program and process.
        CompletableFuture<Void> result = new CompletableFuture<>();
        IndexedMavenRepository.programLoaders.submit(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName("RevisionQuerier " + program);
                Stream<CompletableFuture<Void>> revLoaderStream
                    = indexedRepository.mavenRepository().findRevisions(program)
                        .flatMap(revision -> {
                            var archives = VersionSpecification
                                .toSelected(versionSpecs, revision);
                            if (archives.isEmpty()) {
                                logIndexing(revision.unbound(),
                                    () -> String.format(
                                        "%s not selected for indexing.",
                                        revision.unbound()));
                            }
                            return archives.stream();
                        }).map(archive -> loadArchive(archive));

                // Complete all completables
                revLoaderStream.forEach(
                    revLoad -> unthrow(() -> revLoad.handle((res, thrw) -> {
                        return null;
                    }).get()));
            } catch (Throwable t) {
                reporter.exception(t,
                    "Failed to get list of revisions of %s: %s", program,
                    t.getMessage());
            } finally {
                Thread.currentThread().setName(threadName);
                result.complete(null);
            }
        });
        return result;
    }

    /**
     * Load the archive asynchronously (if selected). Calling 
     * {@link CompletableFuture#get()} never returns an exception. 
     *
     * @param archive the revision
     * @return the completable future
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidDuplicateLiterals",
        "PMD.PositionLiteralsFirstInComparisons" })
    private CompletableFuture<Void> loadArchive(BoundArchive archive) {
        return CompletableFuture.runAsync(() -> {
            IndexingState curState = indexingState.putIfAbsent(
                archive, IndexingState.INDEXING);
            if (curState != null && curState != IndexingState.INDEXING) {
                // Already handled (as dependency)
                logIndexing(archive.revision, () -> String.format(
                    "%s in list (already handled as dependency).", archive));
                return;
            }
            LOG.debug("Loading archive {}.", archive);
            logIndexing(archive.revision,
                () -> String.format("%s in list, indexing...", archive));
            String threadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName("ArchiveLoader " + archive);
                MavenResource resource = indexedRepository.mavenRepository()
                    .resource(archive, BinaryLocation.REMOTE);
                Set<MavenResource> allDeps = new HashSet<>();
                if (!isIndexable(resource, allDeps)) {
                    return;
                }
                addResourceAndDependencies(resource, allDeps);
            } catch (Exception e) {
                // Load "in isolation", don't propagate individual failure.
                reporter.exception(e, "Failed to load revision %s: %s",
                    archive, e.getMessage());
                logIndexing(archive.revision, () -> String.format(
                    "%s failed to load: %s.", archive, e.getMessage()));
            } finally {
                Thread.currentThread().setName(threadName);
            }
        }, IndexedMavenRepository.revisionLoaders);
    }

    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    private Collection<String> findArtifactIds() {
        Set<String> result = new HashSet<>();
        for (MavenBackingRepository repo : indexedRepository.mavenRepository()
            .backing()) {
            URI groupUri = null;
            try {
                groupUri
                    = repo.toURI("").resolve(groupId.replace('.', '/') + "/");
                String page = client.build().headers("User-Agent", "Bnd")
                    .get(String.class)
                    .go(groupUri);
                if (page == null) {
                    continue;
                }
                Matcher matcher = hrefPattern.matcher(page);
                while (matcher.find()) {
                    URI programUri = groupUri.resolve(matcher.group("href"));
                    String artifactId = programUri.getPath()
                        .substring(groupUri.getPath().length());
                    if (artifactId.endsWith("/")) {
                        artifactId
                            = artifactId.substring(0, artifactId.length() - 1);
                    }
                    result.add(artifactId);
                }
            } catch (Exception e) {
                reporter.warning("Problem retrieving %s, skipped: %s", groupUri,
                    e.getMessage());
            }
        }
        return result;
    }

    private void addResourceAndDependencies(MavenResource resource,
            Set<MavenResource> allDeps) {
        addResource(resource);
        // Add the dependencies found while checking to the index.
        try {
            rethrow(IOException.class, () -> allDeps.stream()
                .forEach(res -> runIgnoring(() -> indexedRepository
                    .getOrCreateGroupRepository(
                        res.archive().getRevision().group)
                    .addResource(res))));
        } catch (Exception e) {
            // Load "in isolation", don't propagate individual failure.
            reporter.exception(e, "Failed to add resource %s: %s",
                resource.archive(), e.getMessage());
            logIndexing(resource.archive().revision, () -> String.format(
                "%s failed to load.", resource.archive()));
        }
    }

    /**
     * Checks if the given resource is indexable, considering its 
     * dependencies. A resource is indexable if its dependencies aren't
     * excluded.
     *
     * @param resource the resource to check
     * @param dependencies revisions that must additionally be included
     *    when the checked revision is added
     * @return true, if the revision can be added to the index
     */
    @SuppressWarnings({ "PMD.LongVariable", "PMD.NPathComplexity",
        "PMD.AvoidCatchingGenericException" })
    private boolean isIndexable(MavenResource resource,
            Set<MavenResource> dependencies) {
        Archive archive = resource.archive();
        if (!archive.revision.group.equals(groupId)) {
            throw new IllegalArgumentException("Wrong groupId "
                + archive.revision.group + " (must be " + groupId + ").");
        }
        // Check if already decided.
        switch (indexingState(archive)) {
        case INDEXED:
            return true;
        case EXCLUDED:
        case EXCL_BY_DEP:
            return false;
        default:
            break;
        }
        // Check if excluded by rule.
        if (VersionSpecification
            .excluded(versionSpecs, archive.revision.artifact)
            .includes(MavenVersion.from(archive.revision.version))) {
            if (indexingState.replace(archive, IndexingState.INDEXING,
                IndexingState.EXCLUDED)) {
                logIndexing(archive.revision,
                    () -> String.format("%s is excluded by rule.", archive));
            }
            return false;
        }
        boolean isForced = VersionSpecification.isForced(versionSpecs, archive);

        // Check whether excluded because dependency is excluded
        List<Dependency> deps = evaluateDependencies(resource);
        for (Dependency dep : deps) {
            MavenGroupRepository depRepo;
            try {
                depRepo = indexedRepository
                    .getOrCreateGroupRepository(dep.getGroupId());
            } catch (Exception e) {
                // Failing to get a dependency is no reason to fail.
                continue;
            }
            Optional<MavenResource> optRes = depRepo.dependencyToResource(dep);
            if (!optRes.isPresent()) {
                // Failing to get a dependency is no reason to fail.
                continue;
            }
            MavenResource depResource = optRes.get();
            // Watch out to use the proper repository in the code following!
            depRepo.logIndexing(depResource.archive().revision,
                () -> String.format("%s is dependency of %s, indexing...",
                    depResource.archive(), archive));
            depRepo.indexingState.putIfAbsent(depResource.archive(),
                IndexingState.INDEXING);
            Set<MavenResource> collectedDeps = new HashSet<>();
            if (!depRepo.isIndexable(depResource, collectedDeps)) {
                // Note that the revision which was checked is not indexable
                // due to a dependency that is not indexable (unless forced)
                if (!isForced) {
                    if (indexingState.replace(archive, IndexingState.INDEXING,
                        IndexingState.EXCL_BY_DEP)) {
                        logIndexing(archive.revision, () -> String.format(
                            "%s not indexable, depends on %s.",
                            archive, depResource.archive()));
                    }
                    return false;
                }
            }
            if (!VersionSpecification.toSelected(depRepo.versionSpecs,
                depResource.archive().revision).isEmpty()) {
                // Has just been indexed as dependency, but would have
                // been indexed (as selected) anyway, so add independent
                // of outcome of dependency evaluation.
                depRepo.addResourceAndDependencies(depResource, collectedDeps);
            } else {
                dependencies.add(depResource);
                dependencies.addAll(collectedDeps);
            }
        }
        return true;
    }

    private List<Dependency> evaluateDependencies(MavenResource resource) {
        List<Dependency> deps;
        try {
            deps = resource.dependencies();
        } catch (Exception e) {
            // Failing to get the dependencies is no reason to fail.
            return Collections.emptyList();
        }
        if (!deps.isEmpty()) {
            logIndexing(resource.archive().revision,
                () -> String.format("%s has dependencies: %s",
                    resource.archive(), deps.stream()
                        .map(d -> d.getGroupId() + ":" + d.getArtifactId() + ":"
                            + d.getVersion())
                        .collect(Collectors.joining(", "))));
        }
        return deps;
    }

    private Optional<MavenResource> dependencyToResource(Dependency dep) {
        Program depPgm = Program.valueOf(dep.getGroupId(), dep.getArtifactId());
        try {
            return indexedRepository.mavenRepository().resource(
                depPgm, narrowVersion(depPgm, MavenVersionSpecification
                    .from(dep.getVersion())),
                dep.getType(), dep.getClassifier(),
                BinaryLocation.REMOTE);
        } catch (Exception e) {
            // Failing to get a dependency is no reason to fail.
            return Optional.empty();
        }
    }

    private MavenVersionSpecification narrowVersion(
            Program program, MavenVersionSpecification version)
            throws IOException {
        if (version instanceof MavenVersion) {
            // Specific version, leave as is.
            return version;
        }
        // If it's a range, restrict it to allowed
        MavenVersionRange excluded = VersionSpecification
            .excluded(versionSpecs, program.artifact);
        return excluded.complement().restrict((MavenVersionRange) version);
    }

    /**
     * Adds the specified revision.
     *
     * @param revision the revision to add
     */
    private void addResource(MavenResource resource) {
        synchronized (indexingState) {
            if (indexingState(resource.archive()) == IndexingState.INDEXED) {
                return;
            }
            try {
                add(resource.asResource());
                logIndexing(resource.archive().revision,
                    () -> String.format("%s indexed.", resource.archive()));
            } catch (MavenResourceException e) {
                reporter.exception(e, "Failed to get %s as resource.",
                    resource.toString());
                logIndexing(resource.archive().revision,
                    () -> String.format("%s could not be index: %s.",
                        resource.archive(), e.getMessage()));
            }
            indexingState.put(resource.archive(), IndexingState.INDEXED);
        }
    }

    /* package */ Optional<Resource> searchInBackup(Archive archive) {
        if (backupRepo == null) {
            return Optional.empty();
        }
        return backupRepo.findProvider(
            backupRepo.newRequirementBuilder("bnd.info")
                .addDirective("filter",
                    String.format("(from=%s)", archive.toString()))
                .build())
            .stream().findFirst().map(Capability::getResource);
    }

    private void logIndexing(Revision revision, Supplier<String> msgSupplier) {
        loggedMessages
            .computeIfAbsent(revision,
                rev -> Collections.synchronizedList(new ArrayList<>()))
            .add(msgSupplier.get());
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "MavenGroupRepository [groupId=" + groupId + "]";
    }
}
