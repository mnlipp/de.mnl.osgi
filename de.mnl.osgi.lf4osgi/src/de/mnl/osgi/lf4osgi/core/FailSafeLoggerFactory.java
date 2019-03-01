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

package de.mnl.osgi.lf4osgi.core;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

/**
 * A wrapper class for {@link LoggerFactory}s passed to {@link LoggerFacade}s.
 * <P>
 * Logger facades can be created statically. It is therefore possible 
 * that they call 
 * {link {@link LoggerFactory#getLogger(Bundle, String, Class)}
 * with a bundle that is not yet resolved as argument. This would 
 * cause the invocation to fail with an {@link IllegalArgumentException}.
 * <P>
 * Therefore, logger facades always get a logger factory wrapped in
 * a {@link FailSafeLoggerFactory}. This wrapper checks if the bundle 
 * passed as argument to
 * {@link LoggerFactory#getLogger(Bundle, String, Class)}
 * is resolved before forwarding the call to the wrapped logger factory.
 * If it is not, the wrapper returns a logger with the LF4OSGi's bundle 
 * context (see {@link #getLogger(String, Class)}) for temporary use.
 * <P>
 * In addition, the wrapper registers a listener for changes of the bundle
 * passed as argument. When the bundle becomes resolved, the
 * {@link #updateFacade()} method is called, which calls 
 * {@link LoggerFacade#loggerFactoryUpdated(LoggerFactory)} on the facade
 * associated with this wrapper. Although it is not really the logger 
 * factory that has changed, the call will cause the logger 
 * facade to get a new logger, which references the now resolved bundle. 
 */
/* package */ class FailSafeLoggerFactory implements LoggerFactory {

    private final LoggerFacade facade;
    private final LoggerFactory delegee;

    /**
     * Instantiates a new logger factory wrapper.
     * @param delegee the delegee
     */
    public FailSafeLoggerFactory(LoggerFacade facade,
            LoggerFactory delegee) {
        this.facade = facade;
        this.delegee = delegee;
    }

    @Override
    public Logger getLogger(String name) {
        return delegee.getLogger(name);
    }

    @Override
    public Logger getLogger(Class<?> clazz) {
        return delegee.getLogger(clazz);
    }

    @Override
    public <L extends Logger> L getLogger(String name, Class<L> loggerType) {
        return delegee.getLogger(name, loggerType);
    }

    @Override
    public <L extends Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
        return delegee.getLogger(clazz, loggerType);
    }

    @Override
    public <L extends Logger> L getLogger(Bundle bundle, String name,
            Class<L> loggerType) {
        // Work around https://issues.apache.org/jira/browse/FELIX-6077.
        // Should be (Bundle.RESOLVED | Bundle.STARTING | Bundle.ACTIVE
        // | Bundle.STOPPING)
        if ((bundle.getState() & (Bundle.RESOLVED | Bundle.ACTIVE)) != 0) {
            return delegee.getLogger(bundle, name, loggerType);
        }
        // Return the temporary logger and prepare to replace as
        // soon as possible.
        registerForUpdate(bundle);
        return delegee.getLogger(name, loggerType);
    }

    private void registerForUpdate(final Bundle bundle) {
        LoggerFacadeManager.contextOperation(context -> {
            BundleListener listener = new BundleListener() {
                @Override
                public void bundleChanged(BundleEvent event) {
                    Bundle bundle = event.getBundle();
                    synchronized (bundle) {
                        if (event.getBundle().equals(bundle)
                            && (bundle.getState()
                                & (Bundle.RESOLVED | Bundle.ACTIVE)) != 0) {
                            context.removeBundleListener(this);
                            facade.loggerFactoryUpdated(delegee);
                        }
                    }
                }
            };
            context.addBundleListener(listener);
            // As we cannot have a lock on the bundle's state, the state
            // may have changed already and the listener is never invoked,
            // so re-check.
            if ((bundle.getState() & (Bundle.RESOLVED | Bundle.ACTIVE)) != 0) {
                context.removeBundleListener(listener);
                facade.loggerFactoryUpdated(delegee);
            }

        });
    }

}
