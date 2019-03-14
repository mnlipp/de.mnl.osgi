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
import de.mnl.osgi.bnd.maven.BoundRevision;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository.BinaryLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.resource.Capability;
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

    private final String groupId;
    private final CompositeMavenRepository mavenRepository;
    private final HttpClient client;
    private final Path groupPropsPath;
    private final Path groupIndexPath;
    private final Set<Revision> mavenRevisions = new HashSet<>();
    private final Properties groupProps;
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
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.ConfusingTernary")
    public MavenGroupRepository(String groupId, Path directory,
            CompositeMavenRepository mavenRepository, HttpClient client)
            throws IOException {
        this.groupId = groupId;
        this.mavenRepository = mavenRepository;
        this.client = client;

        // Prepare directory and files
        groupPropsPath = directory.resolve("group.properties");
        groupProps = new Properties();
        // If directory does not exist, it's not from a request.
        if (!directory.toFile().exists()) {
            directory.toFile().mkdir();
            groupProps.setProperty("requested", "false");
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
                LOG.warn("Cannot parse {}, ignored.", groupIndexPath, e);
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
                LOG.error("Cannot save {}.", groupIndexPath, e);
            }
            indexChanged = false;
        }
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
     * Checks if the group has explicitly been requested.
     *
     * @return true, if is requested
     */
    public boolean isRequested() {
        return Boolean
            .parseBoolean(groupProps.getProperty("requested", "true"));
    }

    /**
     * Clears this repository. Keeps the current content as
     * backup for reuse in a subsequent call to {@link #refresh(Consumer)}.
     */
    public void clear() {
        synchronized (this) {
            backupRepo = new ResourcesRepository(getResources());
            set(Collections.emptyList());
            mavenRevisions.clear();
        }
    }

    /**
     * Refresh the repository. This retrieves the list of known
     * artifactIds from the remote repository and adds the
     * versions. For versions already in the repository, the
     * existing information is re-used.  
     *
     * @param dependencyHandler the dependency handler
     * @throws IOException 
     */
    @SuppressWarnings({ "PMD.AvoidReassigningLoopVariables",
        "PMD.AvoidCatchingGenericException" })
    public void
            refresh(Consumer<Collection<IPom.Dependency>> dependencyHandler)
                    throws IOException {
        if (!isRequested()) {
            return;
        }
        synchronized (this) {
            if (backupRepo == null) {
                backupRepo = new ResourcesRepository(getResources());
            }
            set(Collections.emptyList());
            mavenRevisions.clear();
        }
        Collection<String> artifactIds = findArtifactIds(groupId);
        for (String artifactId : artifactIds) {
            Program program = Program.valueOf(groupId, artifactId);

            // Get revisions of program.
            List<BoundRevision> revisions;
            try {
                revisions = mavenRepository.boundRevisions(program);
            } catch (Exception e) {
                // Strange name in parsed result.
                LOG.warn("Couldn't get revisions for {}" // NOPMD (constant)
                    + " (found in maven-metadata.xml).", program, e);
                continue;
            }

            // Add the revisions.
            for (BoundRevision rev : revisions) {
                addRevision(rev, dependencyHandler);
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
                LOG.warn("Problem retrieving {}, skipped.", groupUri, e);
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
}
