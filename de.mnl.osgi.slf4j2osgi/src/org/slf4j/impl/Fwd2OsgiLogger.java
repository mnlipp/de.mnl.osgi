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

import de.mnl.osgi.lf4osgi.provider.LoggerFacade;
import de.mnl.osgi.lf4osgi.provider.LoggerFacadeManager;

import org.osgi.framework.Bundle;
import org.osgi.service.log.LoggerFactory;
import org.slf4j.helpers.MarkerIgnoringBase;

/**
 * The Class Fwd2OsgiLogger.
 */
@SuppressWarnings({ "PMD.TooManyMethods", "PMD.ExcessivePublicCount" })
public class Fwd2OsgiLogger extends MarkerIgnoringBase implements LoggerFacade {
    private static final long serialVersionUID = -6844449574931434059L;

    private final Bundle bundle;
    private final String name;
    @SuppressWarnings("PMD.LoggerIsNotStaticFinal")
    private org.osgi.service.log.Logger delegee;

    /**
     * Instantiates a new logger with the provided name.
     *
     * @param bundle the bundle
     * @param name the name
     */
    public Fwd2OsgiLogger(Bundle bundle, String name) {
        this.bundle = bundle;
        this.name = name;
        LoggerFacadeManager.registerFacade(this);
    }

    @Override
    public void loggerFactoryUpdated(LoggerFactory factory) {
        delegee = factory.getLogger(bundle, name,
            org.osgi.service.log.Logger.class);
    }

    @Override
    public boolean isTraceEnabled() {
        return delegee.isTraceEnabled();
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
    public boolean isDebugEnabled() {
        return delegee.isDebugEnabled();
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
    public boolean isInfoEnabled() {
        return delegee.isInfoEnabled();
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
    public boolean isWarnEnabled() {
        return delegee.isWarnEnabled();
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
    public boolean isErrorEnabled() {
        return delegee.isErrorEnabled();
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

}
