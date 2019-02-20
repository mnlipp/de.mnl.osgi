/**
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

package org.slf4j.impl;

import de.mnl.osgi.lf4osgi.provider.AbstractLoggerFacade;

import org.osgi.service.log.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * The Class Fwd2OsgiLogger.
 */
@SuppressWarnings({ "PMD.TooManyMethods", "PMD.ExcessivePublicCount" })
public class Fwd2OsgiLogger extends AbstractLoggerFacade implements Logger {

    @SuppressWarnings("PMD.LoggerIsNotStaticFinal")
    private org.osgi.service.log.Logger delegee;

    /**
     * Instantiates a new logger with the provided name.
     *
     * @param name the name
     */
    public Fwd2OsgiLogger(String name) {
        super(name);
    }

    @Override
    public void loggerFactoryUpdated(LoggerFactory factory) {
        delegee = factory.getLogger(getName());
    }

    @Override
    public boolean isTraceEnabled() {
        return delegee.isTraceEnabled();
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
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
    public void trace(String format, Object arg) {
        delegee.trace(format, arg);
    }

    @Override
    public void trace(String message, Throwable arg) {
        delegee.trace(message, arg);
    }

    @Override
    public void trace(String message) {
        delegee.trace(message);
    }

    @Override
    public void trace(Marker marker, String msg) {
        trace(msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        trace(format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        trace(format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... arguments) {
        trace(format, arguments);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable thr) {
        trace(msg, thr);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegee.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
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
    public void debug(String format, Object arg) {
        delegee.debug(format, arg);
    }

    @Override
    public void debug(String message, Throwable arg1) {
        delegee.debug(message, arg1);
    }

    @Override
    public void debug(String message) {
        delegee.debug(message);
    }

    @Override
    public void debug(Marker marker, String msg) {
        debug(msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        debug(format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        debug(format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        debug(format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable thr) {
        debug(msg, thr);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegee.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
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
    public void info(String format, Object arg) {
        delegee.info(format, arg);
    }

    @Override
    public void info(String message, Throwable arg1) {
        delegee.info(message, arg1);
    }

    @Override
    public void info(String message) {
        delegee.info(message);
    }

    @Override
    public void info(Marker marker, String msg) {
        info(msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        info(format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        info(format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        info(format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable thr) {
        info(msg, thr);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegee.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        delegee.warn(format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arg1) {
        delegee.warn(format, arg1);
    }

    @Override
    public void warn(String format, Object arg) {
        delegee.warn(format, arg);
    }

    @Override
    public void warn(String message, Throwable arg1) {
        delegee.warn(message, arg1);
    }

    @Override
    public void warn(String message) {
        delegee.warn(message);
    }

    @Override
    public void warn(Marker marker, String msg) {
        warn(msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        warn(format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        warn(format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        warn(format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable thr) {
        warn(msg, thr);
    }

    @Override
    public boolean isErrorEnabled() {
        return delegee.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
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
    public void error(String format, Object arg) {
        delegee.error(format, arg);
    }

    @Override
    public void error(String message, Throwable arg1) {
        delegee.error(message, arg1);
    }

    @Override
    public void error(String message) {
        delegee.error(message);
    }

    @Override
    public void error(Marker marker, String msg) {
        error(msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        error(format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        error(format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        error(format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable thr) {
        error(msg, thr);
    }

}
