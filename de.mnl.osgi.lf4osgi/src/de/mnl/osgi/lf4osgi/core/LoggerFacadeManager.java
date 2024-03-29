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

import de.mnl.osgi.coreutils.ServiceResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LoggerFactory;

/**
 * Tracks the availability of an OSGi {@link LoggerFactory} service
 * and keeps the logger facades up-to-date.
 */
public class LoggerFacadeManager extends ServiceResolver {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Collection<LoggerFacade> facades
        = Collections.newSetFromMap(new WeakHashMap<>());
    private static BundleContext context;
    private static Queue<Consumer<BundleContext>> pendingContextOperations
        = new ConcurrentLinkedQueue<>();
    private static BufferingLoggerFactory bufferingFactory
        = new BufferingLoggerFactory();
    private static LoggerFactory loggerFactory = bufferingFactory;

    /**
     * Register the given facade for receiving updates when the
     * logger factory changes. 
     *
     * @param loggerFacade the logger facade
     */
    public static void registerFacade(LoggerFacade loggerFacade) {
        synchronized (LoggerFacadeManager.class) {
            loggerFacade.loggerFactoryUpdated(
                loggerFactory == bufferingFactory ? bufferingFactory // NOPMD
                    : new FailSafeLoggerFactory(loggerFacade, loggerFactory));
            facades.add(loggerFacade);
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void updateLoggerFactory(LoggerFactory factory) {
        synchronized (LoggerFacadeManager.class) {
            loggerFactory = factory;
            for (LoggerFacade facade : new ArrayList<>(facades)) {
                facade.loggerFactoryUpdated(
                    loggerFactory == bufferingFactory ? bufferingFactory // NOPMD
                        : new FailSafeLoggerFactory(facade, loggerFactory));
            }
        }
    }

    @Override
    public void start(BundleContext context) throws Exception {
        synchronized (LoggerFacadeManager.class) {
            LoggerFacadeManager.context = context;
            // Process any pending operations that depend on a context.
            while (true) {
                Consumer<BundleContext> operation
                    = pendingContextOperations.poll();
                if (operation == null) {
                    break;
                }
                operation.accept(context);
            }
        }
        super.start(context);
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
    public void stop(BundleContext context) throws Exception {
        synchronized (LoggerFacadeManager.class) {
            LoggerFacadeManager.context = context;
        }
        super.stop(context);
    }

    /**
     * Execute an operation that depends on the availability of the
     * bundle context. If a context is available, the operation
     * is executed at once. Else, it is delayed until the context
     * becomes available.
     *
     * @param operation the operation
     */
    public static void contextOperation(Consumer<BundleContext> operation) {
        synchronized (LoggerFacadeManager.class) {
            if (context != null) {
                operation.accept(context);
                return;
            }
            pendingContextOperations.add(operation);
        }
    }

    @Override
    protected void onDissolving() {
        updateLoggerFactory(bufferingFactory);
    }

}
