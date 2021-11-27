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
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.maven.api.Archive;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.MavenResourceRepository;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.rethrow;
import static de.mnl.osgi.bnd.maven.RepositoryUtils.unthrow;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /* default */ static ExecutorService programLoaders
        = Executors.newFixedThreadPool(4);
    /* default */ static ExecutorService revisionLoaders
        = Executors.newFixedThreadPool(8);

    private static final Logger LOG = LoggerFactory.getLogger(
        IndexedMavenRepository.class);
    private final String name;
    private final Path indexDbDir;
    private final Path depsDir;
    private final List<URL> releaseUrls;
    private final List<URL> snapshotUrls;
    private final File localRepo;
    private final Reporter reporter;
    private final HttpClient client;
    private final boolean logIndexing;
    private final ExecutorService groupLoaders
        = Executors.newFixedThreadPool(4);
    private final MavenResourceRepository mavenRepository;
    private final Map<String, MavenGroupRepository> groups
        = new ConcurrentHashMap<>();
    private Map<String, MavenGroupRepository> backupGroups;
    private final AtomicBoolean refreshing = new AtomicBoolean();

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
     * @param logIndexing the log indexing
     * @throws Exception the exception
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.SignatureDeclareThrowsException", "PMD.AvoidDuplicateLiterals" })
    public IndexedMavenRepository(String name, List<URL> releaseUrls,
            List<URL> snapshotUrls, File localRepo, File indexDbDir,
            Reporter reporter, HttpClient client, boolean logIndexing)
            throws Exception {
        this.name = name;
        this.indexDbDir = indexDbDir.toPath();
        depsDir = this.indexDbDir.resolve("dependencies");
        this.releaseUrls = releaseUrls;
        this.snapshotUrls = snapshotUrls;
        this.localRepo = localRepo;
        this.reporter = reporter;
        this.client = client;
        this.logIndexing = logIndexing;

        // Check prerequisites
        if (indexDbDir.exists() && !indexDbDir.isDirectory()) {
            reporter.error("%s must be a directory.", indexDbDir);
            throw new IOException(indexDbDir + "must be a directory.");
        }
        if (!indexDbDir.exists()) {
            indexDbDir.mkdirs();
        }
        if (!depsDir.toFile().exists()) {
            depsDir.toFile().mkdir();
        }

        // Our repository
        mavenRepository = createMavenRepository();

        // Restore
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, MavenGroupRepository> oldGroups = new HashMap<>();
        CompletableFuture
            .allOf(scanRequested(oldGroups), scanDependencies(oldGroups)).get();
        for (MavenGroupRepository groupRepo : groups.values()) {
            addAll(groupRepo.getResources());
        }
        backupGroups = groups;
    }

    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException", "resource" })
    private MavenResourceRepository createMavenRepository() throws Exception {
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
        return new MavenResourceRepository(
            localRepo, name(), releaseBackers,
            snapshotBackers, Processor.getExecutor(), reporter)
                .setResourceSupplier(this::restoreResource);
    }

    private Optional<Resource> restoreResource(Archive archive) {
        return Optional.ofNullable(backupGroups.get(archive.revision.group))
            .flatMap(group -> group.searchInBackup(archive));
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
     * Returns true if indexing is to be logged.
     *
     * @return true, if indexing should be log
     */
    public boolean logIndexing() {
        return logIndexing;
    }

    /**
     * Return the Maven repository object used to implements this repository.
     *
     * @return the repository 
     */
    public MavenResourceRepository mavenRepository() {
        return mavenRepository;
    }

    /**
     * Get or create the group repository for the given group id.
     * If the repository is created, it is created as a repository
     * for dependencies.
     *
     * @param groupId the group id
     * @return the repository
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public MavenGroupRepository getOrCreateGroupRepository(String groupId)
            throws IOException {
        @SuppressWarnings("PMD.PrematureDeclaration")
        MavenGroupRepository result = rethrow(IOException.class,
            () -> groups.computeIfAbsent(groupId,
                grp -> unthrow(() -> new MavenGroupRepository(grp,
                    depsDir.resolve(grp), false, this, client, reporter))));
        return result;
    }

    /**
     * Refresh this repository's content.
     * 
     * @return true if refreshed, false if not refreshed possibly due to error
     * @throws Exception if a problem occurs
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public boolean refresh() throws Exception {
        if (!refreshing.compareAndSet(false, true)) {
            reporter.warning("Repository is already refreshing.");
            return false;
        }
        String threadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("IndexedMaven Refresher");
            return doRefresh();
        } finally {
            Thread.currentThread().setName(threadName);
            refreshing.set(false);
        }
    }

    @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.AvoidDuplicateLiterals", "PMD.SignatureDeclareThrowsException" })
    private boolean doRefresh() throws Exception {
        mavenRepository.reset();
        backupGroups = new HashMap<>(groups);

        // Reuse and clear (or create new) group repositories for the existing
        // directories, first for explicitly requested group ids...
        groups.clear();
        scanRequested(backupGroups).get();
        scanDependencies(backupGroups).get();
//        CompletableFuture
//            .allOf(scanRequested(backupGroups), scanDependencies(backupGroups))
//            .get();
        // Refresh them all.
        CompletableFuture<?>[] repoLoaders
            = new ArrayList<>(groups.values()).stream()
                .map(repo -> CompletableFuture.runAsync(() -> {
                    String threadName = Thread.currentThread().getName();
                    try {
                        Thread.currentThread()
                            .setName("RepoLoader " + repo.id());
                        LOG.debug("Reloading %s...", repo.id());
                        repo.reload();
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    } finally {
                        Thread.currentThread().setName(threadName);
                    }
                }, groupLoaders))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(repoLoaders).get();
        // Remove no longer required group repositories.
        for (Iterator<Map.Entry<String, MavenGroupRepository>> iter
            = groups.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, MavenGroupRepository> entry = iter.next();
            if (entry.getValue().removeIfRedundant()) {
                iter.remove();
            }
        }
        CompletableFuture.allOf(
            // Persist updated data.
            CompletableFuture.allOf(new ArrayList<>(groups.values()).stream()
                .map(repo -> CompletableFuture.runAsync(() -> {
                    try {
                        repo.flush();
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, groupLoaders))
                .toArray(CompletableFuture[]::new)),
            // Write federated index.
            CompletableFuture.runAsync(() -> {
                if (groups.keySet().equals(backupGroups.keySet())) {
                    return;
                }
                try (OutputStream fos
                    = Files.newOutputStream(indexDbDir.resolve("index.xml"))) {
                    writeFederatedIndex(fos);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, groupLoaders),
            // Add collected to root (this).
            CompletableFuture.runAsync(() -> {
                set(Collections.emptyList());
                for (MavenGroupRepository groupRepo : groups.values()) {
                    addAll(groupRepo.getResources());
                }
            }, groupLoaders)).get();
        backupGroups = groups;
        return true;
    }

    private CompletableFuture<Void>
            scanRequested(Map<String, MavenGroupRepository> oldGroups) {
        return CompletableFuture.allOf(
            Arrays.stream(indexDbDir.toFile().list()).parallel()
                .filter(dir -> dir.matches("^[A-Za-z].*")
                    && !"index.xml".equals(dir)
                    && !"dependencies".equals(dir))
                .map(groupId -> CompletableFuture
                    .runAsync(() -> restoreGroup(oldGroups, groupId, true),
                        groupLoaders))
                .toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void>
            scanDependencies(Map<String, MavenGroupRepository> oldGroups) {
        if (!depsDir.toFile().canRead() || !depsDir.toFile().isDirectory()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(
            Arrays.stream(depsDir.toFile().list()).parallel()
                .filter(dir -> dir.matches("^[A-Za-z].*"))
                .filter(dir -> {
                    if (groups.containsKey(dir)) {
                        // Is/has become explicitly requested
                        try {
                            Files.walk(depsDir.resolve(dir))
                                .sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        return false;
                    }
                    return true;
                })
                .map(groupId -> CompletableFuture
                    .runAsync(() -> restoreGroup(oldGroups, groupId, false),
                        groupLoaders))
                .toArray(CompletableFuture[]::new));
    }

    private void restoreGroup(Map<String, MavenGroupRepository> oldGroups,
            String groupId, boolean requested) {
        if (oldGroups.containsKey(groupId)) {
            // Reuse existing.
            MavenGroupRepository groupRepo = oldGroups.get(groupId);
            groupRepo.reset((requested ? indexDbDir : depsDir).resolve(groupId),
                requested);
            groups.put(groupId, groupRepo);
            return;
        }
        try {
            MavenGroupRepository groupRepo
                = new MavenGroupRepository(groupId,
                    (requested ? indexDbDir : depsDir).resolve(groupId),
                    requested, this, client, reporter);
            groups.put(groupId, groupRepo);
        } catch (IOException e) {
            reporter.exception(e,
                "Cannot create group repository for %s: %s",
                groupId, e.getMessage());
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
        for (Map.Entry<String, MavenGroupRepository> repo : groups.entrySet()) {
            // <referral url=...>
            Element referral = doc.createElementNS(repoNs, "referral");
            referral.setAttribute("url",
                (repo.getValue().isRequested() ? "" : "dependencies/")
                    + repo.getKey() + "/index.xml");
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
            reporter.exception(e, "Cannot write federated index: %s",
                e.getMessage());
        }
    }

}
