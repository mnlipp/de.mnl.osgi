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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;

/**
 * Provide an OSGi repository (a collection of {@link Resource}s, see 
 * {@link Repository}), filled with the results of searching specific
 * artifacts.
 */
public class MavenCoordinatesOsgiRepository extends MavenOsgiRepository {

	private final static Logger logger = LoggerFactory.getLogger(
			MavenCoordinatesOsgiRepository.class);
	private List<URL> releaseUrls;
	private List<URL> snapshotUrls;
	private List<String> coordinates;
	private File localRepo = null;
	private Reporter reporter;
	private HttpClient client; 
	private MavenRepository mavenRepository;
	
	/**
	 * Create a new instance that uses the provided information/resources to perform
	 * its work.
	 * 
	 * @param releaseUrl the release URL
	 * @param snapshotUrl the snapshot URL
	 * @param localRepo the local Maven repository (cache)
	 * @param obrIndexFile the persistent representation of this repository's content
	 * @param query the query to execute
	 * @param reporter a reporter for reporting the progress
	 * @param client an HTTP client for obtaining information from the Nexus server
	 */
	public MavenCoordinatesOsgiRepository (String name, List<URL> releaseUrls, List<URL> snapshotUrls,
			List<String> coordinates, File localRepo, File obrIndexFile, 
			Reporter reporter, HttpClient client)
			throws Exception {
		super(name, obrIndexFile);
		this.releaseUrls = releaseUrls;
		this.snapshotUrls = snapshotUrls;
		this.coordinates = coordinates;
		this.localRepo = localRepo;
		this.reporter = reporter;
		this.client = client;
		
		// load results from previous execution.
		mavenRepository = createMavenRepository();
		if (!location().exists() || !location().isFile()) {
			refresh();
		} else {
			try (XMLResourceParser parser = new XMLResourceParser(location())) {
				List<Resource> resources = parser.parse();
				addAll(resources);
			}
		}
	}

	/**
	 * Refresh this repository's content.
	 * 
	 * @return true if refreshed, false if not refreshed possibly due to error
	 * @throws Exception
	 */
	public boolean refresh() throws Exception {
		if (coordinates == null) {
			return false;
		}
		List<Revision> startRevisions = coordinates.stream()
				.map(an -> Revision.valueOf(an)).collect(Collectors.toList());
		// List all revisions from configuration.
		for (Revision revision: startRevisions) {
			logger.debug("Found {}", revision);					
		}
		return refresh(mavenRepository, startRevisions);
	}

	private MavenRepository createMavenRepository() throws Exception {
		// Create repository from URLs
		List<MavenBackingRepository> releaseBackers = new ArrayList<>();
		for(URL url: releaseUrls) {
			releaseBackers.addAll(MavenBackingRepository.create(
					url.toString(), reporter, localRepo, client));
		}
		List<MavenBackingRepository> snapshotBackers = new ArrayList<>();
		for(URL url: snapshotUrls) {
			snapshotBackers.addAll(MavenBackingRepository.create(
					url.toString(), reporter, localRepo, client));
		}
		return new MavenRepository(localRepo, name(), 
				releaseBackers, snapshotBackers,
				Processor.getExecutor(), reporter);
	}
	
	/**
	 * Return the Maven repository object used to back this repository.
	 * 
	 * @return
	 */
	public IMavenRepo mavenRepository() throws Exception {
		if (mavenRepository == null) {
			refresh();
		}
		return mavenRepository;
	}
}
