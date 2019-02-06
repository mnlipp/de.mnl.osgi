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

package de.mnl.osgi.jul2osgi.lib;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A {@link Logger} that forwards the {@link LogRecord}s to OSGi.
 */
public class ForwardingLogger extends Logger {

	private LogManager manager;
	
	public ForwardingLogger(LogManager manager, Logger orig) {
		super(orig.getName(), orig.getResourceBundleName());
		this.manager = manager;
	}

	@Override
	public void log(LogRecord record) {
        if (!isLoggable(record.getLevel())) {
            return;
        }
        Filter theFilter = getFilter();
        if (theFilter != null && !theFilter.isLoggable(record)) {
            return;
        }

        // We still invoke handlers that may have been attached
        // programmatically.
        
        Logger logger = this;
        while (logger != null) {
            final Handler[] loggerHandlers = logger.getHandlers();
            for (Handler handler : loggerHandlers) {
                handler.publish(record);
            }
            final boolean useParentHdls = logger.getUseParentHandlers();
            if (!useParentHdls) {
                return;
            }
            logger = logger.getParent();
        }

        // Unless parent handlers were to be ignored, we now log to OSGi
        manager.log(record);
	}

}
