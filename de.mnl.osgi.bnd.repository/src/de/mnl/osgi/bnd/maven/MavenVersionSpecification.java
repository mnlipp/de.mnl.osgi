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

/**
 * The common base class of {@link MavenVersion} and {@link MavenVersionRange}.
 */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class MavenVersionSpecification {

    /**
     * Checks if is the provided version representation is a range.
     * If {@code version} is {@code} null it is considered to be
     * the "all inclusive range" ("[0,)").
     *
     * @param version the version
     * @return true, if is range
     */
    public static boolean isRange(String version) {
        if (version == null) {
            return true;
        }
        version = version.trim();
        return version.startsWith("[") || version.startsWith("(");
    }

    /**
     * Creates a maven version specification from the given arguments.
     *
     * @param program the program
     * @param version the version
     */
    public static MavenVersionSpecification from(String version) {
        if (isRange(version)) {
            return new MavenVersionRange(version);
        } else {
            return new MavenVersion(version);
        }
    }
}
