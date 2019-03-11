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
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.MergingMavenRepository;

import java.beans.IndexedPropertyDescriptor;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.osgi.framework.AllServiceListener;
import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide an OSGi repository (a collection of {@link Resource}s, see 
 * {@link Repository}), filled with resolved artifacts from given groupIds.
 */
public class IndexedMavenRepository extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        IndexedMavenRepository.class);
    private final String name;
    private final File indexDbDir;
    private final List<URL> releaseUrls;
    private final List<URL> snapshotUrls;
    private final File localRepo;
    private final Reporter reporter;
    private final HttpClient client;
    private final MergingMavenRepository mavenRepository;
    private final Set<URL> repositoryUrls = new HashSet<>();
    private final Pattern hrefPattern = Pattern.compile(
        "<[aA]\\s+(?:[^>]*?\\s+)?href=(?<quote>[\"'])"
            + "(?<href>[a-zA-Z].*?)\\k<quote>");

    /**
     * Create a new instance that uses the provided information/resources to perform
     * its work.
     *
     * @param name the name
     * @param releaseUrls the release urls
     * @param snapshotUrls the snapshot urls
     * @param localRepo the local Maven repository (cache)
     * @param indexDbDir the persistent representation of this repository's content
     * @param reporter a reporter for reporting the progress
     * @param client an HTTP client for obtaining information from the Nexus server
     * @throws Exception the exception
     */
    public IndexedMavenRepository(String name, List<URL> releaseUrls,
            List<URL> snapshotUrls, File localRepo, File indexDbDir,
            Reporter reporter, HttpClient client) throws Exception {
        this.name = name;
        this.indexDbDir = indexDbDir;
        this.releaseUrls = releaseUrls;
        this.snapshotUrls = snapshotUrls;
        this.localRepo = localRepo;
        this.reporter = reporter;
        this.client = client;

        // Check prerequisites
        if (indexDbDir.exists() && !indexDbDir.isDirectory()) {
            LOG.error(indexDbDir + "must be a directory.");
            throw new IOException(indexDbDir + "must be a directory.");
        }
        if (!indexDbDir.exists()) {
            indexDbDir.mkdirs();
        }

        // All URLs
        repositoryUrls.addAll(releaseUrls);
        repositoryUrls.addAll(snapshotUrls);

        // Our backend
        mavenRepository = createMavenRepository();
        // refresh();
    }

    private MergingMavenRepository createMavenRepository() throws Exception {
        // Create repository from URLs
        List<MavenBackingRepository> releaseBackers = new ArrayList<>();
        for (URL url : releaseUrls) {
            releaseBackers.addAll(MavenBackingRepository.create(
                url.toString(), reporter, localRepo, client));
        }
        List<MavenBackingRepository> snapshotBackers = new ArrayList<>();
        for (URL url : snapshotUrls) {
            snapshotBackers.addAll(MavenBackingRepository.create(
                url.toString(), reporter, localRepo, client));
        }
        return new MergingMavenRepository(localRepo, name(), releaseBackers,
            snapshotBackers, Processor.getExecutor(), reporter);
    }

    /**
     * Return the name of this repository.
     * 
     * @return the name;
     */
    public final String name() {
        return name;
    }

    /**
     * Return the representation of this repository in the local file system.
     * 
     * @return the location
     */
    public final File location() {
        return indexDbDir;
    }

    /**
     * Return the Maven repository object used to implements this repository.
     * 
     * @return the repository object
     * @throws Exception if a problem occurs
     */
    public IMavenRepo mavenRepository() throws Exception {
        if (mavenRepository == null) {
            refresh();
        }
        return mavenRepository;
    }

    /**
     * Refresh this repository's content.
     * 
     * @return true if refreshed, false if not refreshed possibly due to error
     * @throws Exception if a problem occurs
     */
    public boolean refresh() throws Exception {
        for (String dir : indexDbDir.list()) {
            if (!dir.matches("^[A-Za-z].*")) {
                continue;
            }
            LOG.debug("Refreshing {}", dir);
            Collection<String> artifactIds = findArtifactIds(dir);
            for (String artifactId : artifactIds) {
                if (artifactId.endsWith("/")) {
                    artifactId
                        = artifactId.substring(0, artifactId.length() - 1);
                }
                Program program = Program.valueOf(dir, artifactId);
                List<Revision> revisions
                    = mavenRepository.getRevisions(program);
                for (Revision rev : revisions) {
                    String path = rev.archive("jar", null).remotePath;
                    path = null;
                }
                revisions = null;
            }
        }
//        if (coordinates == null) {
//            return false;
//        }
//        List<Revision> startRevisions = coordinates.stream()
//            .map(an -> Revision.valueOf(an)).collect(Collectors.toList());
//        // List all revisions from configuration.
//        for (Revision revision : startRevisions) {
//            logger.debug("Found {}", revision);
//        }
//        return refresh(mavenRepository, startRevisions);
        return true;
    }

    public Collection<String> findArtifactIds(String dir) throws Exception {
        Set<String> result = new HashSet<>();
        for (URL repoUrl : repositoryUrls) {
            String page = client.build().headers("User-Agent", "Bnd")
                .get(String.class).go(new URL(repoUrl, dir.replace('.', '/')));
            Matcher matcher = hrefPattern.matcher(page);
            while (matcher.find()) {
                result.add(matcher.group("href"));
            }
        }
        return result;
    }

}
