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

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import de.mnl.osgi.jul2osgi.lib.LogManager;
import de.mnl.osgi.jul2osgi.lib.LogRecordHandler;

/**
 */
public class Forwarder implements BundleActivator, LogRecordHandler {

	private ServiceTracker<LogService, LogService> logSvcTracker;

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
			public LogService addingService(ServiceReference<LogService> reference) {
				((LogManager)lm).setForwarder(Forwarder.this);
				return super.addingService(reference);
			}

			@Override
			public void removedService(ServiceReference<LogService> reference, LogService service) {
				// TODO Auto-generated method stub
				super.removedService(reference, service);
				if (getService() == null) {
					((LogManager)lm).setForwarder(null);
				}
			}
		};
    	logSvcTracker.open();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		java.util.logging.LogManager lm
			= java.util.logging.LogManager.getLogManager();
		if (lm instanceof LogManager) {
			((LogManager)lm).setForwarder(null);
		}
	}

	@Override
	public boolean process(LogRecord record) {
		LogService service = logSvcTracker.getService();
		if (service == null) {
			return false;
		}
		int julLevel = record.getLevel().intValue();
		int level;
		if (julLevel >= Level.SEVERE.intValue()) {
			level = LogService.LOG_ERROR;
		} else if (julLevel >= Level.WARNING.intValue()) {
			level = LogService.LOG_WARNING;
		} else if (julLevel >= Level.INFO.intValue()) {
			level = LogService.LOG_INFO;
		} else {
			level = LogService.LOG_DEBUG;
		}
		service.log(level, record.getMessage(), record.getThrown());
		return true;
	}

}
