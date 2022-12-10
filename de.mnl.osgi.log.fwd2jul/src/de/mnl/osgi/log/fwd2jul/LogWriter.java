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

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.osgi.framework.Bundle;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogService;

/**
 * This class implements a LogListener that forwards the LogEntries .
 *
 * @deprecated Replaced by {@code de.mnl.osgi.osgi2jul}.
 */
@Deprecated
public class LogWriter implements LogListener {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.osgi.service.log.LogListener#logged(org.osgi.service.log.LogEntry)
     */
  @Override
    public void logged(LogEntry entry) {
        final Logger logger = Logger.getLogger("OSGi Logger");
        Level level = Level.OFF;
        switch (entry.getLevel()) {
        case LogService.LOG_DEBUG:
            level = Level.FINE;
            break;
        case LogService.LOG_INFO:
            level = Level.INFO;
            break;
        case LogService.LOG_WARNING:
            level = Level.WARNING;
            break;
        case LogService.LOG_ERROR:
            level = Level.SEVERE;
            break;
        }
        LogRecord record = new LogRecord(level, entry.getMessage());
        Throwable trw = entry.getException();
        if (trw != null) {
            record.setThrown(trw);
        }
        Bundle bundle = entry.getBundle();
        if (bundle != null) {
            record.setSourceClassName(entry.getBundle().getSymbolicName());
        }
        logger.log(record);
    }

}
