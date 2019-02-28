/*
 * Bnd Nexus Search Plugin
 * Copyright (C) 2017  Michael N. Lipp
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
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.lib.strings.Strings;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;
import de.mnl.osgi.bnd.repository.maven.provider.NexusSearchNGResponseParser.ParseResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

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

/**
 * Provide an OSGi repository (a collection of {@link Resource}s, see 
 * {@link Repository}), filled with the results of performing a search 
 * on a Nexus server.
 */
public class NexusSearchOsgiRepository extends MavenOsgiRepository {

	private static final Logger logger = LoggerFactory.getLogger(
			NexusSearchOsgiRepository.class);
	private URL server;
	private String queryString;
	private int searchBreadth;
	private int chunkSize;
	private File localRepo = null;
	private File mvnReposFile;
	private Reporter reporter;
	private HttpClient client; 
	private MavenRepository mavenRepository;
	
	/**
	 * Create a new instance that uses the provided information/resources to perform
	 * its work.
	 *
	 * @param name the name
	 * @param server the url of the Nexus server
	 * @param localRepo the local Maven repository (cache)
	 * @param obrIndexFile the persistent representation of this repository's content
	 * @param mvnResposFile the mvn respos file
	 * @param queryString the query string
	 * @param searchBreadth the search breadth
	 * @param chunkSize the chunk size
	 * @param reporter a reporter for reporting the progress
	 * @param client an HTTP client for obtaining information from the Nexus server
	 * @throws Exception if a problem occurs
	 */
	public NexusSearchOsgiRepository (String name, URL server, File localRepo, 
			File obrIndexFile, File mvnResposFile, String queryString, int searchBreadth,
			int chunkSize, Reporter reporter, HttpClient client) throws Exception {
		super(name, obrIndexFile);
		this.server = server;
		this.queryString = queryString;
		this.searchBreadth = searchBreadth;
		this.chunkSize = chunkSize;
		this.localRepo = localRepo;
		this.mvnReposFile = mvnResposFile;
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

	private MavenRepository restoreRepository() throws Exception {
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
		return new MavenRepository(localRepo, name(), 
				releaseBackers, snapshotBackers, Processor.getExecutor(), reporter);
	}

	/**
	 * Refresh this repository's content.
	 * 
	 * @return true if refreshed, false if not refreshed possibly due to error
	 * @throws Exception if a problem occurs
	 */
	public boolean refresh() throws Exception {
		if (queryString == null) {
			return false;
		}
		NexusSearchNGResponseParser parser = new NexusSearchNGResponseParser();
		queryRepositories(parser);
		queryArtifacts(parser);
		// Repository information is obtained from both querying the repositories
		// (provides information about existing repositories) and from executing
		// the query (provides information about actually used repositories).
		mavenRepository = createMavenRepository(parser);
		Set<Revision> revsFound = parser.artifacts();
		Map<String,List<Revision>> revsByName = new HashMap<>();
		for (Revision rev: revsFound) {
			revsByName.computeIfAbsent(rev.group + ":" + rev.artifact, 
					k -> new ArrayList<>()).add(rev);
		}
		Set<Revision> filteredRevs = new HashSet<>();
		for (List<Revision> artifactRevs: revsByName.values()) {
			artifactRevs.sort(new MavenRevisionComparator().reversed());
			filteredRevs.addAll(artifactRevs.subList(
					0, Math.min(searchBreadth, artifactRevs.size())));
		}
		return refresh(mavenRepository, filteredRevs);
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
	private void queryArtifacts(NexusSearchNGResponseParser parser) 
			throws Exception {
		ExecutorCompletionService<QueryResult> exeSvc 
			= new ExecutorCompletionService<>(Executors.newFixedThreadPool(4));
		int executing = 0;
		for (String query: Strings.split(queryString)) {
			exeSvc.submit(new ArtifactQuery(parser, query, 1, chunkSize));
			executing += 1;
		}
		while (executing > 0) {
			QueryResult result = exeSvc.take().get();
			executing -= 1;
			ParseResult parsed = result.parsed;
			if (parsed.from > 1) {
				continue;
			}
			int from = parsed.from;
			while (from + chunkSize - 1 < parsed.totalCount) {
				from += chunkSize;
				exeSvc.submit(new ArtifactQuery(parser, result.query, from,
						chunkSize));
				executing += 1;
			}
		}
	}

	/**
	 * Execute the query. The result is stored in the parser.
	 */
	class ArtifactQuery implements Callable<QueryResult> {
		private NexusSearchNGResponseParser parser;
		private String query;
		private int from;
		private int count;
		
		public ArtifactQuery(NexusSearchNGResponseParser parser, String query, int from, int count) {
			super();
			this.parser = parser;
			this.query = query;
			this.from = from;
			this.count = count;
		}

		@Override
		public QueryResult call() throws Exception {
			int attempts = 0;
			QueryResult result = new QueryResult();
			result.query = query;
			while (true) {
				try {
					logger.debug("Searching {}", query);
					InputStream answer = client.build()
							.headers("User-Agent", "Bnd")
							.get(InputStream.class)
							.go(new URL(server, "service/local/lucene/search?"
									+ query + "&from=" + from + "&count=" + count));
					result.parsed = parser.parse(answer);
					answer.close();
					logger.debug("Got for {} results from {} to {} (of {})", 
							query, result.parsed.from, 
							result.parsed.from + result.parsed.count - 1,
							result.parsed.totalCount);
					if (result.parsed.tooManyResults 
					        && result.parsed.count < count) {
						logger.error("Too many results for {}, results were "
								+ "lost (chunk size too big)", query);
					}
					break;
				} catch (Exception e) {
					attempts += 1;
					if (attempts > 3)
						throw e;
					Thread.sleep(1000 * attempts);
				}
			}

			// List all revisions from query.
			for (Revision revision: parser.artifacts()) {
				logger.debug("Found {}", revision);					
			}
		
			return result;
		}
	}

	private class QueryResult {
		String query;
		ParseResult parsed;
	}
	
	private MavenRepository createMavenRepository(
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
		return new MavenRepository(localRepo, name(), 
				releaseBackers, snapshotBackers,
				Processor.getExecutor(), reporter);
	}
	
	/**
	 * Return the Maven repository object used to back this repository.
	 *
	 * @return the maven repository
	 * @throws Exception if a problem occurs
	 */
	public IMavenRepo mavenRepository() throws Exception {
		if (mavenRepository == null) {
			refresh();
		}
		return mavenRepository;
	}
}
