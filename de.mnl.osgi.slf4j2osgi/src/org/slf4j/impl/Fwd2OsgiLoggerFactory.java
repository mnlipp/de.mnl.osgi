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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * An implementation of {@link ILoggerFactory} which returns
 * {@link Fwd2OsgiLogger} instances.
 */
public class Fwd2OsgiLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, Logger> loggerMap;

    /**
     * Instantiates a new logger factory.
     */
    public Fwd2OsgiLoggerFactory() {
        loggerMap = new ConcurrentHashMap<String, Logger>();
    }

    /**
     * Return an appropriate {@link Fwd2OsgiLogger} instance by name.
     */
    public Logger getLogger(String name) {
        return loggerMap.computeIfAbsent(name, k -> new Fwd2OsgiLogger(name));
    }

}
