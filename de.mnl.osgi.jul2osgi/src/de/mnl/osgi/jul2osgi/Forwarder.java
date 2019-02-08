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
import de.mnl.osgi.jul2osgi.lib.LogRecordHandler;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.service.log.admin.LoggerContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 */
public class Forwarder implements BundleActivator, LogRecordHandler {

    private ServiceTracker<LogService, LogService> logSvcTracker;
    private ServiceTracker<LoggerAdmin, LoggerAdmin> logAdmTracker;
    private String logPattern;
    private LogLevel contextLevel;

    @Override
    public void start(BundleContext context) throws Exception {
        final java.util.logging.LogManager lm
            = java.util.logging.LogManager.getLogManager();
        if (!(lm instanceof LogManager)) {
            System.err.println("Configuration error: "
                + "Bundle de.mnl.osgi.jul2osgi must be used with "
                + "LogManager from de.mnl.osgi.jul2osgi.log.");
            return;
        }
        logPattern = Optional.ofNullable(
            context.getProperty("de.mnl.osgi.jul2osgi.logPattern"))
            .orElse("{0}");
        String ctxProperty = context.getProperty(
            "de.mnl.osgi.jul2osgi.contextLevel");
        if (ctxProperty == null) {
            contextLevel = LogLevel.TRACE;
        } else {
            if (!ctxProperty.equals("NONE")) {
                try {
                    contextLevel = LogLevel.valueOf(ctxProperty);
                } catch (IllegalArgumentException e) {
                    // Ignored if invalid.
                }
            }
        }

        // Create new service tracker.
        logSvcTracker = new ServiceTracker<LogService, LogService>(
            context, LogService.class, null) {
            @Override
            public LogService addingService(
                    ServiceReference<LogService> reference) {
                ((LogManager) lm).setForwarder(Forwarder.this);
                return super.addingService(reference);
            }

            @Override
            public void removedService(ServiceReference<LogService> reference,
                    LogService service) {
                // TODO Auto-generated method stub
                super.removedService(reference, service);
                if (getService() == null) {
                    ((LogManager) lm).setForwarder(null);
                }
            }
        };
        logSvcTracker.open();
        // Create the admin tracker
        logAdmTracker = new ServiceTracker<LoggerAdmin, LoggerAdmin>(
            context, LoggerAdmin.class, null) {
            @Override
            public LoggerAdmin addingService(
                    ServiceReference<LoggerAdmin> reference) {
                LoggerAdmin adm = super.addingService(reference);
                if (contextLevel != null) {
                    LoggerContext ctx = adm.getLoggerContext(
                        Forwarder.class.getPackage().getName());
                    Map<String, LogLevel> logLevels = new HashMap<>();
                    logLevels.put(Logger.ROOT_LOGGER_NAME, contextLevel);
                    ctx.setLogLevels(logLevels);
                }
                return adm;
            }
        };
        logAdmTracker.open();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        java.util.logging.LogManager lm
            = java.util.logging.LogManager.getLogManager();
        if (lm instanceof LogManager) {
            ((LogManager) lm).setForwarder(null);
        }
        logSvcTracker.close();
        logAdmTracker.close();
    }

    @Override
    public boolean process(LogRecord record) {
        LogService service = logSvcTracker.getService();
        if (service == null) {
            return false;
        }
        Logger logger = service.getLogger(
            Optional.ofNullable(record.getLoggerName())
                .orElse(Logger.ROOT_LOGGER_NAME));
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

    private String formatMessage(String format, LogRecord record) {
        String message = record.getMessage();
        if (record.getResourceBundle() != null) {
            try {
                message = record.getResourceBundle().getString(message);
            } catch (MissingResourceException e) {
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
}
