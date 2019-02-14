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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ForwardingManager implements BundleActivator {

    private static final Pattern HANDLER_DEF = Pattern.compile(
        "(?:(?<bundle>[^:]+):)?(?<class>[^\\[]+)(?:\\[(?<id>\\d+)\\])?");

    /** This tracker holds all log reader services. */
    private ServiceTracker<LogReaderService, LogReaderService> logReaderTracker;
    private final List<HandlerConfig> handlers = new ArrayList<>();

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

        logReaderTracker
            = new ServiceTracker<LogReaderService, LogReaderService>(
                context, LogReaderService.class, null) {

                @Override
                public LogReaderService addingService(
                        ServiceReference<LogReaderService> reference) {
                    LogReaderService service = super.addingService(reference);
                    CountDownLatch enabled = new CountDownLatch(1);
                    service.addLogListener(
                        new LogWriter(ForwardingManager.this, enabled));
                    List<LogEntry> entries = Collections.list(service.getLog());
                    Collections.reverse(entries);
                    LogWriter historyWriter = new LogWriter(
                        ForwardingManager.this, new CountDownLatch(0));
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
        logReaderTracker.open();
    }

    @SuppressWarnings({ "PMD.SystemPrintln", "PMD.DataflowAnomalyAnalysis" })
    private void createHandlers(BundleContext context) {
        String handlerClasses = Optional.ofNullable(
            context.getProperty(
                ForwardingManager.class.getPackage().getName() + ".handlers"))
            .orElse("java.util.logging.ConsoleHandler");
        Arrays.stream(handlerClasses.split(",")).map(String::trim)
            .forEach(name -> {
                Matcher parts = HANDLER_DEF.matcher(name);
                if (!parts.matches()) {
                    System.err.println("Handler definition \""
                        + name + "\" is invalid.");
                    return;
                }
                Handler handler = null;
                try {
                    if (parts.group("bundle") == null) {
                        // Only class name
                        handler = handlerFromClassName(parts.group("class"));
                    } else {
                        handler = handlerFromBundledClass(context,
                            parts.group("bundle"), parts.group("class"));
                    }
                } catch (Exception e) { // NOPMD
                    System.err.println("Can't load or configure log handler \""
                        + name + "\": " + e.getMessage());
                    return;
                }
                handlers.add(createHandlerConfig(context, parts, handler));
            });
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

    @SuppressWarnings("PMD.SystemPrintln")
    private HandlerConfig createHandlerConfig(BundleContext context,
            Matcher parts, Handler handler) {
        String levelName = null;
        MessageFormat outputFormat = null;
        if (parts.group("id") != null) {
            String handlerPrefix
                = ForwardingManager.class.getPackage().getName()
                    + ".handler" + (parts.group("id") == null ? ""
                        : "." + parts.group("id"));
            levelName = context.getProperty(handlerPrefix + ".level");
            String format = context.getProperty(handlerPrefix + ".format");
            if (format != null) {
                try {
                    outputFormat = new MessageFormat(format);
                } catch (IllegalArgumentException e) {
                    System.err.println("Illegal format: \"" + format + "\"");
                }
            }
        }
        if (levelName != null) {
            Level level = Level.parse(levelName);
            handler.setLevel(level);
        }
        return new HandlerConfig(handler, outputFormat);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        logReaderTracker.close();
        logReaderTracker = null;
        handlers.clear();
    }

    /**
     * Send the record to all handlers. 
     *
     * @param entry the original OSGi log entry
     * @param record the prepared JUL record
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void publish(LogEntry entry, LogRecord record) {
        Object[] xtraArgs = null;
        for (HandlerConfig cfg : handlers) {
            if (cfg.getOutputFormat() == null) {
                cfg.getHandler().publish(record);
                continue;
            }
            String oldMessage = record.getMessage();
            try {
                if (xtraArgs == null) {
                    xtraArgs = new Object[] {
                        oldMessage,
                        entry.getBundle().getSymbolicName(),
                        entry.getBundle().getHeaders().get("Bundle-Name"),
                        entry.getBundle().getVersion().toString(),
                        entry.getThreadInfo() };
                }
                record.setMessage(cfg.getOutputFormat().format(xtraArgs,
                    new StringBuffer(), null).toString());
                cfg.getHandler().publish(record);
            } finally {
                record.setMessage(oldMessage);
            }
        }
    }

}
