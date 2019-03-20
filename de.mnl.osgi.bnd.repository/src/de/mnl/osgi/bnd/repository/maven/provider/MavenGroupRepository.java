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

package de.mnl.osgi.bnd.repository.maven.provider;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.maven.api.IPom;
import aQute.maven.api.IPom.Dependency;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.BoundRevision;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository.BinaryLocation;
import de.mnl.osgi.bnd.maven.MavenVersionRange;
import de.mnl.osgi.bnd.maven.RepositoryUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private static final String NOT_AVAILABLE = "_NOT_AVAILABLE_";

    /** Indexing state. */
    private enum IndexingState {
        NONE, EXCLUDED, INDEXING, INDEXED
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
    private final Map<Revision, IndexingState> indexingState
        = new ConcurrentHashMap<>();
    private boolean propsChanged;
    private boolean indexChanged;
    private ResourcesRepository backupRepo;
    private final Pattern hrefPattern = Pattern.compile(
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
    @SuppressWarnings("PMD.ConfusingTernary")
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
        // If directory does not exist, it's not from a request.
        if (!directory.toFile().exists()) {
            directory.toFile().mkdir();
            propsChanged = true;
        } else {
            // Directory exists, either as newly created or as "old"
            if (groupPropsPath.toFile().canRead()) {
                try (InputStream input = Files.newInputStream(groupPropsPath)) {
                    groupProps.load(input);
                }
            } else {
                propsChanged = true;
            }
        }

        // Prepare OSGi repository
        groupIndexPath = directory.resolve("index.xml");
        if (groupIndexPath.toFile().canRead()) {
            try (XMLResourceParser parser
                = new XMLResourceParser(groupIndexPath.toFile())) {
                // addAll(parser.parse());
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
        if (propsChanged) {
            try (OutputStream out = Files.newOutputStream(groupPropsPath)) {
                groupProps.store(out, "Group properties");
            }
            propsChanged = false;
        }
        if (indexChanged) {
            XMLResourceGenerator generator = new XMLResourceGenerator();
            generator.resources(getResources());
            generator.name(indexedRepository.mavenRepository().getName());
            try {
                generator.save(groupIndexPath.toFile());
            } catch (IOException e) {
                reporter.exception(e, "Cannot save %s.", groupIndexPath);
            }
            indexChanged = false;
        }
        backupRepo = null;
        indexingState.clear();
    }

    /**
     * Removes the group directory if empty.
     *
     * @return true, if removed
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public boolean removeIfRedundant() throws IOException {
        if (isRequested() || !groupProps.isEmpty()) {
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

    @SuppressWarnings("PMD.ForLoopCanBeForeach")
    private CompletableFuture<Void> loadProgram(Program program) {
        // Get revisions of program and process.
        CompletableFuture<Void> result = new CompletableFuture<>();
        IndexedMavenRepository.programLoaders.submit(() -> {
            try {
                Stream<CompletableFuture<Void>> revLoaderStream
                    = indexedRepository.mavenRepository()
                        .boundRevisions(program).parallel()
                        .map(revision -> loadRevision(revision));
                try {
                    for (CompletableFuture<Void> revLoad : (Iterable<
                            CompletableFuture<
                                    Void>>) revLoaderStream::iterator) {
                        revLoad.get();
                    }
                } catch (InterruptedException e) {
                    result.completeExceptionally(e);
                    return;
                } catch (ExecutionException e) {
                    result.completeExceptionally(e.getCause());
                    return;
                }
                result.complete(null);
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private CompletableFuture<Void> loadRevision(BoundRevision revision) {
        return CompletableFuture.runAsync(() -> {
            if (indexingState.getOrDefault(revision.unbound(),
                IndexingState.NONE) != IndexingState.NONE) {
                // Already loading or handled (as dependency)
                return;
            }
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
                    return;
                }
            }
            try {
                Set<BoundRevision> deps = new HashSet<>();
                if (isIndexable(revision, deps, inForced)) {
                    addRevision(revision);
                    for (BoundRevision rev : deps) {
                        indexedRepository
                            .getOrCreateGroupRepository(rev.groupId())
                            .addRevision(rev);
                    }
                }
            } catch (Exception e) {
                // Load "in isolation", don't propagate individual failure.
                reporter.exception(e, "Failed to load %s: %s", revision,
                    e.getMessage());
            }
        }, IndexedMavenRepository.revisionLoaders);

    }

    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    private Collection<String> findArtifactIds() {
        Set<String> result = new HashSet<>();
        for (MavenBackingRepository repo : indexedRepository.mavenRepository()
            .allRepositories()) {
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
     * Checks if the given revision is indexable. A revision is
     * indexable if it is not excluded and its dependencies aren't
     * excluded either.
     *
     * @param revision the revision
     * @param dependencies revisions that must additionally be included
     *    when the checked revision is added
     * @param ignoreExcludedDependencies evaluate as indexable even
     *    if some dependencies aren't indexable
     * @return true, if the revision can be added to the index
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.LongVariable")
    public boolean isIndexable(BoundRevision revision,
            Set<BoundRevision> dependencies,
            boolean ignoreExcludedDependencies) throws IOException {
        if (!revision.groupId().equals(groupId)) {
            throw new IllegalArgumentException("Wrong groupId "
                + revision.groupId() + " (must be " + groupId + ").");
        }
        // Check if already there (or being added)
        if (INDEXING_OR_INDEXED.contains(indexingState
            .getOrDefault(revision.unbound(), IndexingState.NONE))) {
            // Obviously indexable and no additional resources needed.
            return true;
        }
        // Check if excluded by rule.
        if (Optional.ofNullable(
            searchProperty(revision.artifactId(), "exclude"))
            .map(MavenVersionRange::parseRange)
            .map(range -> range.includes(revision.version()))
            .orElse(false)) {
            return false;
        }
        if (!ignoreExcludedDependencies
            && indexingState.getOrDefault(revision.unbound(),
                IndexingState.NONE) == IndexingState.EXCLUDED) {
            // Has been found un-indexable before
            return false;
        }
        // Check whether excluded because dependency is excluded
        Set<Dependency> childDeps = new HashSet<>();
        collectDependencies(revision, childDeps);
        for (IPom.Dependency dep : childDeps) {
            Optional<BoundRevision> boundDep;
            try {
                boundDep = indexedRepository.mavenRepository().toBoundRevision(
                    dep.program, dep.version);
            } catch (IOException e) {
                reporter.exception(e, "Cannot get revision for %s:%s.",
                    dep.program, dep.version);
                continue;
            }
            if (!boundDep.isPresent()) {
                continue;
            }
            MavenGroupRepository depRepo = indexedRepository
                .getOrCreateGroupRepository(dep.program.group);
            if (!depRepo.isIndexable(boundDep.get(), dependencies,
                ignoreExcludedDependencies) && !ignoreExcludedDependencies) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds the specified revision unless it matches an exclude.
     *
     * @param revision the revision to add
     */
    private void addRevision(BoundRevision revision) {
        synchronized (indexingState) {
            // Check, but don't keep the lock
            if (INDEXING_OR_INDEXED.contains(indexingState
                .getOrDefault(revision.unbound(), IndexingState.NONE))) {
                return;
            }
            indexingState.put(revision.unbound(), IndexingState.INDEXING);
        }
        Resource resource = null;
        if (backupRepo != null) {
            List<Capability> cap = backupRepo.findProvider(
                backupRepo.newRequirementBuilder("bnd.info")
                    .addDirective("filter", String.format("(from=%s)",
                        revision.unbound().toString()))
                    .build());
            if (!cap.isEmpty()) {
                // Reuse existing
                resource = cap.get(0).getResource();
            }
        }
        if (resource == null) {
            // Extract information from artifact.
            resource = indexedRepository.mavenRepository()
                .toResource(revision, deps -> {
                }, BinaryLocation.REMOTE).orElse(null);
        }
        if (resource == null) {
            // No such resource
            synchronized (indexingState) {
                indexingState.remove(revision.unbound());
            }
            return;
        }
        // Check again, now with lock, and add
        synchronized (indexingState) {
            if (indexingState.getOrDefault(revision.unbound(),
                IndexingState.NONE) == IndexingState.INDEXED) {
                return;
            }
            add(resource);
        }
        indexChanged = true;
    }

    /**
     * Collects the revisions that are immediate dependencies 
     * of the revision passed as argument. This method uses
     * any cached information first before resorting to
     * {@link CompositeMavenRepository#toResource(BoundRevision, 
     * CompositeMavenRepository.DependencyHandler, BinaryLocation)}.
     *
     * @param revision the revision to examine
     * @param dependencies the dependencies
     */
    @SuppressWarnings({ "PMD.AssignmentInOperand", "PMD.ConfusingTernary" })
    public void collectDependencies(BoundRevision revision,
            Collection<Dependency> dependencies) {
        List<Capability> cap = null;
        if (backupRepo != null && !(cap = backupRepo.findProvider(
            backupRepo.newRequirementBuilder("bnd.info")
                .addDirective("filter", String.format("(from=%s)",
                    revision.unbound().toString()))
                .build())).isEmpty()) {
            restoreDependencies(cap.get(0).getResource(), dependencies);
        } else {
            // Extract information from artifact.
            indexedRepository.mavenRepository().toResource(revision,
                deps -> dependencies.addAll(deps), BinaryLocation.REMOTE);
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void restoreDependencies(Resource resource,
            Collection<Dependency> dependencies) {
        // Actually, there should be only one such capability per resource.
        for (Capability capability : resource
            .getCapabilities(CompositeMavenRepository.MAVEN_DEPENDENCIES_NS)) {
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

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private String searchProperty(String artifactId, String qualifier) {
        String queryKey = artifactId + ";" + qualifier;
        // Attempt to get from cache.
        String prop = propQueryCache.get(queryKey);
        // Special value, because ConcurrentHashMap cannot have null values.
        if (prop == NOT_AVAILABLE) {
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
        propQueryCache.put(queryKey, prop == null ? NOT_AVAILABLE : prop);
        return prop;
    }

}
