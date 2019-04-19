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

/**
 * Some classes that may be helpful for any repository provider 
 * implementation that uses (backing) maven repositories.
 * <P>
 * This package mixes classes from bnd's maven repository libraries
 * with the standard maven libraries, which is far from an ideal
 * situation. However, the bnd 
 * {@link aQute.maven.provider.MavenBackingRepository} class provides 
 * an easy access to remote repositories. The standard
 * maven repository provider is much harder to use and pulls in a 
 * ridiculous number of dependencies. In addition, the bnd classes 
 * {@link aQute.maven.api.Program} and {@link aQute.maven.api.Archive}
 * add some type safety not provided by the maven libraries (though
 * the latter is a misnomer, because an {@link aQute.maven.api.Archive}
 * can also represent a POM).
 * <P>
 * However, the evaluation of the information in the POM by bnd isn't
 * perfect and therefore done using the maven libraries, which results
 * in the afore mentioned mixture. The goal for further development
 * of this package is to depend less on bnd's classes and use
 * maven standard libraries whereever possible.
 */
package de.mnl.osgi.bnd.maven;