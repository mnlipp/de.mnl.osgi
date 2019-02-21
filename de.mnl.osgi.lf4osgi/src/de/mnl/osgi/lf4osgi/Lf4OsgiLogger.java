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

package de.mnl.osgi.lf4osgi;

import de.mnl.osgi.lf4osgi.provider.AbstractLoggerFacade;

import org.osgi.framework.Bundle;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerConsumer;
import org.osgi.service.log.LoggerFactory;

/**
 * The implementation of the facade for OSGi loggers.
 */
@SuppressWarnings("PMD.TooManyMethods")
public class Lf4OsgiLogger extends AbstractLoggerFacade implements Logger {

    @SuppressWarnings("PMD.LoggerIsNotStaticFinal")
    private Logger delegee;

    /**
     * Instantiates a new logger with the provided name.
     *
     * @param name the name
     */
    public Lf4OsgiLogger(String name) {
        super(name, de.mnl.osgi.lf4osgi.LoggerFactory.class.getName());
    }

    /**
     * Instantiates a new logger for the given bundle with the
     * provided name.
     *
     * @param bundle the bundle
     * @param name the name
     */
    public Lf4OsgiLogger(Bundle bundle, String name) {
        super(bundle, name);
    }

    @Override
    public void loggerFactoryUpdated(LoggerFactory factory) {
        delegee = factory.getLogger(getBundle(), getName(), Logger.class);
    }

    @Override
    public boolean isTraceEnabled() {
        return delegee.isTraceEnabled();
    }

    @Override
    public void trace(String message) {
        delegee.trace(message);
    }

    @Override
    public void trace(String format, Object arg) {
        delegee.trace(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        delegee.trace(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        delegee.trace(format, arguments);
    }

    @Override
    public <E extends Exception> void trace(LoggerConsumer<E> consumer)
            throws E {
        delegee.trace(consumer);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegee.isDebugEnabled();
    }

    @Override
    public void debug(String message) {
        delegee.debug(message);
    }

    @Override
    public void debug(String format, Object arg) {
        delegee.debug(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        delegee.debug(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        delegee.debug(format, arguments);
    }

    @Override
    public <E extends Exception> void debug(LoggerConsumer<E> consumer)
            throws E {
        delegee.debug(consumer);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegee.isInfoEnabled();
    }

    @Override
    public void info(String message) {
        delegee.info(message);
    }

    @Override
    public void info(String format, Object arg) {
        delegee.info(format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        delegee.info(format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        delegee.info(format, arguments);
    }

    @Override
    public <E extends Exception> void info(LoggerConsumer<E> consumer)
            throws E {
        delegee.info(consumer);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegee.isWarnEnabled();
    }

    @Override
    public void warn(String message) {
        delegee.warn(message);
    }

    @Override
    public void warn(String format, Object arg) {
        delegee.warn(format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        delegee.warn(format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        delegee.warn(format, arguments);
    }

    @Override
    public <E extends Exception> void warn(LoggerConsumer<E> consumer)
            throws E {
        delegee.warn(consumer);
    }

    @Override
    public boolean isErrorEnabled() {
        return delegee.isErrorEnabled();
    }

    @Override
    public void error(String message) {
        delegee.error(message);
    }

    @Override
    public void error(String format, Object arg) {
        delegee.error(format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        delegee.error(format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        delegee.error(format, arguments);
    }

    @Override
    public <E extends Exception> void error(LoggerConsumer<E> consumer)
            throws E {
        delegee.error(consumer);
    }

    @Override
    public void audit(String message) {
        delegee.audit(message);
    }

    @Override
    public void audit(String format, Object arg) {
        delegee.audit(format, arg);
    }

    @Override
    public void audit(String format, Object arg1, Object arg2) {
        delegee.audit(format, arg1, arg2);
    }

    @Override
    public void audit(String format, Object... arguments) {
        delegee.audit(format, arguments);
    }

}
