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
import de.mnl.osgi.bnd.maven.MergingMavenRepository;
import de.mnl.osgi.bnd.maven.RevisionIndexer;
import de.mnl.osgi.bnd.maven.RevisionIndexer.IndexedResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A manager for a single group index.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class MavenGroupRepository extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        MavenGroupRepository.class);

    private final String groupId;
    private final MergingMavenRepository mavenRepository;
    private final HttpClient client;
    private final Path groupPropsPath;
    private final Path groupIndexPath;
    private final Set<Revision> mavenRevisions = new HashSet<>();
    private final RevisionIndexer indexer;
    private final Properties groupProps;
    private boolean propsChanged;
    private boolean indexChanged;
    private final Pattern hrefPattern = Pattern.compile(
        "<[aA]\\s+(?:[^>]*?\\s+)?href=(?<quote>[\"'])"
            + "(?<href>[a-zA-Z].*?)\\k<quote>");

    /**
     * Instantiates a new representation of group data backed
     * by the specified directory. 
     * 
     * @param directory the directory
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.ConfusingTernary")
    public MavenGroupRepository(String groupId, Path directory,
            MergingMavenRepository mavenRepository, HttpClient client)
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

        // Helper for adding to index
        indexer = new RevisionIndexer(mavenRepository, this,
            IndexedResource.REMOTE);
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
     * Refresh.
     */
    @SuppressWarnings({ "PMD.AvoidReassigningLoopVariables",
        "PMD.AvoidCatchingGenericException" })
    public void
            rescan(Consumer<Collection<IPom.Dependency>> dependencyHandler) {
        ResourcesRepository oldRepo = new ResourcesRepository(getResources());
        set(Collections.emptyList());
        mavenRevisions.clear();
        Collection<String> artifactIds = findArtifactIds(groupId);
        for (String artifactId : artifactIds) {
            if (artifactId.endsWith("/")) {
                artifactId = artifactId.substring(0, artifactId.length() - 1);
            }
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

            // Index the revisions.
            for (BoundRevision rev : revisions) {
                List<Capability> cap = oldRepo.findProvider(
                    oldRepo.newRequirementBuilder("bnd.info")
                        .addAttribute("from", rev.revision().toString())
                        .build());
                if (!cap.isEmpty()) { // NOPMD
                    // Reuse existing
                    add(cap.get(0).getResource());
                } else {
                    // Extract information from artifact.
                    indexer.indexRevision(rev, dependencyHandler);
                }
                mavenRevisions.add(rev.revision());
            }
        }
        indexChanged = true;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Collection<String> findArtifactIds(String dir) {
        Set<String> result = new HashSet<>();
        for (MavenBackingRepository repo : mavenRepository.allRepositories()) {
            URL repoUrl = null;
            try {
                repoUrl = repo.toURI("").toURL();
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                String page = client.build().headers("User-Agent", "Bnd")
                    .get(String.class)
                    .go(new URL(repoUrl, dir.replace('.', '/')));
                if (page == null) {
                    continue;
                }
                Matcher matcher = hrefPattern.matcher(page);
                while (matcher.find()) {
                    result.add(matcher.group("href"));
                }
            } catch (Exception e) {
                LOG.warn("Problem retrieving {}, skipped.", repoUrl, e);
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
    public void addRevision(Revision revision,
            Consumer<Collection<IPom.Dependency>> dependencyHandler)
            throws IOException {
        if (!revision.group.equals(groupId)) {
            throw new IllegalArgumentException("Wrong groupId "
                + revision.group + " (must be " + groupId + ").");
        }
        if (mavenRevisions.contains(revision)) {
            return;
        }
        mavenRepository.toBoundRevision(revision).ifPresent(rev -> {
            indexer.indexRevision(rev, dependencyHandler);
            mavenRevisions.add(revision);
        });
    }
}
