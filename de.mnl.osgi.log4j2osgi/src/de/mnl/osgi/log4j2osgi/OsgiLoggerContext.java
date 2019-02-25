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

package de.mnl.osgi.log4j2osgi;

import de.mnl.osgi.lf4osgi.core.LoggerGroup;

import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerRegistry;
import org.osgi.framework.Bundle;

public class OsgiLoggerContext implements LoggerGroup, LoggerContext {

    private final Bundle bundle;
    private final LoggerRegistry<OsgiLogger> loggerRegistry;

    public OsgiLoggerContext(Bundle bundle) {
        this.bundle = bundle;
        loggerRegistry = new LoggerRegistry<>();
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public OsgiLogger getLogger(final String name) {
        if (!loggerRegistry.hasLogger(name)) {
            loggerRegistry.putIfAbsent(name, null,
                new OsgiLogger(this, name));
        }
        return loggerRegistry.getLogger(name);
    }

    @Override
    public ExtendedLogger getLogger(final String name,
            final MessageFactory messageFactory) {
        if (!loggerRegistry.hasLogger(name, messageFactory)) {
            loggerRegistry.putIfAbsent(name, messageFactory,
                new OsgiLogger(this, name, messageFactory));
        }
        return loggerRegistry.getLogger(name, messageFactory);
    }

    @Override
    public boolean hasLogger(final String name) {
        return loggerRegistry.hasLogger(name);
    }

    @Override
    public boolean hasLogger(final String name,
            final MessageFactory messageFactory) {
        return loggerRegistry.hasLogger(name, messageFactory);
    }

    @Override
    public boolean hasLogger(final String name,
            final Class<? extends MessageFactory> messageFactoryClass) {
        return loggerRegistry.hasLogger(name, messageFactoryClass);
    }

    @Override
    public Object getExternalContext() {
        return null;
    }

}
