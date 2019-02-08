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

import java.util.concurrent.CountDownLatch;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;

/**
 * This class implements a LogListener that forwards the LogEntries
 * to a handler.
 */
public class LogWriter implements LogListener {

    private Handler handler = new ConsoleHandler();
    private final Level auditLevel = new Level("AUDIT", Integer.MAX_VALUE) {
        private static final long serialVersionUID = 1269723275384552686L;
    };
    private CountDownLatch enabled;

    public LogWriter(CountDownLatch enabled) {
        this.enabled = enabled;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.osgi.service.log.LogListener#logged(org.osgi.service.log.LogEntry)
     */
    @Override
    public void logged(LogEntry entry) {
        Level level = Level.OFF;
        switch (entry.getLogLevel()) {
        case TRACE:
            level = Level.FINER;
            break;
        case DEBUG:
            level = Level.FINE;
            break;
        case INFO:
            level = Level.INFO;
            break;
        case WARN:
            level = Level.WARNING;
            break;
        case ERROR:
            level = Level.SEVERE;
            break;
        case AUDIT:
            level = auditLevel;
            break;
        }
        LogRecord record = new LogRecord(level, entry.getMessage());
        record.setLoggerName(entry.getLoggerName());
        record.setMillis(entry.getTime());
        record.setSequenceNumber(entry.getSequence());
        Throwable thrown = entry.getException();
        if (thrown != null) {
            record.setThrown(thrown);
        }
        try {
            enabled.await();
        } catch (InterruptedException e) {
            // Just an attempt to synchronize.
        }
        handler.publish(record);
    }

}
