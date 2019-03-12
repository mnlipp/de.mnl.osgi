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
import aQute.maven.api.Program;
import aQute.maven.provider.MavenBackingRepository;
import de.mnl.osgi.bnd.maven.ExtRevision;
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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A manager for a single group index.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class GroupIndexManager {

    private static final Logger LOG = LoggerFactory.getLogger(
        GroupIndexManager.class);

    private final String groupId;
    private final Path groupDir;
    private final MergingMavenRepository mavenRepository;
    private final HttpClient client;
    private final Path groupPropsPath;
    private final Path groupIndexPath;
    private final ResourcesRepository osgiRepository;
    private final Properties groupProps;
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
    public GroupIndexManager(String groupId, Path directory,
            MergingMavenRepository mavenRepository, HttpClient client)
            throws IOException {
        this.groupId = groupId;
        this.groupDir = directory;
        this.mavenRepository = mavenRepository;
        this.client = client;

        // Prepare directory and files
        groupPropsPath = directory.resolve("group.properties");
        groupProps = new Properties();
        // If directory does not exist, it is created for dependencies
        if (!groupDir.toFile().exists()) { // NOPMD
            groupDir.toFile().mkdir();
            groupProps.setProperty("requested", "false");
            try (OutputStream out = Files.newOutputStream(groupPropsPath)) {
                groupProps.store(out, "Group properties");
            }
        } else {
            // Directory exists, either as newly created or as "old"
            if (groupPropsPath.toFile().canRead()) {
                try (InputStream input = Files.newInputStream(groupPropsPath)) {
                    groupProps.load(input);
                }
            }
        }

        // Prepare OSGi repository
        osgiRepository = new ResourcesRepository();
        groupIndexPath = directory.resolve("index.xml");
        if (groupIndexPath.toFile().canRead()) {
            try (XMLResourceParser parser
                = new XMLResourceParser(groupIndexPath.toFile())) {
                osgiRepository.addAll(parser.parse());
            } catch (Exception e) { // NOPMD
                LOG.warn("Cannot parse {}, ignored.", groupIndexPath, e);
            }
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
    public void rescan() {
        RevisionIndexer indexer = new RevisionIndexer(mavenRepository,
            osgiRepository, IndexedResource.REMOTE);
        Collection<String> artifactIds = findArtifactIds(groupId);
        for (String artifactId : artifactIds) {
            if (artifactId.endsWith("/")) {
                artifactId
                    = artifactId.substring(0, artifactId.length() - 1);
            }
            Program program = Program.valueOf(groupId, artifactId);
            List<ExtRevision> revisions;
            try {
                revisions = mavenRepository.getExtRevisions(program);
            } catch (Exception e) {
                // Strange name in parsed result.
                continue;
            }
            indexer.indexRevisions(revisions, dependencies -> {
                int i = 0;
            });
        }
        XMLResourceGenerator generator = new XMLResourceGenerator();
        generator.resources(osgiRepository.getResources());
        generator.name(mavenRepository.getName());
        try {
            generator.save(groupIndexPath.toFile());
        } catch (IOException e) {
            LOG.error("Cannot save {}.", groupIndexPath, e);
        }

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

}
