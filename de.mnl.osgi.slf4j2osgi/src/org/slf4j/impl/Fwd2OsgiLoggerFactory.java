/**
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

package org.slf4j.impl;

import de.mnl.osgi.lf4osgi.provider.LoggerFacadeContextRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * An implementation of {@link ILoggerFactory} which returns
 * {@link Fwd2OsgiLogger} instances.
 */
public class Fwd2OsgiLoggerFactory implements ILoggerFactory {

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<Bundle, Map<String, Logger>> loggerMap;

    /**
     * Instantiates a new logger factory.
     */
    public Fwd2OsgiLoggerFactory() {
        loggerMap = Collections.synchronizedMap(new WeakHashMap<>());
    }

    /**
     * Return an appropriate {@link Fwd2OsgiLogger} instance by name.
     */
    public Logger getLogger(String name) {
        Bundle bundle = LoggerFacadeContextRegistry
            .findBundle(org.slf4j.LoggerFactory.class.getName()).orElse(null);
        return loggerMap
            .computeIfAbsent(bundle, b -> new ConcurrentHashMap<>())
            .computeIfAbsent(name, n -> new Fwd2OsgiLogger(bundle, n));
    }
}
