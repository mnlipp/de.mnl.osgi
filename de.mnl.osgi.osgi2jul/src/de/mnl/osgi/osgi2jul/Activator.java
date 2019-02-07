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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
public class Activator implements BundleActivator {

	/** This tracker holds all log reader services. */
	private ServiceTracker<LogReaderService, LogReaderService> 
		serviceTracker = null;;

	/**
	 * Open the log service tracker. The tracker is customized to attach the 
	 * LogWriter to all registered log reader services (and detach it on 
	 * un-registration, of course). Already existing log entries 
	 * are forwarded to the LogWriter as well. No provisions have been
	 * taken to avoid the duplicate output that can occur if a message
	 * is logged between registering the LogWriter and forwarding
	 * stored log entries.
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		serviceTracker = new ServiceTracker<LogReaderService, LogReaderService>
			(context, LogReaderService.class, null) {

			@Override
			public LogReaderService addingService
				(ServiceReference<LogReaderService> reference) {
				LogReaderService service = super.addingService(reference);
				CountDownLatch enabled = new CountDownLatch(1);
				service.addLogListener(new LogWriter(enabled));
				List<LogEntry> entries = Collections.list(service.getLog());
				Collections.reverse(entries);
				LogWriter historyWriter = new LogWriter(new CountDownLatch(0));
				for (LogEntry entry: entries) {
					historyWriter.logged(entry);
				}
				enabled.countDown();
				return service;
			}

			@Override
			public void removedService
				(ServiceReference<LogReaderService> reference, 
						LogReaderService service) {
				super.removedService(reference, service);
			}

		};
		serviceTracker.open();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		serviceTracker.close();
		serviceTracker = null;
	}

}
