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

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * An implementation of {@link java.util.logging.LogManager}. The 
 * system property {@code java.util.logging.manager} must be set to 
 * {@code de.mnl.osgi.jul2osgi.LogManager} in order for this manager to be
 * used. The library that provides this implementation must be
 * on the system classpath (e.g. using {@code bnd}'s
 * <a href=https://bnd.bndtools.org/instructions/runpath.html>{@code -runpath}</a>
 * instruction. 
 */
public class LogManager extends java.util.logging.LogManager {

    private Deque<LogInfo> buffered = new LinkedList<>();
    private int bufferSize = 100;
    private LogRecordHandler forwarder;

    /**
     * Instantiates a new log manager.
     */
    public LogManager() {
        try {
            bufferSize = Integer.parseInt(System.getProperty(
                "de.mnl.osgi.jul2osgi.bufferSize", "100"));
        } catch (NumberFormatException e) {
            // Left to default if invaid.
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.LogManager#getLogger(java.lang.String)
     */
    @Override
    public Logger getLogger(final String name) {
        Logger logger = super.getLogger(name);
        return logger;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.logging.LogManager#addLogger(java.util.logging.Logger)
     */
    @Override
    public boolean addLogger(Logger logger) {
        // It's hard to believe but this effectively overwrites the
        // Logger#getLogger factory methods.
        if (!(logger instanceof ForwardingLogger)) {
            logger = new ForwardingLogger(this, logger);
        }
        super.addLogger(logger);
        return false;
    }

    /**
     * Store or forward the logged information.
     *
     * @param logInfo the information
     */
    public void log(LogInfo logInfo) {
        LogRecordHandler fwd = forwarder;
        if (fwd != null && fwd.process(logInfo)) {
            return;
        }
        synchronized (buffered) {
            if (buffered.size() == bufferSize) {
                buffered.removeFirst();
            }
            buffered.add(logInfo);
        }
    }

    /**
     * Sets the forwarder.
     *
     * @param forwarder the new forwarder
     */
    public void setForwarder(LogRecordHandler forwarder) {
        // It may be that the forwarder is used by a call to
        // #log before all records from the buffer have been
        // flushed, thus changing the proper sequence.
        synchronized (this) {
            this.forwarder = forwarder;
            if (forwarder == null) {
                return;
            }
            List<LogInfo> tbf;
            synchronized (buffered) {
                tbf = new ArrayList<>(buffered);
                buffered.clear();
            }
            for (LogInfo record : tbf) {
                forwarder.process(record);
            }

        }
    }

    /**
     * Holds the information from a logger invocation.
     */
    public static class LogInfo {
        private Class<?> callingClass;
        private LogRecord logRecord;

        /**
         * Instantiates a new log info.
         *
         * @param definingClass the calling class
         * @param logRecord the log record
         */
        public LogInfo(Class<?> definingClass, LogRecord logRecord) {
            super();
            this.callingClass = definingClass;
            this.logRecord = logRecord;
        }

        /**
         * Gets the calling class.
         *
         * @return the callingClass
         */
        public Class<?> getCallingClass() {
            return callingClass;
        }

        /**
         * Gets the log record.
         *
         * @return the logRecord
         */
        public LogRecord getLogRecord() {
            return logRecord;
        }

    }
}
