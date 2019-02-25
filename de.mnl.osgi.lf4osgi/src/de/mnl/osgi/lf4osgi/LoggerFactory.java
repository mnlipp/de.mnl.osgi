/*
 * Copyright (C) 2019 Michael N. Lipp (http://www.mnl.de)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.mnl.osgi.lf4osgi;

import de.mnl.osgi.lf4osgi.core.DefaultLoggerGroup;
import de.mnl.osgi.lf4osgi.core.LoggerCatalogue;

import org.osgi.framework.Bundle;

/**
 * The factory that supplies the OSGi {@link Logger}s from static
 * methods.
 */
@SuppressWarnings({ "PMD.ClassNamingConventions", "PMD.UseUtilityClass" })
public class LoggerFactory {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final LoggerCatalogue<
            DefaultLoggerGroup<Lf4OsgiLogger>> catalogue
                = new LoggerCatalogue<>(
                    b -> new DefaultLoggerGroup<Lf4OsgiLogger>(b,
                        (g, n) -> new Lf4OsgiLogger(g, n)));

    /**
     * Gets a logger with the given name.
     *
     * @param name the name
     * @return the logger
     */
    public static Logger getLogger(String name) {
        Bundle bundle = LoggerCatalogue
            .findBundle(LoggerFactory.class.getName()).orElse(null);
        return catalogue.getLoggerGoup(bundle).computeIfAbsent(name,
            (c, n) -> new Lf4OsgiLogger(c, n));
    }

    /**
     * Gets a logger, using the class name as name for the logger.
     *
     * @param clazz the clazz
     * @return the logger
     */
    public static Logger getLogger(Class<?> clazz) {
        String name = clazz.getName();
        Bundle bundle = LoggerCatalogue
            .findBundle(LoggerFactory.class.getName()).orElse(null);
        return catalogue.getLoggerGoup(bundle).computeIfAbsent(name,
            (c, n) -> new Lf4OsgiLogger(c, n));
    }

    /**
     * Gets a logger with the given name for the given bundle.
     * <P>
     * If the logging bundle happens to be known in the context in which 
     * {@code getLogger} is called, this method should be preferred over 
     * {@link #getLogger(String)} because the latter implies a small
     * overhead for finding out the calling bundle.
     *
     * @param bundle the bundle
     * @param name the name
     * @return the logger
     */
    public static Logger getLogger(Bundle bundle, String name) {
        return catalogue.getLoggerGoup(bundle).computeIfAbsent(name,
            (c, n) -> new Lf4OsgiLogger(c, n));
    }

    /**
     * Gets a logger with the given class' name for the given bundle.
     * <P>
     * If the logging bundle happens to be known in the context in which 
     * {@code getLogger} is called, this method should be preferred over 
     * {@link #getLogger(Class)} because the latter implies a small
     * overhead for finding out the calling bundle.
     *
     * @param bundle the bundle
     * @param clazz the class
     * @return the logger
     */
    public static Logger getLogger(Bundle bundle, Class<?> clazz) {
        String name = clazz.getName();
        return catalogue.getLoggerGoup(bundle).computeIfAbsent(name,
            (c, n) -> new Lf4OsgiLogger(c, n));
    }

}
