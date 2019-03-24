/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019  Michael N. Lipp
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

package de.mnl.osgi.bnd.maven;

import aQute.maven.api.IPom.Dependency;
import aQute.maven.api.Revision;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * A resource that is backed by a maven revision.
 */
public interface MavenResource {

    /**
     * Returns the revision.
     *
     * @return the revision
     */
    Revision revision();

    /**
     * Returns the bound revision.
     *
     * @return the revision
     * @throws IOException Signals that an I/O exception has occurred.
     */
    BoundRevision boundRevision() throws IOException;

    /**
     * Returns the dependencies.
     *
     * @return the dependencies
     * @throws IOException Signals that an I/O exception has occurred.
     */
    Set<Dependency> dependencies() throws IOException;

    /**
     * Gets the underlying "ordinary" resource.
     *
     * @return the resource
     * @throws IOException Signals that an I/O exception has occurred.
     */
    Resource asResource() throws IOException;

    /**
     * Gets the capabilities from the given namespace.
     *
     * @param namespace the namespace
     * @return the capabilities
     * @throws IOException Signals that an I/O exception has occurred.
     */
    List<Capability> getCapabilities(String namespace) throws IOException;

    /**
     * Gets the requirements from the given namespace.
     *
     * @param namespace the namespace
     * @return the requirements
     * @throws IOException Signals that an I/O exception has occurred.
     */
    List<Requirement> getRequirements(String namespace) throws IOException;

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    @Override
    String toString();

}