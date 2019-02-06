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

public interface NexusSearchConfiguration {

	/**
	 * The path to the local repository.
	 *
	 * @param deflt the default value
	 * @return the string
	 */
	// default "~/.m2/repository"
	String local(String deflt);

	/**
	 * Points to a file that is used as the cache. It will be in OSGi format.
	 *
	 * @param deflt the default value
	 * @return the result
	 */
	String location(String deflt);

	/**
	 * The name of the repo. Required.
	 * 
	 * @param deflt the default value
	 * @return the result
	 */
	String name(String deflt);

	/**
	 * The URL of the server.
	 * 
	 * @return the result
	 */
	String server();

	/**
	 * The query used to search the Nexus server.
	 * 
	 * @return the result
	 */
	String query();

	/**
	 * Allow transitive dependencies
	 * 
	 * @param deflt the default value
	 * @return the result
	 */
	boolean transitive(boolean deflt);

	/** 
	 * Number of search results to consider for each artifact.
	 * 
	 * @param dflt the default value
	 * @return the result
	 */
	int searchBreadth(int dflt);

	/** 
	 * Number of artfacts to return in one query.
	 * 
	 * @param dflt the default value
	 * @return the result
	 */
	int chunkSize(int dflt);
}
