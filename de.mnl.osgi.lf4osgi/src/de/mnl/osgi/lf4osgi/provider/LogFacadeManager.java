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

import de.mnl.osgi.coreutils.ServiceResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.WeakHashMap;

import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LoggerFactory;

/**
 * Forwards the SLF4J log events to the OSGi log service.
 */
public class LogFacadeManager extends ServiceResolver {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Collection<AbstractLoggerFacade> facades
        = Collections.newSetFromMap(new WeakHashMap<>());
    private static BufferingLoggerFactory bufferingFactory
        = new BufferingLoggerFactory();
    private static LoggerFactory loggerFactory = bufferingFactory;

    /* package */ static void
            addLoggerFacade(AbstractLoggerFacade loggerFacade) {
        synchronized (LogFacadeManager.class) {
            loggerFacade.loggerFactoryUpdated(loggerFactory);
            facades.add(loggerFacade);
        }
    }

    private void updateLoggerFactory(LoggerFactory factory) {
        synchronized (LogFacadeManager.class) {
            loggerFactory = factory;
            for (AbstractLoggerFacade facade : new ArrayList<>(facades)) {
                facade.loggerFactoryUpdated(loggerFactory);
            }
        }
    }

    @Override
    protected void configure() {
        Optional.ofNullable(
            context.getProperty(BufferingLoggerFactory.LOG_THRESHOLD_PROPERTY))
            .map(LogLevel::valueOf)
            .ifPresent(th -> bufferingFactory.setThreshold(th));
        Optional.ofNullable(
            context.getProperty(BufferingLoggerFactory.BUFFER_SIZE_PROPERTY))
            .map(Integer::parseInt)
            .ifPresent(sz -> bufferingFactory.setBufferSize(sz));
        addDependency(LoggerFactory.class);
    }

    @Override
    protected void onResolved() {
        LoggerFactory factory = get(LoggerFactory.class);
        // Yes, it may happen that we get a new logging event
        // before all buffered events have been flushed. But
        // in order to prevent this, we'd have to put all
        // logger usages in a synchronized block.
        updateLoggerFactory(factory);
        bufferingFactory.flush(factory);
    }

    @Override
    protected void onRebound(String dependency) {
        if (LoggerFactory.class.getName().equals(dependency)) {
            updateLoggerFactory(get(LoggerFactory.class));
        }
    }

    @Override
    protected void onDissolving() {
        updateLoggerFactory(bufferingFactory);
    }

}
