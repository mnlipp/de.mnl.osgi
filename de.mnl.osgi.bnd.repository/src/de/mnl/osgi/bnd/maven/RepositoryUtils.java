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

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The Class Utils.
 */
public final class RepositoryUtils {

    public static final Pattern LIST_ITEM_SEPARATOR
        = Pattern.compile("\\s*,\\s*");

    private RepositoryUtils() {
    }

    /**
     * Itemize list.
     *
     * @param list the list
     * @return the stream
     */
    public static Stream<String> itemizeList(String list) {
        if (list == null) {
            return Stream.empty();
        }
        return LIST_ITEM_SEPARATOR.splitAsStream(list);
    }
}
