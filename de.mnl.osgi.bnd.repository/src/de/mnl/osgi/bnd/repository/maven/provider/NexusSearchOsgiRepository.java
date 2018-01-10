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

import static aQute.bnd.osgi.repository.BridgeRepository.addInformationCapability;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;

import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.maven.api.Archive;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.IPom;
import aQute.maven.api.MavenScope;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.api.IPom.Dependency;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;

/**
 * Provide an OSGi repository (a collection of {@link Resource}s, see 
 * {@link Repository}), filled with the results of performing a search 
 * on a Nexus server.
 */
public class NexusSearchOsgiRepository extends ResourcesRepository {

	private final static Logger logger = LoggerFactory.getLogger(
			NexusSearchOsgiRepository.class);
	private String name;
	private URL server;
	private File localRepo = null;
	private File obrIndexFile;
	private File mvnReposFile;
	private String queryString;
	private Reporter reporter;
	private HttpClient client; 
	private IMavenRepo mavenRepository;
	private Set<Revision> toBeProcessed = new HashSet<>();
	private Set<Revision> processing = new HashSet<>();
	private Set<Revision> processed = new HashSet<>();
	
	/**
	 * Create a new instance that uses the provided information/resources to perform
	 * its work.
	 * 
	 * @param server the url of the Nexus server
	 * @param localRepo the local Maven repository (cache)
	 * @param obrIndexFile the persistent representation of this repository's content
	 * @param query the query to execute
	 * @param reporter a reporter for reporting the progress
	 * @param client an HTTP client for obtaining information from the Nexus server
	 */
	public NexusSearchOsgiRepository (String name, URL server, File localRepo, 
			File obrIndexFile, File mvnResposFile, 
			String queryString, Reporter reporter, HttpClient client)
			throws Exception {
		this.name = name;
		this.server = server;
		this.localRepo = localRepo;
		this.obrIndexFile = obrIndexFile;
		this.mvnReposFile = mvnResposFile;
		this.queryString = queryString;
		this.reporter = reporter;
		this.client = client;
		
		// load results from previous execution.
		mavenRepository = restoreRepository();
		if (mavenRepository == null 
				|| !location().exists() || !location().isFile()) {
			refresh();
		} else {
			try (XMLResourceParser parser = new XMLResourceParser(location())) {
				List<Resource> resources = parser.parse();
				addAll(resources);
			}
		}
	}

	private IMavenRepo restoreRepository() throws Exception {
		if (!mvnReposFile.exists()) {
			return null;
		}
		List<MavenBackingRepository> releaseBackers = new ArrayList<>();
		List<MavenBackingRepository> snapshotBackers = new ArrayList<>();
		List<MavenBackingRepository> backers = null;
		XMLEventReader xmlIn = XMLInputFactory.newFactory().createXMLEventReader(
				new FileInputStream(mvnReposFile));
		while (xmlIn.hasNext()) {
			XMLEvent event = xmlIn.nextEvent();
			if (event.getEventType() != XMLStreamConstants.START_ELEMENT) {
				continue;
			}
			switch(event.asStartElement().getName().getLocalPart()) {
			case "releaseUrls":
				backers = releaseBackers;
				break;
			case "snapshotUrls":
				backers = snapshotBackers;
				break;
			case "url":
				do {
					event = xmlIn.nextEvent();
				} while (event.getEventType() != XMLStreamConstants.CHARACTERS);
				backers.addAll(MavenBackingRepository.create(
						event.asCharacters().getData(), reporter, localRepo, client));
				break;
			}
		}
		xmlIn.close();
		return new MavenRepository(localRepo, name, 
				releaseBackers, snapshotBackers, Processor.getExecutor(), reporter, null);
	}

	/**
	 * Return the name of this repository.
	 * 
	 * @return the name;
	 */
	public String name() {
		return name;
	}
	
	/**
	 * Return the representation of this repository in the local file system.
	 * 
	 * @return the location
	 */
	public File location() {
		return obrIndexFile;
	}

	/**
	 * Refresh this repository's content.
	 * 
	 * @return true if refreshed, false if not refreshed possibly due to error
	 * @throws Exception
	 */
	public boolean refresh() throws Exception {
		if (queryString == null) {
			return false;
		}
		set(new HashSet<>()); // Clears this repository
		NexusSearchNGResponseParser parser = new NexusSearchNGResponseParser();
		queryRepositories(parser);
		queryArtifacts(parser);
		toBeProcessed.addAll(parser.artifacts());
		// Repository information is obtained from both querying the repositories
		// (provides information about existing repositories) and from executing
		// the query (provides information about actually used repositories).
		mavenRepository = createMavenRepository(parser);
		Set<Resource> collectedResources = Collections.newSetFromMap(new ConcurrentHashMap<>());
		synchronized (this) {
			while (true) {
				if (toBeProcessed.isEmpty() && processing.isEmpty()) {
					break;
				}
				if(!toBeProcessed.isEmpty() && processing.size() < 4) {
					Revision rev = toBeProcessed.iterator().next();
					toBeProcessed.remove(rev);
					processing.add(rev);
					Processor.getScheduledExecutor().submit(
							new RevisionProcessor(collectedResources, rev));
				}
				wait();
			}
		}
		processed.clear();
		// Set this repository's content to the results...
		addAll(collectedResources);
		// ... and persist the content.
		XMLResourceGenerator generator = new XMLResourceGenerator();
		generator.resources(getResources());
		generator.name(name);
		generator.save(obrIndexFile);
		return true;
	}

	/**
	 * Obtain information about the repositories that exist on the server.
	 * The information is stored in the parser.
	 * 
	 * @param parser the parser
	 * @throws Exception
	 */
	private void queryRepositories(NexusSearchNGResponseParser parser) 
			throws Exception {
		int attempts = 0;
		while (true) {
			try {
				logger.debug("Getting repositories");
				InputStream result = client.build()
						.headers("User-Agent", "Bnd")
						.get(InputStream.class)
						.go(new URL(server, "service/local/repositories"));
				parser.parse(result);
				result.close();
				break;
			} catch (Exception e) {
				attempts += 1;
				if (attempts > 3)
					throw e;
				Thread.sleep(1000 * attempts);
			}
		}
	}
	
	/**
	 * Execute the query. The result is stored in the parser.
	 * 
	 * @param parser the parser
	 * @throws Exception
	 */
	private void queryArtifacts(NexusSearchNGResponseParser parser) throws Exception {
		int attempts = 0;
		while (true) {
			try {
				logger.debug("Searching {}", queryString);
				InputStream result = client.build()
						.headers("User-Agent", "Bnd")
						.get(InputStream.class)
						.go(new URL(server, "service/local/lucene/search?" + queryString));
				parser.parse(result);
				result.close();
				break;
			} catch (Exception e) {
				attempts += 1;
				if (attempts > 3)
					throw e;
				Thread.sleep(1000 * attempts);
			}
		}

		// Add all revisions from query.
		for (Revision revision: parser.artifacts()) {
			logger.debug("Found {}", revision);					
		}
	}

	private IMavenRepo createMavenRepository(
			NexusSearchNGResponseParser parser) throws Exception {
		// Create repository from URLs
		XMLStreamWriter xmlOut = XMLOutputFactory.newFactory().createXMLStreamWriter(
				new FileOutputStream(mvnReposFile));
		xmlOut.writeStartDocument();
		xmlOut.writeStartElement("repositories");
		xmlOut.writeStartElement("repository");
		xmlOut.writeStartElement("releaseUrls");
		List<MavenBackingRepository> releaseBackers = new ArrayList<>();
		for (URL repoUrl: parser.releaseRepositories()) {
			xmlOut.writeStartElement("url");
			xmlOut.writeCharacters(repoUrl.toString());
			xmlOut.writeEndElement();
			releaseBackers.addAll(MavenBackingRepository.create(
					repoUrl.toString(), reporter, localRepo, client));
		}
		xmlOut.writeEndElement();
		xmlOut.writeStartElement("snapshotUrls");
		List<MavenBackingRepository> snapshotBackers = new ArrayList<>();
		for (URL repoUrl: parser.snapshotRepositories()) {
			xmlOut.writeStartElement("url");
			xmlOut.writeCharacters(repoUrl.toString());
			xmlOut.writeEndElement();
			snapshotBackers.addAll(MavenBackingRepository.create(
					repoUrl.toString(), reporter, localRepo, client));
		}
		xmlOut.writeEndElement();
		xmlOut.writeEndElement();
		xmlOut.writeEndElement();
		xmlOut.writeEndDocument();
		xmlOut.close();
		return new MavenRepository(localRepo, name, 
				releaseBackers, snapshotBackers,
				Processor.getExecutor(), reporter, null);
	}
	
	/**
	 * A callable (allows it to throw an exception) that processes a single
	 * Revision.
	 */
	private class RevisionProcessor implements Callable<Void> {
		private Revision revision;
		private Set<Resource> collectedResources;
		
		public RevisionProcessor(Set<Resource> collectedResources, Revision revision) {
			this.collectedResources = collectedResources;
			this.revision = revision;
		}

		/**
		 * Get this revision's dependencies from the POM and enqueue them as
		 * to be processed (unless processed already) and create an entry
		 * for the resource in this repository.
		 */
		@Override
		public Void call() throws Exception {
			try {
				// Get and add this revision's OSGi information (refreshes snapshots)
				Archive archive = mavenRepository.getResolvedArchive(revision, "jar", "");
				if (archive != null) {
					// Get POM for dependencies
					IPom pom = mavenRepository.getPom(archive.getRevision());
					if (pom != null) {
						// Get pom and add all dependencies as to be processed.
						addDependencies(pom);
					}
					Resource resource = parseResource(archive);
					if (resource != null) {
						collectedResources.add(resource);
					}
				}
				return null;
			} finally {
				// We're done witht his revision.
				synchronized (NexusSearchOsgiRepository.this) {
					processing.remove(revision);
					processed.add(revision);
					NexusSearchOsgiRepository.this.notifyAll();
				}
			}
		}

		private void addDependencies(IPom pom) {
			try {
				Map<Program,Dependency> deps = new LinkedHashMap<>();
				deps.putAll(pom.getDependencies(MavenScope.compile, false));
				deps.putAll(pom.getDependencies(MavenScope.runtime, false));
				synchronized (NexusSearchOsgiRepository.this) {
					for (Map.Entry<Program, Dependency> entry : deps.entrySet()) {
						Revision rev = entry.getKey().version(entry.getValue().version);
						if (!toBeProcessed.contains(rev) && !processing.contains(rev) 
								&& !processed.contains(rev)) {
							toBeProcessed.add(rev);
							logger.debug("Added as dependency {}", rev);
						}
						NexusSearchOsgiRepository.this.notifyAll();
					}
				}
			} catch (Exception e) {
				logger.error("Failed to get POM of " + revision + ".", e);
			}
		}
	}
	
	private Resource parseResource(Archive archive) throws Exception {
		ResourceBuilder rb = new ResourceBuilder();
		try {
			File binary = mavenRepository.get(archive).getValue();
			rb.addFile(binary, binary.toURI());
			addInformationCapability(rb, archive.toString(), 
					archive.getRevision().toString(), null);
		} catch (Exception e) {
			return null;
		}
		Resource resource = rb.build();
		return resource;
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
