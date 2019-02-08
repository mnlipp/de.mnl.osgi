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

import aQute.maven.api.Program;
import aQute.maven.api.Revision;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the XML document returned by a Nexus server in response to a
 * <code>lucene/search</code> request.
 */
public class NexusSearchNGResponseParser {
	private static final Logger logger = LoggerFactory.getLogger(
			NexusSearchNGResponseParser.class);
	private Set<Revision> artifacts 
		= Collections.newSetFromMap(new ConcurrentHashMap<>());
	private Map<String,RepoInfo> repoInfos = new ConcurrentHashMap<>();

	/**
	 * Returns the reported snapshot repositories.
	 * 
	 * @return the result
	 * @throws MalformedURLException if the URL is malformed
	 */
	public Set<URL> snapshotRepositories() throws MalformedURLException {
		Set<URL> result = new HashSet<>();
		Iterator<RepoInfo> itr = repoInfos.values().stream().filter(
				ri -> ri.referenced && ri.repoPolicy == RepoPolicy.Snapshot).iterator();
		while (itr.hasNext()) {
			result.add(itr.next().contentResourceUri.toURL());
		}
		return result;
	}
	
	/**
	 * Returns the reported release repositories.
	 * 
	 * @return the result
	 * @throws MalformedURLException if the URL is malformed
	 */
	public Set<URL> releaseRepositories() throws MalformedURLException {
		Set<URL> result = new HashSet<>();
		Iterator<RepoInfo> itr = repoInfos.values().stream().filter(
				ri -> ri.referenced && ri.repoPolicy == RepoPolicy.Release).iterator();
		while (itr.hasNext()) {
			result.add(itr.next().contentResourceUri.toURL());
		}
		return result;
	}
	
	/**
	 * Returns the reported artifacts.
	 * 
	 * @return the result
	 */
	public Set<Revision> artifacts() {
		return Collections.unmodifiableSet(artifacts); 
	}
	
	/**
	 * Parse the result return from the Nexus server. The outcome will be reflected in
	 * the attributes.
	 *
	 * @param in the stream with result data
	 * @return the parse result
	 * @throws Exception if a problem occurs
	 */
	public ParseResult parse(InputStream in) throws Exception {
		XMLEventReader eventReader = XMLInputFactory.newInstance()
				.createXMLEventReader(in);
		ParseResult result = new ParseResult();
		while(eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isStartElement()) {
				StartElement startElement = event.asStartElement();
				switch (startElement.getName().getLocalPart()) {
				case "totalCount":
					result.totalCount = Integer
						.parseInt(parseCharacters(eventReader));
					break;
				case "from":
					result.from = Integer
						.parseInt(parseCharacters(eventReader));
					break;
				case "count":
					result.count = Integer
						.parseInt(parseCharacters(eventReader));
					break;
				case "tooManyResults":
					result.tooManyResults = Boolean
						.parseBoolean(parseCharacters(eventReader));
					break;
				// Repositories (item by item)
				case "repositories-item":
					parseRepositoryData(eventReader);
					break;
				// Repository definitions
				case "org.sonatype.nexus.rest.model.NexusNGRepositoryDetail":
					parseRepositoryDetail(eventReader);
					break;
				// Artifacts
				case "artifact":
					parseArtifact(eventReader);
					result.artifactsInResult += 1;
					break;
				}
			}
		}
		return result;
	}
	
	/**
	 * Parse a repository detail section. The results are added to attributes
	 * {@link #snapshotRepositories} and {@link #releaseRepositories} 
	 * 
	 * @param eventReader
	 * @throws Exception
	 */
	private void parseRepositoryData(XMLEventReader eventReader) 
			throws Exception {
		RepoInfo repoInfo = new RepoInfo();
		boolean skip = true;
		while(eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isEndElement() && event.asEndElement().getName()
					.getLocalPart().equals("repositories-item")) {
				if (!skip && repoInfo.repoPolicy != RepoPolicy.Unknown) {
					repoInfos.put(repoInfo.id, repoInfo);
				}
				break;
			}
			if (event.isStartElement()) {
				switch(event.asStartElement().getName().getLocalPart()) {
				case "id":
					repoInfo.id = parseCharacters(eventReader);
					break;
				case "contentResourceURI":
					repoInfo.contentResourceUri = new URI(parseCharacters(eventReader));
					break;
				case "format":
					if(parseCharacters(eventReader).equals("maven2")) {
						skip = false;
					}
					break;
				case "repoPolicy":
					switch(parseCharacters(eventReader)) {
					case "SNAPSHOT":
						repoInfo.repoPolicy = RepoPolicy.Snapshot;
						break;
					case "RELEASE":
						repoInfo.repoPolicy = RepoPolicy.Release;
						break;
					}
					break;
				}
			}
		}
	}

	/**
	 * Parse a repository detail section. The results are added to attributes
	 * {@link #snapshotRepositories} and {@link #releaseRepositories} 
	 * 
	 * @param eventReader
	 * @throws Exception
	 */
	private void parseRepositoryDetail(XMLEventReader eventReader) 
			throws Exception {
		boolean skip = false;
		while(eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isEndElement() && event.asEndElement().getName()
					.getLocalPart().equals(
							"org.sonatype.nexus.rest.model.NexusNGRepositoryDetail")) {
				break;
			}
		}
		if (skip) {
			return;
		}
	}

	/**
	 * Parse an artifact description and return the result as a 
	 * set of {@link Revision}s.
	 * 
	 * @param eventReader the input
	 * @return the result
	 * @throws XMLStreamException 
	 */
	private void parseArtifact(XMLEventReader eventReader) 
			throws XMLStreamException {
		String groupId = null;
		String artifactId = null;
		String version = null;
		boolean foundClassifierInArtifactLink = false;
		boolean foundBinJar = false;
		
		while(eventReader.hasNext()) {
			XMLEvent event = eventReader.nextEvent();
			if (event.isEndElement()) {
				switch (event.asEndElement().getName().getLocalPart()) {
				case "artifact":
					if (foundBinJar) {
						artifacts.add(Program.valueOf(groupId, artifactId).version(version));
					}
					return;
				case "artifactLink":
					if (!foundClassifierInArtifactLink) {
						foundBinJar = true;
					}
					break;
				}
			}
			if (event.isStartElement()) {
				switch(event.asStartElement().getName().getLocalPart()) {
				case "repositoryId":
					String repositoryId = parseCharacters(eventReader);
					RepoInfo info = repoInfos.get(repositoryId);
					if (info == null) {
						logger.warn("Inconsistent search result: reference to "
								+ "non-existant repository with id " + repositoryId + ".");
						break;
					}
					info.referenced = true;
					break;
				case "groupId":
					groupId = parseCharacters(eventReader);
					break;
				case "artifactId":
					artifactId = parseCharacters(eventReader);
					break;
				case "version":
					version = parseCharacters(eventReader);
					break;
				case "artifactLink":
					foundClassifierInArtifactLink = false;
					break;
				case "classifier":
					foundClassifierInArtifactLink = true;
					break;
				}
			}
		}
	}

	
	private String parseCharacters(XMLEventReader eventReader) 
			throws XMLStreamException {
		StringBuilder sb = new StringBuilder();
		while (eventReader.peek().isCharacters()) {
			XMLEvent event = eventReader.nextEvent();
			sb.append(event.asCharacters().getData());
		}
		return sb.toString();
	}
	
	private enum RepoPolicy { Unknown, Snapshot, Release };
	
	private class RepoInfo {
		public String id;
		public RepoPolicy repoPolicy = RepoPolicy.Unknown;
		public URI contentResourceUri;
		boolean referenced = false;
	}
	
	public class ParseResult {
		public int totalCount;
		public int from;
		public int count;
		public boolean tooManyResults;
		public int artifactsInResult;
	}
}
