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

import aQute.maven.api.Revision;

import java.util.List;

import org.apache.maven.model.Dependency;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * A resource that is backed by a maven revision.
 */
public interface MavenResource {

    /**
     * Returns the revision. The revision is the only mandatory
     * information when creating a {@link MavenResource}. The other
     * informations that can be obtained can be made available
     * lazily, i.e. can be loaded on demand. 
     * <P>
     * This implies that a {@link MavenResource} can be created 
     * that does not exist in the backing maven repositories. 
     * If not obvious from the context, a call to {@link #boundRevision()}
     * can be used to verify that the resource exists.
     *
     * @return the revision
     */
    Revision revision();

    /**
     * Returns the bound revision.
     *
     * @return the revision
     * @throws MavenResourceException the maven resource exception
     */
    BoundRevision boundRevision() throws MavenResourceException;

    /**
     * Returns the mandatory maven compile and runtime dependencies. 
     * Dependencies in the POM that use variables are filtered, because
     * these variable cannot be resolved. 
     *
     * @return the dependencies
     * @throws MavenResourceException the maven resource exception
     */
    List<Dependency> dependencies() throws MavenResourceException;

    /**
     * Gets the underlying "ordinary" resource.
     *
     * @return the resource
     * @throws MavenResourceException the maven resource exception
     */
    Resource asResource() throws MavenResourceException;

    /**
     * Gets the capabilities from the given namespace.
     *
     * @param namespace the namespace
     * @return the capabilities
     * @throws MavenResourceException the maven resource exception
     */
    List<Capability> getCapabilities(String namespace)
            throws MavenResourceException;

    /**
     * Gets the requirements from the given namespace.
     *
     * @param namespace the namespace
     * @return the requirements
     * @throws MavenResourceException the maven resource exception
     */
    List<Requirement> getRequirements(String namespace)
            throws MavenResourceException;

    /**
     * A {@link MavenResource} can be compared with another maven resource.
     * In this case the objects are considered equal if their revisions 
     * are equal. A {@link MavenResource} can also be compared with 
     * another {@link Resource}. In this case, equality will be checked 
     * between {@link MavenResource#asResource()} and the other resource 
     * (see {@link Resource#equals(Object)}). 
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    boolean equals(Object obj);

    /**
     * The hash code is defined by the revision.
     *
     * @return the int
     */
    @Override
    int hashCode();

    @Override
    String toString();

}
