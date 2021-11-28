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

package de.mnl.osgi.lf4osgi.core;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.osgi.framework.Bundle;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

/**
 * A factory for creating {@link BufferingLogger}s.
 */
public class BufferingLoggerFactory implements LoggerFactory {

    public static final String LOG_THRESHOLD_PROPERTY
        = "de.mnl.osgi.lf4osgi.bufferThreshold";
    public static final String BUFFER_SIZE_PROPERTY
        = "de.mnl.osgi.lf4osgi.bufferSize";
    @SuppressWarnings("PMD.LooseCoupling")
    private final Queue<BufferedEvent> events;
    private LogLevel threshold = LogLevel.DEBUG;
    private int bufferSize = 100;

    /**
     * Instantiates a new buffering logger factory with an
     * event buffer with given size, buffering only events with
     * at least the given threshold. 
     */
    public BufferingLoggerFactory() {
        super();
        this.events = new ConcurrentLinkedQueue<>();
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                threshold = Optional.ofNullable(System
                    .getProperty(LOG_THRESHOLD_PROPERTY))
                    .map(LogLevel::valueOf).orElse(LogLevel.TRACE);
                bufferSize = Optional.ofNullable(System
                    .getProperty(BUFFER_SIZE_PROPERTY))
                    .map(Integer::parseInt).orElse(1000);
                return null;
            }
        });
    }

    /**
     * Sets the buffer size.
     *
     * @param bufferSize the buffer size
     * @return the buffering logger factory
     */
    public BufferingLoggerFactory setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        while (events.size() > bufferSize) {
            events.poll();
        }
        return this;
    }

    /**
     * Sets the threshold.
     *
     * @param threshold the threshold
     * @return the buffering logger factory
     */
    public BufferingLoggerFactory setThreshold(LogLevel threshold) {
        this.threshold = threshold;
        return this;
    }

    public LogLevel threshold() {
        return threshold;
    }

    /**
     * Adds the event, removing the oldest event if the buffer sise is reached.
     *
     * @param event the event
     */
    public void addEvent(BufferedEvent event) {
        if (events.size() == bufferSize) {
            events.poll();
        }
        events.add(event);
    }

    /**
     * Forward all buffered events.
     *
     * @param factory the factory
     */
    public void flush(LoggerFactory factory) {
        while (true) {
            BufferedEvent event = events.poll();
            if (event == null) {
                break;
            }
            event.forward(factory);
        }
    }

    @Override
    public Logger getLogger(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Logger getLogger(Class<?> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <L extends Logger> L getLogger(String name, Class<L> loggerType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <L extends Logger> L getLogger(Class<?> clazz, Class<L> loggerType) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <L extends Logger> L getLogger(Bundle bundle, String name,
            Class<L> loggerType) {
        return (L) new BufferingLogger(this, bundle, name);
    }

}
