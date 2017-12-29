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

public interface NexusSearchConfiguration {

	/**
	 * The path to the local repository
	 */
	// default "~/.m2/repository"
	String local(String deflt);

	/**
	 * Points to a file that is used as the cache. It will be in OSGi format.
	 */
	String location(String deflt);

	/**
	 * The name of the repo. Required.
	 */
	String name(String deflt);

	/**
	 * The URL of the server.
	 */
	String server();

	/**
	 * The query used to search the Nexus server.
	 */
	String query();

	/**
	 * Allow transitive dependencies
	 */
	boolean transitive(boolean deflt);

}
