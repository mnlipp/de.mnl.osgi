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

import de.mnl.osgi.lf4osgi.core.LoggerCatalogue;
import java.net.URI;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.osgi.framework.Bundle;

/**
 * A factory for creating OsgiLoggerContext objects.
 */
public class OsgiLoggerContextFactory extends LoggerCatalogue<OsgiLoggerContext>
        implements LoggerContextFactory {

    /**
     * Instantiates a new OSGi logger context factory.
     */
    public OsgiLoggerContextFactory() {
        super(b -> new OsgiLoggerContext(b));
    }

    @Override
    public OsgiLoggerContext getContext(final String fqcn,
            final ClassLoader loader, final Object externalContext,
            final boolean currentContext) {
        Bundle bundle = LoggerCatalogue.findBundle(fqcn).get();
        return getLoggerGoup(bundle);
    }

    @Override
    public OsgiLoggerContext getContext(final String fqcn,
            final ClassLoader loader,
            final Object externalContext, final boolean currentContext,
            final URI configLocation, final String name) {
        return getContext(fqcn, loader, externalContext, currentContext);
    }

    @Override
    public void removeContext(final LoggerContext ignored) {
        // Empty
    }
}
