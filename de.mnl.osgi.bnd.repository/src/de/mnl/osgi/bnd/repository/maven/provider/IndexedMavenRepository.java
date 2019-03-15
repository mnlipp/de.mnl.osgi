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
import aQute.maven.api.IPom;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.BoundRevision;
import de.mnl.osgi.bnd.maven.CompositeMavenRepository;
import de.mnl.osgi.bnd.maven.TaskCollection;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Provide an OSGi repository (a collection of {@link Resource}s, see 
 * {@link Repository}), filled with resolved artifacts from given groupIds.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class IndexedMavenRepository extends ResourcesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(
        IndexedMavenRepository.class);
    private final String name;
    private final Path indexDbDir;
    private final List<URL> releaseUrls;
    private final List<URL> snapshotUrls;
    private final File localRepo;
    private final Reporter reporter;
    private final HttpClient client;
    private final CompositeMavenRepository mavenRepository;
    private final TaskCollection execCtx;
    private final Map<String, MavenGroupRepository> groups
        = new ConcurrentHashMap<>();

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
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.SignatureDeclareThrowsException", "PMD.AvoidDuplicateLiterals" })
    public IndexedMavenRepository(String name, List<URL> releaseUrls,
            List<URL> snapshotUrls, File localRepo, File indexDbDir,
            Reporter reporter, HttpClient client) throws Exception {
        this.name = name;
        this.indexDbDir = indexDbDir.toPath();
        this.releaseUrls = releaseUrls;
        this.snapshotUrls = snapshotUrls;
        this.localRepo = localRepo;
        this.reporter = reporter;
        this.client = client;
        execCtx = new TaskCollection(Processor.getScheduledExecutor());

        // Check prerequisites
        if (indexDbDir.exists() && !indexDbDir.isDirectory()) {
            LOG.error("{} must be a directory.", indexDbDir);
            throw new IOException(indexDbDir + "must be a directory.");
        }
        if (!indexDbDir.exists()) {
            indexDbDir.mkdirs();
        }

        // Our repository
        mavenRepository = createMavenRepository();

        // Restore
        File federatedIndex = this.indexDbDir.resolve("index.xml").toFile();
        if (federatedIndex.canRead()) {
            try (XMLResourceParser parser
                = new XMLResourceParser(federatedIndex)) {
                addAll(parser.parse());
            } catch (Exception e) {
                LOG.warn("Cannot parse federated index (ignored).", e);
            }
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private CompositeMavenRepository createMavenRepository() throws Exception {
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
        return new CompositeMavenRepository(localRepo, name(), releaseBackers,
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
        return indexDbDir.toFile();
    }

    /**
     * Return the Maven repository object used to implements this repository.
     * 
     * @return the repository object
     * @throws Exception if a problem occurs
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
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
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    public boolean refresh() throws Exception {
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, MavenGroupRepository> oldGroups = new HashMap<>(groups);

        // Keep and clear or create new group repositories for the existing
        // directories.
        groups.clear();
        for (String groupId : indexDbDir.toFile().list()) {
            if (!groupId.matches("^[A-Za-z].*")
                || "index.xml".equals(groupId)) {
                continue;
            }
            execCtx.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    if (oldGroups.containsKey(groupId)) {
                        MavenGroupRepository groupRepo = oldGroups.get(groupId);
                        oldGroups.clear();
                        groups.put(groupId, groupRepo);
                        return null;
                    }
                    MavenGroupRepository groupRepo = new MavenGroupRepository(
                        groupId, indexDbDir.resolve(groupId), mavenRepository,
                        client);
                    groups.put(groupId, groupRepo);
                    return null;
                }
            });
        }
        execCtx.await();
        for (MavenGroupRepository group : new ArrayList<>(groups.values())) {
            if (group.isRequested()) {
                execCtx.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        LOG.debug("Refreshing {}", group.id());
                        group.refresh(
                            deps -> handleDependencies(deps));
                        return null;
                    }
                });
            }
        }
        execCtx.await();
        for (MavenGroupRepository groupRepo : groups.values()) {
            execCtx.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    groupRepo.flush();
                    return null;
                }
            });
        }
        execCtx.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try (OutputStream fos
                    = Files.newOutputStream(indexDbDir.resolve("index.xml"))) {
                    writeFederatedIndex(fos);
                }
                return null;
            }
        });
        execCtx.submit(() -> {
            set(Collections.emptyList());
            for (MavenGroupRepository groupRepo : groups.values()) {
                addAll(groupRepo.getResources());
            }
        });
        execCtx.await();
        return true;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void handleDependencies(Collection<IPom.Dependency> dependencies) {
        for (IPom.Dependency dep : dependencies) {
            MavenGroupRepository groupRepo = groups.computeIfAbsent(
                dep.program.group, groupId -> {
                    try {
                        return new MavenGroupRepository(groupId,
                            indexDbDir.resolve(groupId), mavenRepository,
                            client);
                    } catch (IOException e) {
                        LOG.error("Cannot create group repository for {}.",
                            groupId, e);
                        return null;
                    }
                });
            if (groupRepo == null) {
                return;
            }
            execCtx.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Optional<BoundRevision> bound = mavenRepository
                        .toBoundRevision(dep.program, dep.version);
                    if (bound.isPresent()) {
                        groupRepo.addRevision(bound.get(),
                            deps -> handleDependencies(deps));
                    }
                    return null;
                }
            });
        }
    }

    private void writeFederatedIndex(OutputStream out) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc;
        try {
            doc = dbf.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            return;
        }
        @SuppressWarnings("PMD.AvoidFinalLocalVariable")
        final String repoNs = "http://www.osgi.org/xmlns/repository/v1.0.0";
        // <repository name=... increment=...>
        Element repoNode = doc.createElementNS(repoNs, "repository");
        repoNode.setAttribute("name", name);
        repoNode.setAttribute("increment",
            Long.toString(System.currentTimeMillis()));
        doc.appendChild(repoNode);
        for (String groupId : groups.keySet()) {
            // <referral url=...>
            Element referral = doc.createElementNS(repoNs, "referral");
            referral.setAttribute("url", groupId + "/index.xml");
            repoNode.appendChild(referral);
        }
        // Write federated index.
        try {
            Transformer transformer
                = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, new StreamResult(out));
        } catch (TransformerException e) {
            LOG.error("Cannot write federated index.", e);
        }
    }
}
