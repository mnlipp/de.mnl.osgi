/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019-2022 Michael N. Lipp
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

import aQute.bnd.deployer.repository.RepoResourceUtils;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.version.Version;
import aQute.maven.api.Archive;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository.BinaryLocation;
import de.mnl.osgi.bnd.maven.MavenResource;
import de.mnl.osgi.bnd.maven.MavenResourceException;
import de.mnl.osgi.bnd.maven.MavenVersion;
import de.mnl.osgi.bnd.maven.MavenVersionRange;
import de.mnl.osgi.bnd.maven.MavenVersionSpecification;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.rethrow;
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
import org.apache.maven.model.Dependency;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A repository with artifacts from a single group.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.TooManyFields",
    "PMD.ExcessiveImports", "PMD.GodClass", "PMD.TooManyMethods",
    "PMD.CyclomaticComplexity" })
public class MavenGroupRepository extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        MavenGroupRepository.class);

    /** Indexing state. */
    private enum IndexingState {
        NONE, CHECKING, INDEXED, EXCLUDED, EXCL_BY_DEP
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
    private final Map<Revision, List<String>> loggedMessages
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
        "PMD.AvoidCatchingGenericException", "PMD.AvoidDuplicateLiterals",
        "PMD.GuardLogStatement" })
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
    @SuppressWarnings({ "PMD.ConfusingTernary", "PMD.GuardLogStatement" })
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
     * Reset the repository group. Must be called for all groups before
     * reloading. 
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    /* package */ void reset() throws IOException {
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
    }

    /**
     * Reload the repository. May be called concurrently for different
     * group repositories after resetting all. Requested repositories 
     * retrieve the list of known artifactIds from the remote repository 
     * and add the versions. For versions already in the repository, 
     * the backup information is re-used.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.AvoidReassigningLoopVariables",
        "PMD.AvoidCatchingGenericException", "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.AvoidThrowingRawExceptionTypes", "PMD.PreserveStackTrace" })
    /* package */ void reload() throws IOException {
        if (!isRequested()) {
            // Will be filled with dependencies only
            return;
        }
        try {
            CompletableFuture<?>[] programLoaders = findArtifactIds().stream()
                .map(artifactId -> loadProgram(
                    Program.valueOf(groupId, artifactId)))
                .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(programLoaders).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException(e.getCause());
        } catch (InterruptedException e) {
            reporter.exception(e, "Loading %s has been interrupted: %s",
                groupId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.GuardLogStatement" })
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

    @SuppressWarnings({ "PMD.AvoidCatchingThrowable", "PMD.CognitiveComplexity",
        "PMD.NPathComplexity", "PMD.NcssCount" })
    private CompletableFuture<Void> loadProgram(Program program) {
        // Get revisions of program and process.
        CompletableFuture<Void> result = new CompletableFuture<>();
        IndexedMavenRepository.programLoaders.submit(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName("RevisionQuerier " + program);
                var resources = listRevisions(program);
                if (resources.isEmpty()) {
                    return;
                }
                removeOutOfOrderVersions(resources);

                // Now start indexing for remaining
                for (var resource : resources) {
                    var archive = resource.archive();
                    // Indexing may have been started for this as dependency
                    // but as we don't know yet if that will be successful,
                    // we start it nevertheless (concurrently).
                    if (Optional.ofNullable(indexingState.putIfAbsent(
                        resource.archive(), IndexingState.CHECKING))
                        .orElse(
                            IndexingState.CHECKING) != IndexingState.CHECKING) {
                        logIndexing(resource, () -> String.format(
                            "%s from revision list already handled as dependency.",
                            resource));
                        continue;
                    }
                    logIndexing(resource, () -> String.format(
                        "%s in revision list, indexing...", resource));
                    var deps = indexableDependencies(resource, true);
                    if (deps == null) {
                        if (indexingState.replace(archive,
                            IndexingState.CHECKING,
                            IndexingState.EXCL_BY_DEP)) {
                            logIndexing(archive, () -> String.format(
                                "%s skipped due to unavailable "
                                    + "dependencies.",
                                archive));
                        }
                        continue;
                    }
                    addResourceAndDependencies(resource, deps);
                }
            } finally {
                Thread.currentThread().setName(threadName);
                result.complete(null);
            }
        });
        return result;
    }

    private List<MavenResource> listRevisions(Program program) {
        return indexedRepository.mavenRepository().findRevisions(program)
            .flatMap(revision -> {
                var boundArchives
                    = VersionSpecification.toSelected(versionSpecs, revision);
                if (boundArchives.isEmpty()) {
                    logIndexing(revision.unbound(),
                        () -> String.format("%s not selected for indexing.",
                            revision.unbound()));
                }
                return boundArchives.stream();
            }).map(boundArchive -> {
                LOG.debug("Loading archive {}.", boundArchive);
                return indexedRepository.mavenRepository()
                    .resource(boundArchive, BinaryLocation.REMOTE);
            }).sorted(new Comparator<>() {
                @Override
                public int compare(MavenResource res1, MavenResource res2) {
                    // Sort descending
                    return res2.archive().compareTo(res1.archive());
                }
            }).collect(Collectors.toList());
    }

    private void removeOutOfOrderVersions(List<MavenResource> resources) {
        // Remove resources with versions that are inconsistent
        // with OSGi version order.
        var resourcesIter = resources.iterator();
        Version lastVersion = null;
        while (resourcesIter.hasNext()) {
            var next = resourcesIter.next();
            var nextVersion = osgiVersion(next);
            if (nextVersion.isEmpty()) {
                continue;
            }
            if (lastVersion != null
                && nextVersion.get().compareTo(lastVersion) >= 0) {
                resourcesIter.remove();
                logIndexing(next, () -> String.format(
                    "%s skipped, violates OSGi version order.", next));
                continue;
            }
            lastVersion = nextVersion.get();
        }
    }

    /**
     * Checks if the resource matches the selection criteria.
     *
     * @param resource the resource to check
     * @return true, if the revision matches
     */
    @SuppressWarnings("PMD.CollapsibleIfStatements")
    private boolean indexingCandidate(MavenResource resource) {
        Archive archive = resource.archive();
        if (!archive.revision.group.equals(groupId)) {
            throw new IllegalArgumentException("Wrong groupId "
                + archive.revision.group + " (must be " + groupId + ").");
        }
        // Check if forced.
        if (VersionSpecification.isForced(versionSpecs, archive)) {
            return true;
        }
        // Check if excluded by rule.
        if (VersionSpecification
            .excluded(versionSpecs, archive.revision.artifact)
            .includes(MavenVersion.from(archive.revision.version))) {
            if (indexingState.replace(archive, IndexingState.CHECKING,
                IndexingState.EXCLUDED)) {
                logIndexing(archive.revision,
                    () -> String.format("%s is excluded by rule.", archive));
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void addResourceAndDependencies(MavenResource resource,
            Set<MavenResource> allDeps) {
        addResource(resource);
        // Add the dependencies found while checking to the index.
        for (var depRes : allDeps) {
            try {
                indexedRepository.getOrCreateGroupRepository(
                    depRes.archive().getRevision().group).addResource(depRes);
            } catch (IOException e) {
                // No reason to fail completely.
                reporter.exception(e, "Failed to add dependency %s of %s: %s",
                    depRes, resource, e.getMessage());
                logIndexing(resource, () -> String.format(
                    "%s failed to add depedendency %s.", resource, depRes));
            }
        }
    }

    private Optional<Version> osgiVersion(MavenResource resource) {
        try {
            if (ResourceUtils.getIdentityCapability(
                resource.asResource()) == null) {
                return Optional.empty();
            }
            return Optional
                .ofNullable(ResourceUtils.getVersion(resource.asResource()));
        } catch (IllegalArgumentException | MavenResourceException e) {
            reporter.exception(e, "Failed to get as resource %s: %s",
                resource.archive(), e.getMessage());
            logIndexing(resource,
                () -> String.format("%s failed to load.", resource));
            return Optional.empty();
        }
    }

    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.ReturnEmptyCollectionRatherThanNull" })
    private Set<MavenResource> indexableDependencies(MavenResource resource,
            boolean log) {
        // Get dependencies and check them
        List<Dependency> dependencies = evaluateDependencies(resource);
        if (!dependencies.isEmpty() && log) {
            logIndexing(resource,
                () -> String.format("%s has dependencies: %s",
                    resource, dependencies.stream()
                        .map(d -> d.getGroupId() + ":" + d.getArtifactId() + ":"
                            + d.getVersion())
                        .collect(Collectors.joining(", "))));
        }
        Set<MavenResource> indexable = new HashSet<>();
        boolean isForced = VersionSpecification.isForced(versionSpecs,
            resource.archive());
        for (Dependency dep : dependencies) {
            MavenGroupRepository depRepo;
            try {
                depRepo = indexedRepository
                    .getOrCreateGroupRepository(dep.getGroupId());
            } catch (Exception e) {
                reporter.exception(e, "Failed to get repo %s: %s",
                    dep.getGroupId(), e.getMessage());
                // Failing to get a dependency is no reason to fail.
                continue;
            }
            var depsDeps = depRepo.collectTransient(resource, dep, isForced);
            if (depsDeps == null) {
                if (log) {
                    logIndexing(resource, () -> String.format(
                        "%s lacks dependency: %s", resource,
                        dep.getGroupId() + ":" + dep.getArtifactId() + ":"
                            + dep.getVersion()));
                }
                return null;
            }
            indexable.addAll(depsDeps);
        }
        return indexable;
    }

    @SuppressWarnings({ "PMD.CollapsibleIfStatements",
        "PMD.ReturnEmptyCollectionRatherThanNull", "PMD.NcssCount",
        "PMD.CognitiveComplexity" })
    private Set<MavenResource> collectTransient(MavenResource dependant,
            Dependency dependency, boolean dontFail) {
        Set<MavenResource> collected = new HashSet<>();
        Optional<MavenResource> optRes = dependencyToResource(dependency);
        if (!optRes.isPresent()) {
            // Failing to get the resource is no reason to fail.
            return collected;
        }
        MavenResource resource = optRes.get();
        IndexingState state = Optional.ofNullable(indexingState
            .putIfAbsent(resource.archive(), IndexingState.CHECKING))
            .orElse(IndexingState.NONE);
        switch (state) {
        case INDEXED:
            // Dependency (and its dependencies() have already been indexed.
            return collected;
        case EXCLUDED:
            if (dontFail) {
                return collected;
            }
            logIndexing(resource, () -> String.format(
                "%s is excluded, thus blocks %s.", resource,
                dependant));
            return null;
        case EXCL_BY_DEP:
            // Indexing of dependency has already failed.
            if (dontFail) {
                return collected;
            }
            logIndexing(resource, () -> String.format(
                "%s lacks dependencies, thus blocks %s.", resource,
                dependant));
            return null;
        case NONE:
            // Only the first attempt reports.
            logIndexing(resource, () -> String.format(
                "%s is checked as dependency of %s...", resource, dependant));
            break;
        default:
            break;
        }

        // Attempt to index.
        Set<MavenResource> transDeps = null;
        boolean candidate = indexingCandidate(resource);
        if (candidate || dontFail) {
            transDeps = indexableDependencies(resource,
                state == IndexingState.NONE);
        }
        if (transDeps == null) {
            // Note that the revision which was checked is not indexable
            // due to a dependency that is not indexable (unless forced)
            if (!dontFail) {
                if (indexingState.replace(resource.archive(),
                    IndexingState.CHECKING, IndexingState.EXCL_BY_DEP)) {
                    logIndexing(resource, () -> String.format(
                        "%s lacks dependencies, thus blocks %s.", resource,
                        dependant));
                }
                return null;
            }
        } else {
            collected.addAll(transDeps);
        }
        collected.add(resource);
        return collected;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private List<Dependency> evaluateDependencies(MavenResource resource) {
        List<Dependency> deps;
        try {
            deps = resource.dependencies();
        } catch (Exception e) {
            reporter.exception(e, "Failed to get dependency of %s: %s",
                resource, e.getMessage());
            logIndexing(resource, () -> String.format(
                "Failed to get dependencies of %s: %s", resource,
                e.getMessage()));
            // Failing to get the dependencies is no reason to fail.
            return Collections.emptyList();
        }
        return deps;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Optional<MavenResource> dependencyToResource(Dependency dep) {
        Program depPgm = Program.valueOf(dep.getGroupId(), dep.getArtifactId());
        try {
            return indexedRepository.mavenRepository().resource(
                depPgm, narrowVersion(depPgm, MavenVersionSpecification
                    .from(dep.getVersion())),
                dep.getType(), dep.getClassifier(),
                BinaryLocation.REMOTE);
        } catch (Exception e) {
            reporter.exception(e, "Failed to get resource %s: %s",
                depPgm, e.getMessage());
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
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void addResource(MavenResource resource) {
        if (!indexingState.replace(resource.archive(),
            IndexingState.CHECKING, IndexingState.INDEXED)) {
            return;
        }
        try {
            // The ResourcesRepoitory that we inherit from isn't thread safe
            synchronized (this) {
                add(resource.asResource());
            }
            logIndexing(resource,
                () -> String.format("%s added to index.", resource));
        } catch (Exception e) {
            reporter.exception(e, "Failed to get %s as resource.", resource);
            logIndexing(resource, () -> String.format(
                "%s could not be indexed: %s.", resource, e.getMessage()));
        }
    }

    /* package */ Optional<Resource> searchInBackup(Archive archive) {
        if (backupRepo == null) {
            return Optional.empty();
        }
        var optResource = backupRepo.findProvider(
            backupRepo.newRequirementBuilder("bnd.info")
                .addDirective("filter",
                    String.format("(from=%s)", archive.toString()))
                .build())
            .stream().findFirst().map(Capability::getResource);
        if (optResource.isEmpty()) {
            return optResource;
        }

        // Restore supporting resources, see
        // https://github.com/bndtools/bnd/issues/6211 and
        // https://github.com/bndtools/bnd/issues/6212
        ResourceBuilder builder = new ResourceBuilder();
        builder.addResource(optResource.get());
        var ident = RepoResourceUtils.getIdentityCapability(optResource.get());
        var name
            = ident.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE);
        var version = ident.getAttributes()
            .get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        Set<String> known = new HashSet<>();
        backupRepo.findProvider(
            backupRepo.newRequirementBuilder("bnd.multirelease")
                .addDirective("filter",
                    String.format("(&(bnd.multirelease=%s)(version=%s))", name,
                        version.toString()))
                .build())
            .stream().map(Capability::getResource)
            .filter(r -> {
                var forRel = RepoResourceUtils.getIdentityCapability(r)
                    .getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE)
                    .toString();
                if (known.contains(forRel)) {
                    return false;
                }
                known.add(forRel);
                return true;
            })
            .forEach(builder::addSupportingResource);

        return Optional.of(builder.build());
    }

    private void logIndexing(Revision revision, Supplier<String> msgSupplier) {
        loggedMessages
            .computeIfAbsent(revision,
                rev -> Collections.synchronizedList(new ArrayList<>()))
            .add(msgSupplier.get());
    }

    private void logIndexing(Archive archive, Supplier<String> msgSupplier) {
        logIndexing(archive.revision, msgSupplier);
    }

    private void logIndexing(MavenResource resource,
            Supplier<String> msgSupplier) {
        logIndexing(resource.archive(), msgSupplier);
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
