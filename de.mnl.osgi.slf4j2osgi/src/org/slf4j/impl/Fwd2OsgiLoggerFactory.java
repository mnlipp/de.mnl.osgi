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

import de.mnl.osgi.lf4osgi.core.DefaultLoggerGroup;
import de.mnl.osgi.lf4osgi.core.LoggerCatalogue;

import org.osgi.framework.Bundle;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * An implementation of {@link ILoggerFactory} which returns
 * {@link Fwd2OsgiLogger} instances.
 */
public class Fwd2OsgiLoggerFactory implements ILoggerFactory {

    private final LoggerCatalogue<DefaultLoggerGroup<Fwd2OsgiLogger>> loggers;

    /**
     * Instantiates a new logger factory.
     */
    public Fwd2OsgiLoggerFactory() {
        loggers = new LoggerCatalogue<>(b -> new DefaultLoggerGroup<>(b,
            (g, n) -> new Fwd2OsgiLogger(g, n)));
    }

    /**
     * Return an appropriate {@link Fwd2OsgiLogger} instance by name.
     */
    public Logger getLogger(String name) {
        Bundle bundle = LoggerCatalogue
            .findBundle(org.slf4j.LoggerFactory.class.getName()).orElse(null);
        return loggers.getLoggerGoup(bundle).computeIfAbsent(name);
    }
}
