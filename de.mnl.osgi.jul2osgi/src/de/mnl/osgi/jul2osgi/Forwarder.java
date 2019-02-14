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

package de.mnl.osgi.jul2osgi;

import de.mnl.osgi.jul2osgi.lib.LogManager;
import de.mnl.osgi.jul2osgi.lib.LogManager.LogInfo;
import de.mnl.osgi.jul2osgi.lib.LogRecordHandler;

import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.service.log.admin.LoggerContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class Forwarder implements BundleActivator, LogRecordHandler {

    private ServiceTracker<LogService, LogService> logSvcTracker;
    private ServiceTracker<LoggerAdmin, LoggerAdmin> logAdmTracker;
    private String logPattern;
    private boolean adaptOsgiLevel = true;
    private final Map<Class<?>, WeakReference<Bundle>> bundles
        = new ConcurrentHashMap<>();
    private final Set<Bundle> adaptedBundles = Collections
        .synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    public void start(BundleContext context) throws Exception {
        final java.util.logging.LogManager logMgr
            = java.util.logging.LogManager.getLogManager();
        if (!(logMgr instanceof LogManager)) {
            System.err.println("Configuration error: "
                + "Bundle de.mnl.osgi.jul2osgi must be used with "
                + "LogManager from de.mnl.osgi.jul2osgi.log.");
            return;
        }
        logPattern = Optional.ofNullable(
            context.getProperty("de.mnl.osgi.jul2osgi.logPattern"))
            .orElse("{0}");
        String adaptProperty = context.getProperty(
            "de.mnl.osgi.jul2osgi.adaptOSGiLevel");
        if (adaptProperty != null) {
            adaptOsgiLevel = Boolean.parseBoolean(adaptProperty);
        }

        // Create the admin tracker
        logAdmTracker = new ServiceTracker<LoggerAdmin, LoggerAdmin>(
            context, LoggerAdmin.class, null) {
            @Override
            public LoggerAdmin addingService(
                    ServiceReference<LoggerAdmin> reference) {
                LoggerAdmin adm = super.addingService(reference);
                // Handle this bundle specially
                if (adaptOsgiLevel) {
                    adaptLogLevel(adm, context.getBundle());
                }
                return adm;
            }
        };
        logAdmTracker.open();

        // Create new service tracker.
        logSvcTracker = new ServiceTracker<LogService, LogService>(
            context, LogService.class, null) {
            private LogService fallbackLogService;

            @Override
            public LogService addingService(
                    ServiceReference<LogService> reference) {
                LogService newService = super.addingService(reference);
                // Note that the service being added is not returned
                // by getService() yet.
                fallbackLogService = newService;
                ((LogManager) logMgr).setForwarder(Forwarder.this);
                return newService;
            }

            @Override
            public void removedService(ServiceReference<LogService> reference,
                    LogService service) {
                fallbackLogService = null;
                super.removedService(reference, service);
                if (getService() == null) {
                    ((LogManager) logMgr).setForwarder(null);
                }
            }

            @Override
            public LogService getService() {
                return Optional.ofNullable(super.getService())
                    .orElse(fallbackLogService);
            }

        };
        logSvcTracker.open();
    }

    private void adaptLogLevel(LoggerAdmin logAdmin, Bundle bundle) {
        if (adaptedBundles.contains(bundle)) {
            return;
        }
        LoggerContext ctx = logAdmin.getLoggerContext(
            bundle.getSymbolicName() + "|" + bundle.getVersion());
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, LogLevel> logLevels = new HashMap<>();
        logLevels.put(Logger.ROOT_LOGGER_NAME, LogLevel.TRACE);
        ctx.setLogLevels(logLevels);
        adaptedBundles.add(bundle);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        java.util.logging.LogManager logMgr
            = java.util.logging.LogManager.getLogManager();
        if (logMgr instanceof LogManager) {
            ((LogManager) logMgr).setForwarder(null);
        }
        logSvcTracker.close();
        logAdmTracker.close();
    }

    @Override
    public boolean process(LogInfo logInfo) {
        LogService service = logSvcTracker.getService();
        if (service == null) {
            return false;
        }
        final LogRecord record = logInfo.getLogRecord();
        final String loggerName = Optional.ofNullable(record.getLoggerName())
            .orElse(Logger.ROOT_LOGGER_NAME);
        Logger logger = findBundle(logInfo.getCallingClass())
            .map(b -> {
                if (adaptOsgiLevel) {
                    Optional.ofNullable(logAdmTracker.getService())
                        .ifPresent(adm -> adaptLogLevel(adm, b));
                }
                return service.getLogger(b, loggerName, Logger.class);
            }).orElse(service.getLogger(loggerName));

        int julLevel = record.getLevel().intValue();
        if (julLevel >= Level.SEVERE.intValue()) {
            if (logger.isErrorEnabled()) {
                logger.error("{}", formatMessage(logPattern, record),
                    record.getThrown());
            }
        } else if (julLevel >= Level.WARNING.intValue()) {
            if (logger.isWarnEnabled()) {
                logger.warn("{}", formatMessage(logPattern, record),
                    record.getThrown());
            }
        } else if (julLevel >= Level.INFO.intValue()) {
            if (logger.isInfoEnabled()) {
                logger.info("{}", formatMessage(logPattern, record),
                    record.getThrown());
            }
        } else if (julLevel >= Level.FINE.intValue()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}", formatMessage(logPattern, record),
                    record.getThrown());
            }
        } else if (logger.isTraceEnabled()) {
            logger.trace("{}", formatMessage(logPattern, record),
                record.getThrown());
        }
        return true;
    }

    private Optional<Bundle> findBundle(Class<?> callingClass) {
        if (callingClass == null) {
            return Optional.empty();
        }
        Bundle bundle = Optional.ofNullable(bundles.get(callingClass))
            .map(WeakReference::get).orElse(null);
        if (bundle != null) {
            return Optional.of(bundle);
        }
        bundle = FrameworkUtil.getBundle(callingClass);
        if (bundle != null) {
            bundles.put(callingClass, new WeakReference<>(bundle)); // NOPMD
            return Optional.of(bundle);
        }
        return Optional.empty();
    }

    private String formatMessage(String format, LogRecord record) {
        String message = record.getMessage();
        if (record.getResourceBundle() != null) {
            try {
                message = record.getResourceBundle().getString(message);
            } catch (MissingResourceException e) { // NOPMD
                // Leave message as it is
            }
        }
        Object[] parameters = record.getParameters();
        if (parameters != null && parameters.length > 0) {
            message = MessageFormat.format(message, record.getParameters());
        }
        return MessageFormat.format(format, message, record.getMillis(),
            record.getSequenceNumber(), record.getSourceClassName(),
            record.getSourceMethodName(), record.getThreadID());
    }

    /**
     * Process events that are delivered .
     *
     * @param logInfos the log infos
     */
    @SuppressWarnings({ "PMD.EmptyCatchBlock", "PMD.UseVarargs" })
    public void processBuffered(LogInfo[] logInfos) {
        String threadName = Thread.currentThread().getName();
        try {
            // Set thread name to indicate invalid context
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        Thread.currentThread().setName("(log flusher)");
                        return null;
                    }
                });
            } catch (SecurityException e) {
                // Ignored, was just a best effort.
            }
            // Now process
            for (LogInfo info : logInfos) {
                process(info);
            }
        } finally {
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        Thread.currentThread().setName(threadName);
                        return null; // NOPMD
                    }
                });
            } catch (SecurityException e) {
                // Ignored. If resetting doesn't work, setting hasn't worked
                // neither
            }
        }
    }

}
