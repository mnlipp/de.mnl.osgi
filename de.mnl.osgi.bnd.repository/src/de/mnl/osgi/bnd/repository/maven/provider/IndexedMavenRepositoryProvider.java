/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019-2021  Michael N. Lipp
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

import aQute.bnd.build.Workspace;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.BaseRepository;
import aQute.bnd.osgi.repository.BridgeRepository;
import aQute.bnd.osgi.repository.BridgeRepository.ResourceInfo;
import aQute.bnd.service.Plugin;
import aQute.bnd.service.Refreshable;
import aQute.bnd.service.Registry;
import aQute.bnd.service.RegistryPlugin;
import aQute.bnd.service.RepositoryListenerPlugin;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.util.repository.DownloadListenerPromise;
import aQute.bnd.version.Version;
import aQute.lib.converter.Converter;
import aQute.lib.io.IO;
import aQute.libg.reporter.slf4j.Slf4jReporter;
import aQute.maven.api.Archive;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.maven.RepositoryUtils;
import de.mnl.osgi.bnd.repository.maven.idxmvn.IndexedMavenConfiguration;
import de.mnl.osgi.bnd.repository.maven.idxmvn.IndexedMavenRepository;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;
import org.osgi.util.promise.Promise;

/**
 * Maintains an index of a subset of one or more maven repositories
 * and provides it as an OSGi repository.
 */
public class IndexedMavenRepositoryProvider extends BaseRepository
        implements Repository, Plugin, RegistryPlugin, RepositoryPlugin,
        Refreshable {
    private static final String MAVEN_REPO_LOCAL
        = System.getProperty("maven.repo.local", "~/.m2/repository");

    private boolean initialized;
    private IndexedMavenConfiguration configuration;
    private String name = "Indexed Maven";
    private String location;
    private Registry registry;
    private Reporter reporter
        = new Slf4jReporter(IndexedMavenRepositoryProvider.class);
    private IndexedMavenRepository osgiRepository;
    private BridgeRepository bridge;
    private boolean logIndexing;

    @Override
    @SuppressWarnings({ "PMD.UseLocaleWithCaseConversions", "restriction" })
    public void setProperties(Map<String, String> properties) throws Exception {
        configuration
            = Converter.cnv(IndexedMavenConfiguration.class, properties);
        name = configuration.name(name);
        location = configuration.location(
            "cnf/" + name.toLowerCase().replace(' ', '-').replace('/', ':'));
        logIndexing = configuration.logIndexing();
    }

    @Override
    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    @Override
    public void setReporter(Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PutResult put(InputStream stream, PutOptions options)
            throws Exception {
        throw new IllegalStateException("Read-only repository");
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    /**
     * Performs initialization. Initialization must be delayed because the
     * precise sequence of injecting dependencies seems to be undefined.
     * 
     * @throws MalformedURLException 
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidThrowingRawExceptionTypes" })
    private void init() {
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;
            Workspace workspace = registry.getPlugin(Workspace.class);
            HttpClient client = registry.getPlugin(HttpClient.class);
            File indexDb = workspace.getFile(getLocation());
            File localRepo = IO.getFile(configuration.local(MAVEN_REPO_LOCAL));
            try {
                Thread.currentThread()
                    .setContextClassLoader(getClass().getClassLoader());
                osgiRepository = new IndexedMavenRepository(name,
                    RepositoryUtils.itemizeList(configuration.releaseUrls())
                        .map(ru -> stringToUrl(ru))
                        .collect(Collectors.toList()),
                    RepositoryUtils.itemizeList(configuration.snapshotUrls())
                        .map(ru -> stringToUrl(ru))
                        .collect(Collectors.toList()),
                    localRepo, indexDb, reporter, client, logIndexing);
                bridge = new BridgeRepository(osgiRepository);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private URL stringToUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public File getRoot() throws Exception {
        return osgiRepository.location();
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public boolean refresh() throws Exception {
        init();
        if (!osgiRepository.refresh()) {
            return false;
        }
        bridge = new BridgeRepository(osgiRepository);
        for (RepositoryListenerPlugin listener : registry
            .getPlugins(RepositoryListenerPlugin.class)) {
            try {
                listener.repositoryRefreshed(this);
            } catch (Exception e) {
                reporter.exception(e, "Updating listener plugin %s", listener);
            }
        }
        return true;
    }

    @Override
    public File get(String bsn, Version version, Map<String, String> properties,
            DownloadListener... listeners) throws Exception {
        init();
        ResourceInfo resource = bridge.getInfo(bsn, version);
        if (resource == null) {
            return null;
        }
        String name = resource.getInfo().name();
        Archive archive = Archive.valueOf(name);

        Promise<File> prmse
            = osgiRepository.mavenRepository().retrieve(archive);

        if (listeners.length == 0) {
            return prmse.getValue();
        }
        new DownloadListenerPromise(reporter,
            name + ": get " + bsn + ";" + version, prmse, listeners);
        return osgiRepository.mavenRepository().toLocalFile(archive);
    }

    @Override
    public List<String> list(String pattern) throws Exception {
        init();
        return bridge.list(pattern);
    }

    @Override
    public SortedSet<Version> versions(String bsn) throws Exception {
        init();
        return bridge.versions(bsn);
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public Map<Requirement, Collection<Capability>> findProviders(
            Collection<? extends Requirement> requirements) {
        init();
        return osgiRepository.findProviders(requirements);
    }
}
