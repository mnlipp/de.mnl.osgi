/*
 * Copyright (C) 2016 Michael N. Lipp (http://www.mnl.de)
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
package de.mnl.osgi.log.fwd2jul;

import java.util.Collections;
import java.util.List;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @deprecated Replaced by {@code de.mnl.osgi.osgi2jul}.
 * 
 * This class provides the activator for this service. It registers
 * (respectively unregisters) the {@link LogWriter} as LogListener 
 * for for all log reader services and forwards any already existing 
 * log entries to it. 
 */
public class Activator implements BundleActivator {

	/** This tracker holds all log reader services. */
	private ServiceTracker<LogReaderService, LogReaderService> 
		serviceTracker = null;;
	/** The log listener that forwards to java.util.logging. */
	private LogWriter logWriter = new LogWriter();

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
				service.addLogListener(logWriter);
				// We don't check for duplicate log entries. It can happen
				// that an entry is forwarded twice. A new entry might be
				// handled by the LogWriter registered above and go into
				// the log entry store before we have copied its content
				// below. As log entries haven't got unique ids (as in
				// java.util.logging) we'd have to compare all properties
				// in order to identify duplicates.
				// But it is not very probable that this happens and having
				// an identical entry twice isn't as bad as loosing one.
				@SuppressWarnings("unchecked")
				List<LogEntry> entries = Collections.list(service.getLog());
				Collections.reverse(entries);
				for (LogEntry entry: entries) {
					logWriter.logged(entry);
				}
				return service;
			}

			@Override
			public void removedService
				(ServiceReference<LogReaderService> reference, 
						LogReaderService service) {
				super.removedService(reference, service);
				service.removeLogListener(logWriter);
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
