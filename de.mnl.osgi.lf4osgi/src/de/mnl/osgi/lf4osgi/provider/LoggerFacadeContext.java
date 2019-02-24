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

package de.mnl.osgi.lf4osgi.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.osgi.framework.Bundle;

/**
 * The context in which logger facades are created and cached. 
 * The context is defined by the bundle for which the context
 * creates and caches the logger facades.
 */
public class LoggerFacadeContext<T extends LoggerFacade> {

    private final Bundle bundle;

    private final Map<String, T> loggers;

    /**
     * Instantiates a new bundle context.
     *
     * @param bundle the bundle
     */
    public LoggerFacadeContext(Bundle bundle) {
        this.bundle = bundle;
        loggers = new ConcurrentHashMap<>();
    }

    /**
     * @return the bundle
     */
    public final Bundle getBundle() {
        return bundle;
    }

    /**
     * Checks for logger.
     *
     * @param name the name
     * @return true, if successful
     */
    public boolean hasLogger(String name) {
        return loggers.containsKey(name);
    }

    /**
     * Gets the logger if it exists.
     *
     * @param name the name
     * @return the logger or {@code null}
     */
    public T getLogger(String name) {
        return loggers.get(name);
    }

    /**
     * Gets the logger with the specified name.
     *
     * @param name the name
     * @param supplier the supplier
     * @return the logger
     */
    public T computeIfAbsent(String name,
            BiFunction<LoggerFacadeContext<T>, String, T> supplier) {
        return loggers.computeIfAbsent(name, n -> supplier.apply(this, n));
    }

    /**
     * Put the logger in the context if it isn't already known.
     *
     * @param name the name
     * @param logger the logger
     */
    public void putIfAbsent(String name, T logger) {
        loggers.computeIfAbsent(name, k -> logger);
    }
}
