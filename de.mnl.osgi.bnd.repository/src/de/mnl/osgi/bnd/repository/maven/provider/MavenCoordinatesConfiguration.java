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

public interface MavenCoordinatesConfiguration {

	/**
	 * The path to the local repository.
	 *
	 * @param deflt the default value
	 * @return the result
	 */
	// default "~/.m2/repository"
	String local(String deflt);

	/**
	 *
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
	 * The url to the remote release repository.
	 * 
	 * @return the result
	 */
	String releaseUrls();

	/**
	 * The url to the remote snapshot repository. If this is not specified,
	 * it falls back to the release repository or just local if this is also
	 * not specified.
	 * 
	 * @return the result
	 */
	String snapshotUrls();

	/**
	 * The coordinates to get.
	 * 
	 * @return the result
	 */
	String coordinates();

	/**
	 * Allow transitive dependencies
	 * 
	 * @param deflt the default value
	 * @return the result
	 */
	boolean transitive(boolean deflt);

}
