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

package de.mnl.osgi.bnd.repository.maven.idxmvn;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.BoundRevision;
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
import java.util.Arrays;
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
import java.util.function.Function;
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
    private static final String PROP_UNAVAILABLE = "_NOT_AVAILABLE_";

    /** Indexing state. */
    private enum IndexingState {
        NONE, FAILED, EXCL_BY_DEP, INDEXING, INDEXED
    }

    private static final Set<IndexingState> INDEXING_OR_INDEXED
        = new HashSet<>(Arrays.asList(new IndexingState[] {
            IndexingState.INDEXING, IndexingState.INDEXED }));

    private final String groupId;
    private boolean requested;
    private final IndexedMavenRepository indexedRepository;
    private final HttpClient client;
    private final Reporter reporter;
    private final Path groupDir;
    private final Path groupPropsPath;
    private final Path groupIndexPath;
    private final Properties groupProps;
    private final Map<String, String> propQueryCache
        = new ConcurrentHashMap<>();
    private final ConcurrentMap<Revision, IndexingState> indexingState
        = new ConcurrentHashMap<>();
    private boolean indexChanged;
    private ResourcesRepository backupRepo;
    private Writer indexingLog;
    private Map<Revision, List<String>> loggedMessages
        = new ConcurrentHashMap<>();

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Pattern hrefPattern = Pattern.compile(
        "<[aA]\\s+(?:[^>]*?\\s+)?href=(?<quote>[\"'])"
            + "(?<href>[a-zA-Z].*?)\\k<quote>");

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
        this.groupDir = directory;
        this.requested = requested;
        this.indexedRepository = indexedRepository;
        this.client = client;
        this.reporter = reporter;

        // Prepare directory and files
        groupPropsPath = directory.resolve("group.properties");
        groupProps = new Properties();
        // If directory does not exist, create it.
        if (!directory.toFile().exists()) {
            directory.toFile().mkdir();
        } else {
            // Directory exists, check if we have properties.
            if (groupPropsPath.toFile().canRead()) {
                try (InputStream input = Files.newInputStream(groupPropsPath)) {
                    groupProps.load(input);
                }
            }
        }

        // Prepare OSGi repository
        groupIndexPath = groupDir.resolve("index.xml");
        if (groupIndexPath.toFile().canRead()) {
            try (XMLResourceParser parser
                = new XMLResourceParser(groupIndexPath.toFile())) {
                addAll(parser.parse());
            } catch (Exception e) { // NOPMD
                reporter.warning("Cannot parse %s, ignored: %s", groupIndexPath,
                    e.getMessage());
            }
        } else {
            indexChanged = true;
        }
        // Cache revisions for faster checks.
        for (Capability cap : findProvider(
            newRequirementBuilder("bnd.info").build())) {
            indexingState.put(
                Revision.valueOf((String) cap.getAttributes().get("from")),
                IndexingState.INDEXED);
        }
        LOG.debug("Created group repository for {}.", groupId);
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
     * any backup information prepared by {@link #reset(boolean)}.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void flush() throws IOException {
        if (indexChanged && backupRepo != null) {
            // Only a rough guess, verify to avoid unnecessary new versions
            // of the generated file.
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
            indexChanged = false;
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
     * Clears this repository and updates the requested flag. 
     * Keeps the current content as backup for reuse in a 
     * subsequent call to {@link #reload()}.
     *
     * @param requested whether this is a requested group
     */
    public void reset(boolean requested) {
        synchronized (this) {
            // Update "type"
            this.requested = requested;
            // Save current content and clear.
            backupRepo = new ResourcesRepository(getResources());
            set(Collections.emptyList());
            // Clear and reload properties
            groupProps.clear();
            if (groupPropsPath.toFile().canRead()) {
                try (InputStream input = Files.newInputStream(groupPropsPath)) {
                    propQueryCache.clear();
                    groupProps.load(input);
                } catch (IOException e) {
                    reporter.warning("Problem reading %s (ignored): %s",
                        groupPropsPath, e.getMessage());
                }
            }
            // Clear remaining caches.
            indexingState.clear();
            propQueryCache.clear();
        }
    }

    /**
     * Returns the excluded versions of the specified artifact id.
     *
     * @param artifactId the artifact id
     * @return the maven version range
     */
    private MavenVersionRange excludedRange(String artifactId) {
        // Check if excluded by rule.
        return Optional
            .ofNullable(searchProperty(artifactId, "exclude"))
            .map(MavenVersionRange::parseRange)
            .orElse(MavenVersionRange.NONE);
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
            propQueryCache.clear();
        }
        try {
            CompletableFuture<Void> actions
                = CompletableFuture.allOf(findArtifactIds().stream().map(
                    artifactId -> loadProgram(Program.valueOf(groupId,
                        artifactId)))
                    .toArray(CompletableFuture[]::new));
            actions.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        indexChanged = true;
    }

    private CompletableFuture<Void> loadProgram(Program program) {
        // Get revisions of program and process.
        CompletableFuture<Void> result = new CompletableFuture<>();
        IndexedMavenRepository.programLoaders.submit(() -> {
            Stream<CompletableFuture<Void>> revLoaderStream;
            try {
                LOG.debug("Getting list of revisions of {}.", program);
                revLoaderStream = indexedRepository.mavenRepository()
                    .findRevisions(program)
                    .map(revision -> loadRevision(revision));
            } catch (IOException e) {
                reporter.exception(e,
                    "Failed to get list of revisions of %s: %s", program,
                    e.getMessage());
                result.complete(null);
                return;
            }
            // Ignore exceptions from loadRevision, there won't be any.
            revLoaderStream.forEach(
                revLoad -> unthrow(() -> revLoad.handle((res, thrw) -> {
                    return null;
                }).get()));
            result.complete(null);
        });
        return result;
    }

    /**
     * Load the revision asynchronously. Calling 
     * {@link CompletableFuture#get()} never returns an exception. 
     *
     * @param revision the revision
     * @return the completable future
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidDuplicateLiterals",
        "PMD.PositionLiteralsFirstInComparisons" })
    private CompletableFuture<Void> loadRevision(BoundRevision revision) {
        return CompletableFuture.runAsync(() -> {
            MavenVersionRange acceptedRange = Optional.ofNullable(
                searchProperty(revision.artifactId(), "versions"))
                .map(MavenVersionRange::parseRange).orElse(null);
            MavenVersionRange forcedRange = Optional.ofNullable(
                searchProperty(revision.artifactId(), "forcedVersions"))
                .map(MavenVersionRange::parseRange).orElse(null);
            boolean inForced = forcedRange != null
                && forcedRange.includes(revision.version());
            if (acceptedRange != null || forcedRange != null) {
                boolean inVersions = acceptedRange != null
                    && acceptedRange.includes(revision.version());
                if (!inVersions && !inForced) {
                    logIndexing(revision.unbound(),
                        () -> String.format("%s not selected for indexing.",
                            revision));
                    return;
                }
            }
            if (indexingState.getOrDefault(revision.unbound(),
                IndexingState.NONE) != IndexingState.NONE) {
                // Already loading or handled (as dependency)
                return;
            }
            LOG.debug("Loading revision {}.", revision.unbound());
            logIndexing(revision.unbound(),
                () -> String.format("%s in list, indexing...",
                    revision.unbound()));
            try {
                MavenResource resource = indexedRepository.mavenRepository()
                    .resource(revision, BinaryLocation.REMOTE);
                Set<MavenResource> allDeps = new HashSet<>();
                if (!isIndexable(resource, allDeps, inForced)) {
                    return;
                }
                addResource(resource);
                // Add the dependencies found while checking to the index.
                rethrow(IOException.class, () -> allDeps.stream()
                    .filter(res -> runIgnoring(() -> isBundle(res), false))
                    .filter(res -> indexingState.getOrDefault(res.revision(),
                        IndexingState.NONE) == IndexingState.NONE)
                    .forEach(res -> runIgnoring(() -> indexedRepository
                        .getOrCreateGroupRepository(res.revision().group)
                        .addResource(res))));
            } catch (Exception e) {
                // Load "in isolation", don't propagate individual failure.
                reporter.exception(e, "Failed to load revision %s: %s",
                    revision, e.getMessage());
                logIndexing(revision.unbound(), () -> String.format(
                    "%s failed to load.", revision.unbound()));
            }
        }, IndexedMavenRepository.revisionLoaders);
    }

    @SuppressWarnings("PMD.PositionLiteralsFirstInComparisons")
    private boolean isBundle(MavenResource resource)
            throws MavenResourceException {
        return resource.getCapabilities("osgi.identity").stream()
            .map(cap -> cap.getAttributes().keySet().stream())
            .flatMap(Function.identity())
            .anyMatch(key -> key.equals("osgi.identity"));
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

    /**
     * Checks if the given revision is indexable, considering its 
     * dependencies. A revision is indexable if its dependencies aren't
     * excluded.
     *
     * @param resource the resource to check
     * @param dependencies revisions that must additionally be included
     *    when the checked revision is added
     * @param ignoreExcludedDependencies evaluate as indexable even
     *    if some dependencies aren't indexable
     * @return true, if the revision can be added to the index
     */
    @SuppressWarnings({ "PMD.LongVariable", "PMD.NPathComplexity",
        "PMD.AvoidCatchingGenericException" })
    private boolean isIndexable(MavenResource resource,
            Set<MavenResource> dependencies,
            boolean ignoreExcludedDependencies) {
        Revision revision = resource.revision();
        if (!revision.group.equals(groupId)) {
            throw new IllegalArgumentException("Wrong groupId "
                + revision.group + " (must be " + groupId + ").");
        }
        // Check if already there (or being added)
        if (INDEXING_OR_INDEXED.contains(indexingState
            .getOrDefault(revision, IndexingState.NONE))) {
            // Obviously indexable and no additional resources needed.
            return true;
        }
        // Check if excluded by rule.
        if (Optional.ofNullable(
            searchProperty(revision.artifact, "exclude"))
            .map(MavenVersionRange::parseRange)
            .map(range -> range.includes(MavenVersion.from(revision.version)))
            .orElse(false)) {
            logIndexing(revision,
                () -> String.format("%s is excluded by rule.", revision));
            return false;
        }
        // Check if has been found un-indexable before
        if (!ignoreExcludedDependencies
            && indexingState.getOrDefault(revision,
                IndexingState.NONE) == IndexingState.EXCL_BY_DEP) {
            return false;
        }
        // Check whether excluded because dependency is excluded
        List<Dependency> deps;
        try {
            deps = resource.dependencies();
        } catch (Exception e) {
            // Failing to get the dependencies is no reason to fail.
            return true;
        }
        if (!deps.isEmpty()) {
            logIndexing(revision,
                () -> String.format("%s has dependencies: %s", revision,
                    deps.stream()
                        .map(d -> d.getGroupId() + ":" + d.getArtifactId() + ":"
                            + d.getVersion())
                        .collect(Collectors.joining(", "))));
        }
        for (Dependency dep : deps) {
            MavenGroupRepository depRepo;
            try {
                depRepo = indexedRepository
                    .getOrCreateGroupRepository(dep.getGroupId());
            } catch (Exception e) {
                continue;
            }
            Optional<MavenResource> depRes;
            try {
                depRes = indexedRepository.mavenRepository().resource(
                    Program.valueOf(dep.getGroupId(), dep.getArtifactId()),
                    MavenVersionSpecification.from(dep.getVersion()),
                    BinaryLocation.REMOTE);
            } catch (Exception e) {
                // Failing to get a dependency is no reason to fail.
                continue;
            }
            if (!depRes.isPresent()) {
                // Failing to get a dependency is no reason to fail.
                continue;
            }
            depRepo.logIndexing(depRes.get().revision(),
                () -> String.format("%s is dependency of %s.",
                    depRes.get().revision(), revision));
            if (!depRepo.isIndexable(depRes.get(), dependencies,
                ignoreExcludedDependencies)) {
                // Note that the revision which was checked is not indexable
                // due to a dependency that is not indexable
                indexingState.put(revision, IndexingState.EXCL_BY_DEP);
                if (!ignoreExcludedDependencies) {
                    logIndexing(revision,
                        () -> String.format("%s not indexable, depends on %s.",
                            revision, depRes.get().revision()));
                    return false;
                }
            }
            dependencies.add(depRes.get());
        }
        return true;
    }

    /**
     * Adds the specified revision.
     *
     * @param revision the revision to add
     */
    private void addResource(MavenResource resource) {
        synchronized (indexingState) {
            if (indexingState.getOrDefault(resource.revision(),
                IndexingState.NONE) == IndexingState.INDEXED) {
                return;
            }
            try {
                add(resource.asResource());
            } catch (MavenResourceException e) {
                reporter.exception(e, "Failed to get %s as resource.",
                    resource.toString());
            }
            indexingState.put(resource.revision(), IndexingState.INDEXED);
            logIndexing(resource.revision(),
                () -> String.format("%s indexed.", resource.revision()));
        }
        indexChanged = true;
    }

    /* package */ Optional<Resource> searchInBackup(Revision revision) {
        if (backupRepo == null) {
            return Optional.empty();
        }
        return backupRepo.findProvider(
            backupRepo.newRequirementBuilder("bnd.info")
                .addDirective("filter",
                    String.format("(from=%s)", revision.toString()))
                .build())
            .stream().findFirst().map(Capability::getResource);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private String searchProperty(String artifactId, String qualifier) {
        String queryKey = artifactId + ";" + qualifier;
        // Attempt to get from cache.
        String prop = propQueryCache.get(queryKey);
        // Special value, because ConcurrentHashMap cannot have null values.
        if (prop == PROP_UNAVAILABLE) {
            return null;
        }
        // Found in cache return.
        if (prop != null) {
            return prop;
        }
        // Try query key unmodified
        prop = groupProps.getProperty(queryKey);
        if (prop == null) {
            // Try <id>.*, successively removing trailing parts.
            String rest = artifactId;
            while (true) {
                prop = groupProps.getProperty(rest + ".*;" + qualifier);
                if (prop != null) {
                    break;
                }
                int lastDot = rest.lastIndexOf('.');
                if (lastDot < 0) {
                    break;
                }
                rest = rest.substring(0, lastDot);
            }
        }
        if (prop == null) {
            prop = groupProps.getProperty(qualifier);
        }
        propQueryCache.put(queryKey, prop == null ? PROP_UNAVAILABLE : prop);
        return prop;
    }

    private void logIndexing(Revision revision, Supplier<String> msgSupplier) {
        loggedMessages.computeIfAbsent(revision, rev -> new ArrayList<>())
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
