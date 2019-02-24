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

package de.mnl.osgi.lf4osgi.provider;

import org.osgi.framework.Bundle;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerConsumer;

/**
 * A logger that logs to a buffer.
 */
@SuppressWarnings("PMD.TooManyMethods")
public class BufferingLogger implements Logger {

    private final BufferingLoggerFactory factory;
    private final Bundle bundle;
    private final String name;

    /**
     * Instantiates a new buffering logger with the given factory, bundle
     * and provided factory.
     *
     * @param factory the factory
     * @param bundle the bundle
     * @param name the name
     */
    public BufferingLogger(BufferingLoggerFactory factory, Bundle bundle,
            String name) {
        this.factory = factory;
        this.bundle = bundle;
        this.name = name;
    }

    /**
     * @return the name
     */
    public final String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return factory.threshold().implies(LogLevel.TRACE);
    }

    @Override
    public void trace(String message) {
        if (isTraceEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.TRACE, message));
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.TRACE, format, arg));
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (isTraceEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.TRACE, format, arg1, arg2));
        }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (isTraceEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.TRACE, format, arguments));
        }
    }

    @Override
    public <E extends Exception> void trace(LoggerConsumer<E> consumer)
            throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isDebugEnabled() {
        return factory.threshold().implies(LogLevel.DEBUG);
    }

    @Override
    public void debug(String message) {
        if (isDebugEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.DEBUG, message));
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.DEBUG, format, arg));
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.DEBUG, format, arg1, arg2));
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.DEBUG, format, arguments));
        }
    }

    @Override
    public <E extends Exception> void debug(LoggerConsumer<E> consumer)
            throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isInfoEnabled() {
        return factory.threshold().implies(LogLevel.INFO);
    }

    @Override
    public void info(String message) {
        if (isInfoEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.INFO, message));
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.INFO, format, arg));
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.INFO, format, arg1, arg2));
        }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.INFO, format, arguments));
        }
    }

    @Override
    public <E extends Exception> void info(LoggerConsumer<E> consumer)
            throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isWarnEnabled() {
        return factory.threshold().implies(LogLevel.WARN);
    }

    @Override
    public void warn(String message) {
        if (isWarnEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.WARN, message));
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.WARN, format, arg));
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.WARN, format, arg1, arg2));
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.WARN, format, arguments));
        }
    }

    @Override
    public <E extends Exception> void warn(LoggerConsumer<E> consumer)
            throws E {
        consumer.accept(this);
    }

    @Override
    public boolean isErrorEnabled() {
        return factory.threshold().implies(LogLevel.ERROR);
    }

    @Override
    public void error(String message) {
        if (isErrorEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.ERROR, message));
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.ERROR, format, arg));
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.ERROR, format, arg1, arg2));
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            factory.addEvent(new BufferedEvent(bundle, getName(),
                LogLevel.ERROR, format, arguments));
        }
    }

    @Override
    public <E extends Exception> void error(LoggerConsumer<E> consumer)
            throws E {
        consumer.accept(this);
    }

    @Override
    public void audit(String message) {
        factory.addEvent(new BufferedEvent(bundle, getName(),
            LogLevel.AUDIT, message));
    }

    @Override
    public void audit(String format, Object arg) {
        factory.addEvent(new BufferedEvent(bundle, getName(),
            LogLevel.AUDIT, format, arg));
    }

    @Override
    public void audit(String format, Object arg1, Object arg2) {
        factory.addEvent(new BufferedEvent(bundle, getName(),
            LogLevel.AUDIT, format, arg1, arg2));
    }

    @Override
    public void audit(String format, Object... arguments) {
        factory.addEvent(new BufferedEvent(bundle, getName(),
            LogLevel.AUDIT, format, arguments));
    }

}
