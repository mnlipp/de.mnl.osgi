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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogService;
import org.osgi.service.log.Logger;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.service.log.admin.LoggerContext;
import org.osgi.util.tracker.ServiceTracker;

import de.mnl.osgi.jul2osgi.lib.LogManager;
import de.mnl.osgi.jul2osgi.lib.LogRecordHandler;

/**
 */
public class Forwarder implements BundleActivator, LogRecordHandler {

	private ServiceTracker<LogService, LogService> logSvcTracker;
	private ServiceTracker<LoggerAdmin, LoggerAdmin> logAdmTracker;

    @Override
	public void start(BundleContext context) throws Exception {
    	final java.util.logging.LogManager lm
			= java.util.logging.LogManager.getLogManager();
		if (!(lm instanceof LogManager)) {
			return;
		}
		// Create new service tracker.
		logSvcTracker = new ServiceTracker<LogService, LogService>(
				context, LogService.class, null) {
			@Override
			public LogService addingService(
					ServiceReference<LogService> reference) {
				((LogManager)lm).setForwarder(Forwarder.this);
				return super.addingService(reference);
			}

			@Override
			public void removedService(ServiceReference<LogService> reference,
					LogService service) {
				// TODO Auto-generated method stub
				super.removedService(reference, service);
				if (getService() == null) {
					((LogManager)lm).setForwarder(null);
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
				LoggerContext ctx = adm.getLoggerContext(
						Forwarder.class.getPackage().getName());
				Map<String,LogLevel> logLevels = new HashMap<>();
				logLevels.put(Logger.ROOT_LOGGER_NAME, LogLevel.TRACE);
				ctx.setLogLevels(logLevels);
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
			((LogManager)lm).setForwarder(null);
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
				.orElse(Logger.ROOT_LOGGER_NAME), FormatterLogger.class);
		String format = "%s";
		int julLevel = record.getLevel().intValue();
		if (julLevel >= Level.SEVERE.intValue()) {
			if (logger.isErrorEnabled()) {
				logger.error(format, record.getMessage(), record.getMillis(),
						record.getSequenceNumber(), record.getSourceClassName(),
						record.getSourceMethodName(), record.getThreadID(),
						record.getThrown());
			}
		} else if (julLevel >= Level.WARNING.intValue()) {
			if (logger.isWarnEnabled()) {
				logger.warn(format, record.getMessage(), record.getMillis(),
						record.getSequenceNumber(), record.getSourceClassName(),
						record.getSourceMethodName(), record.getThreadID(),
						record.getThrown());
			}
		} else if (julLevel >= Level.INFO.intValue()) {
			if (logger.isInfoEnabled()) {
				logger.info(format, record.getMessage(), record.getMillis(),
						record.getSequenceNumber(), record.getSourceClassName(),
						record.getSourceMethodName(), record.getThreadID(),
						record.getThrown());
			}
		} else if (julLevel >= Level.FINE.intValue()) {
			if (logger.isDebugEnabled()) {
				logger.debug(format, record.getMessage(), record.getMillis(),
						record.getSequenceNumber(), record.getSourceClassName(),
						record.getSourceMethodName(), record.getThreadID(),
						record.getThrown());
			}
		} else if (logger.isTraceEnabled()) {
			logger.trace(format, record.getMessage(), record.getMillis(),
					record.getSequenceNumber(), record.getSourceClassName(),
					record.getSourceMethodName(), record.getThreadID(),
					record.getThrown());
		} 
		return true;
	}

}
