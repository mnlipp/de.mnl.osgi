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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A repository with artifacts from a single group.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class MavenGroupRepository extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        MavenGroupRepository.class);
    private static final String NOT_AVAILABLE = "_NOT_AVAILABLE_";

    private final String groupId;
    private boolean requested;
    private final CompositeMavenRepository mavenRepository;
    private final HttpClient client;
    private final Reporter reporter;
    private final Path groupDir;
    private final Path groupPropsPath;
    private final Path groupIndexPath;
    private final Set<Revision> mavenRevisions = new HashSet<>();
    private final Properties groupProps;
    private final Map<String, String> propQueryCache
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
     * @param mavenRepository the maven repository
     * @param client the client used for remote access
     * @param reporter the reporter
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.ConfusingTernary")
    public MavenGroupRepository(String groupId, Path directory,
            boolean requested, CompositeMavenRepository mavenRepository,
            HttpClient client, Reporter reporter) throws IOException {
        this.groupId = groupId;
        this.groupDir = directory;
        this.requested = requested;
        this.mavenRepository = mavenRepository;
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
            mavenRevisions.add(
                Revision.valueOf((String) cap.getAttributes().get("from")));
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
     * Writes all changes to persistent storage.
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
            generator.name(mavenRepository.getName());
            try {
                generator.save(groupIndexPath.toFile());
            } catch (IOException e) {
                reporter.exception(e, "Cannot save %s.", groupIndexPath);
            }
            indexChanged = false;
        }
    }

    /**
     * Removes the group directory if empty.
     *
     * @return true, if removed
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public boolean removeIfRedundant() throws IOException {
        if (mavenRevisions.isEmpty()) {
            // Nothing in this group
            Files.walk(groupDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            return true;
        }
        return false;
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
     * subsequent call to {@link #reload(Consumer)}.
     */
    public void reset(boolean requested) {
        synchronized (this) {
            this.requested = requested;
            backupRepo = new ResourcesRepository(getResources());
            set(Collections.emptyList());
            mavenRevisions.clear();
            propQueryCache.clear();
        }
    }

    /**
     * Reload the repository. Requested repositories retrieve
     * the list of known artifactIds from the remote repository 
     * and add the versions. For versions already in the repository, 
     * the backup information is re-used.
     *
     * @param dependencyHandler the dependency handler
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.AvoidReassigningLoopVariables",
        "PMD.AvoidCatchingGenericException" })
    public void reload(Consumer<Collection<IPom.Dependency>> dependencyHandler)
            throws IOException {
        if (groupPropsPath.toFile().canRead()) {
            try (InputStream input = Files.newInputStream(groupPropsPath)) {
                groupProps.clear();
                groupProps.load(input);
            }
        }
        if (!isRequested()) {
            return;
        }
        synchronized (this) {
            if (backupRepo == null) {
                backupRepo = new ResourcesRepository(getResources());
            }
            propQueryCache.clear();
        }
        Collection<String> artifactIds = findArtifactIds(groupId);
        for (String artifactId : artifactIds) {
            Program program = Program.valueOf(groupId, artifactId);

            // Get revisions of program.
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            IOException[] exc = new IOException[1];
            MavenVersionRange range
                = Optional.ofNullable(searchProperty(artifactId, "versions"))
                    .map(MavenVersionRange::parseRange).orElse(null);
            mavenRepository.boundRevisions(program).forEach(
                revision -> {
                    if (range != null && !range.includes(revision.version())) {
                        return;
                    }
                    try {
                        addRevision(revision, dependencyHandler);
                    } catch (IOException e) {
                        exc[0] = e;
                    }
                });
            if (exc[0] != null) {
                throw exc[0];
            }
        }
        indexChanged = true;
    }

    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    private Collection<String> findArtifactIds(String dir) {
        Set<String> result = new HashSet<>();
        for (MavenBackingRepository repo : mavenRepository.allRepositories()) {
            URI groupUri = null;
            try {
                groupUri = repo.toURI("").resolve(dir.replace('.', '/') + "/");
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
     * Adds the specified revision.
     *
     * @param revision the revision
     * @param dependencyHandler the dependency handler
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void addRevision(BoundRevision revision,
            Consumer<Collection<IPom.Dependency>> dependencyHandler)
            throws IOException {
        if (!revision.groupId().equals(groupId)) {
            throw new IllegalArgumentException("Wrong groupId "
                + revision.groupId() + " (must be " + groupId + ").");
        }
        synchronized (this) {
            if (mavenRevisions.contains(revision.unbound())) {
                return;
            }
        }
        MavenVersionRange excludeRange = Optional.ofNullable(
            searchProperty(revision.artifactId(), "exclude"))
            .map(MavenVersionRange::parseRange).orElse(null);
        if (excludeRange != null && excludeRange.includes(revision.version())) {
            return;
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
                replayDependencies(resource, dependencyHandler);
            }
        }
        if (resource == null) {
            // Extract information from artifact.
            resource = mavenRepository.toResource(revision,
                dependencyHandler, BinaryLocation.REMOTE).orElse(null);
        }
        if (resource != null) {
            synchronized (this) {
                add(resource);
                mavenRevisions.add(revision.unbound());
                indexChanged = true;
            }
        }
    }

    private void replayDependencies(Resource resource,
            Consumer<Collection<IPom.Dependency>> dependencyHandler) {
        for (Capability capability : resource
            .getCapabilities(CompositeMavenRepository.MAVEN_DEPENDENCIES_NS)) {
            Map<String, Object> depAttrs = capability.getAttributes();
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            Collection<IPom.Dependency> dependencies
                = depAttrs.values().stream()
                    .flatMap(val -> RepositoryUtils.itemizeList((String) val))
                    .map(rev -> {
                        String[] parts = rev.split(":");
                        IPom.Dependency dep = new IPom.Dependency();
                        dep.program = Program.valueOf(parts[0], parts[1]);
                        dep.version = parts[2];
                        return dep;
                    }).collect(Collectors.toList());
            dependencyHandler.accept(dependencies);
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
