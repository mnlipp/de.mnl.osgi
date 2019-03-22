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

import java.util.List;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * A resource that is backed by a maven revision.
 */
public class MavenResource implements Resource {

    private final BoundRevision revision;
    private final Resource delegee;

    /**
     * Instantiates a new maven resource from the given data.
     *
     * @param revision the revision
     * @param resource the delegee
     */
    public MavenResource(BoundRevision revision, Resource resource) {
        this.revision = revision;
        this.delegee = resource;
    }

    /**
     * Gets the revision.
     *
     * @return the revision
     */
    public BoundRevision getRevision() {
        return revision;
    }

    @Override
    public List<Capability> getCapabilities(String namespace) {
        return delegee.getCapabilities(namespace);
    }

    @Override
    public List<Requirement> getRequirements(String namespace) {
        return delegee.getRequirements(namespace);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MavenResource) {
            return delegee.equals(((MavenResource) obj).delegee);
        }
        return delegee.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegee.hashCode();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return delegee.toString();
    }
}
