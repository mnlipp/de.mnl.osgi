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
 * Indicates a problem when lazily loading properties of a {@link MavenResource}.
 */
public class MavenResourceException extends Exception {

    private static final long serialVersionUID = 1112064683351913549L;

    /**
     * Instantiates a new maven resource exception.
     */
    public MavenResourceException() {
        // Empty constructor
    }

    /**
     * Instantiates a new maven resource exception.
     *
     * @param message the message
     */
    public MavenResourceException(String message) {
        super(message);
    }

    /**
     * Instantiates a new maven resource exception.
     *
     * @param cause the cause
     */
    public MavenResourceException(Throwable cause) {
        super(cause);
    }

    /**
     * Instantiates a new maven resource exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public MavenResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Instantiates a new maven resource exception.
     *
     * @param message the message
     * @param cause the cause
     * @param enableSuppression the enable suppression
     * @param writableStackTrace the writable stack trace
     */
    public MavenResourceException(String message, Throwable cause,
            boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
