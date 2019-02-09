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

package de.mnl.osgi.osgi2jul;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This class provides the activator for this service. It registers
 * (respectively unregisters) the {@link LogWriter} as LogListener 
 * for for all log reader services and forwards any already existing 
 * log entries to it. 
 */
public class Forwarder implements BundleActivator {

    /** This tracker holds all log reader services. */
    private ServiceTracker<LogReaderService, LogReaderService> serviceTracker
        = null;
    private List<Handler> handlers = new ArrayList<>();

    /**
     * Open the log service tracker. The tracker is customized to attach a 
     * {@link LogWriter} to all registered log reader services (and detach 
     * it on un-registration, of course). Already existing log entries 
     * are forwarded to the {@link LogWriter} as well. No provisions have been
     * taken to avoid the duplicate output that can occur if a message
     * is logged between registering the {@link LogWriter} and forwarding
     * stored log entries.
     */
    @Override
    public void start(BundleContext context) throws Exception {
        createHandlers(context);

        serviceTracker = new ServiceTracker<LogReaderService, LogReaderService>(
            context, LogReaderService.class, null) {

            @Override
            public LogReaderService addingService(
                    ServiceReference<LogReaderService> reference) {
                LogReaderService service = super.addingService(reference);
                CountDownLatch enabled = new CountDownLatch(1);
                service.addLogListener(new LogWriter(Forwarder.this, enabled));
                List<LogEntry> entries = Collections.list(service.getLog());
                Collections.reverse(entries);
                LogWriter historyWriter
                    = new LogWriter(Forwarder.this, new CountDownLatch(0));
                for (LogEntry entry : entries) {
                    historyWriter.logged(entry);
                }
                enabled.countDown();
                return service;
            }

            @Override
            public void removedService(
                    ServiceReference<LogReaderService> reference,
                    LogReaderService service) {
                super.removedService(reference, service);
            }

        };
        serviceTracker.open();
    }

    private void createHandlers(BundleContext context) {
        String handlerClasses = Optional.ofNullable(
            context.getProperty(
                Forwarder.class.getPackage().getName() + ".handlers"))
            .orElse("java.util.logging.ConsoleHandler");
        for (String name : handlerClasses.split(",")) {
            name = name.trim();
            String[] bundleAndClass = name.split(":");
            Handler handler = null;
            try {
                if (bundleAndClass.length == 1) {
                    // Only class name
                    handler = handlerFromClassName(name);
                } else {
                    handler = handlerFromBundledClass(context,
                        bundleAndClass[0], bundleAndClass[1]);
                }
            } catch (Exception e) {
                System.err.println("Can't load or configure log handler \""
                    + name + "\": " + e.getMessage());
                e.printStackTrace();
            }
            String levelName
                = LogManager.getLogManager().getProperty(name + ".level");
            if (levelName != null) {
                Level level = Level.parse(levelName);
                handler.setLevel(level);
            }
            handlers.add(handler);
        }
    }

    private Handler handlerFromBundledClass(BundleContext context,
            String bundleName, String className)
            throws InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        for (Bundle bundle : context.getBundles()) {
            if (bundle.getSymbolicName().equals(bundleName)) {
                return (Handler) bundle.loadClass(className).newInstance();
            }
        }
        throw new ClassNotFoundException("Class " + className + " not found "
            + "in bundle " + bundleName);
    }

    private Handler handlerFromClassName(String name)
            throws PrivilegedActionException {
        Handler handler;
        handler = AccessController
            .doPrivileged(new PrivilegedExceptionAction<Handler>() {
                @Override
                public Handler run() throws Exception {
                    Class<?> hdlrCls;
                    hdlrCls = ClassLoader.getSystemClassLoader()
                        .loadClass(name);
                    return (Handler) hdlrCls.newInstance();
                }
            });
        return handler;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        serviceTracker.close();
        serviceTracker = null;
        handlers.clear();
    }

    public void publish(LogRecord record) {
        for (Handler handler : handlers) {
            handler.publish(record);
        }
    }

}
