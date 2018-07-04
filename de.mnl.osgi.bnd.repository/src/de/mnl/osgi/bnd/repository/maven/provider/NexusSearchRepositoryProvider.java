/*
 * Bnd Nexus Search Plugin
 * Copyright (C) 2017  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.bnd.repository.maven.provider;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;
import org.osgi.util.promise.Promise;

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
import aQute.maven.api.Archive;
import aQute.service.reporter.Reporter;

/**
 * Provide a {@link NexusSearchOsgiRepository} as a bnd repository. The interesting
 * work is done by the {@link NexusSearchOsgiRepository}.
 */
public class NexusSearchRepositoryProvider extends BaseRepository 
	implements Repository, Plugin, RegistryPlugin, RepositoryPlugin, Refreshable {
//	private final static Logger logger = LoggerFactory.getLogger(
//			NexusSearchRepositoryProvider.class);
	private static final String MAVEN_REPO_LOCAL
		= System.getProperty("maven.repo.local", "~/.m2/repository");

	private boolean initialized = false;
	private File localRepo;
	private NexusSearchConfiguration configuration;
	private String name = "OssSonatype";
	private Registry registry = null;
	private Reporter reporter;
	private NexusSearchOsgiRepository osgiRepository = null;
	private URL server = null;
	private BridgeRepository bridge;
	
	@Override
	public void setProperties(Map<String, String> properties) throws Exception {
		configuration = Converter.cnv(NexusSearchConfiguration.class, properties);
		name = configuration.name("OssSonatype");
		server = new URL(configuration.server() != null 
				? configuration.server() : "https://oss.sonatype.org");
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
	public PutResult put(InputStream stream, PutOptions options) throws Exception {
		throw new IllegalStateException("Read-only repository");
	}

	@Override
	public boolean canWrite() {
		return false;
	}

	/**
	 * Performs initialization. Initialization must be delayed because the
	 * precise sequence of injecting dependencies seems to be undefined.
	 * @throws MalformedURLException 
	 */
	private synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		String queryString = configuration.query();
		Workspace workspace = registry.getPlugin(Workspace.class);
		HttpClient client = registry.getPlugin(HttpClient.class);
		File obrIndexFile = workspace.getFile(getLocation());
		File mvnReposFile = workspace.getFile(
				"cnf/cache/nexus-search-" + name + "-repositories.xml");
		localRepo = IO.getFile(configuration.local(MAVEN_REPO_LOCAL));
		try {
			osgiRepository = new NexusSearchOsgiRepository(
					name, server, localRepo, obrIndexFile, mvnReposFile, 
					queryString, reporter, client);
			bridge = new BridgeRepository(osgiRepository);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public File getRoot() throws Exception {
		return localRepo;
	}

	@Override
	public boolean refresh() throws Exception {
		init();
		if (!osgiRepository.refresh()) {
			return false;
		}
		bridge = new BridgeRepository(osgiRepository);
		for (RepositoryListenerPlugin listener : registry.getPlugins(RepositoryListenerPlugin.class)) {
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
		if (resource == null)
			return null;

		String name = resource.getInfo().name();
		Archive archive = Archive.valueOf(name);

		Promise<File> p = osgiRepository.mavenRepository().get(archive);

		if (listeners.length == 0)
			return p.getValue();

		new DownloadListenerPromise(reporter, name + ": get " + bsn + ";" + version, p, listeners);
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
		return "cnf/cache/nexus-search-" + name + ".xml";
	}

	@Override
	public Map<Requirement, Collection<Capability>> findProviders(
			Collection<? extends Requirement> requirements) {
		init();
		return osgiRepository.findProviders(requirements);
	}
}
