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

package de.mnl.osgi.log4j2osgi;

import de.mnl.osgi.lf4osgi.provider.LoggerFacade;
import de.mnl.osgi.lf4osgi.provider.LoggerFacadeManager;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.osgi.service.log.LoggerFactory;

public class OsgiLogger extends AbstractLogger implements LoggerFacade {
    private static final long serialVersionUID = 1L;
    private OsgiLoggerContext context;
    private org.osgi.service.log.Logger delegee;

    public OsgiLogger(OsgiLoggerContext context, final String name,
            final MessageFactory messageFactory) {
        super(name, messageFactory);
        this.context = context;
        LoggerFacadeManager.registerFacade(this);
    }

    public OsgiLogger(OsgiLoggerContext context, final String name) {
        super(name);
        this.context = context;
        LoggerFacadeManager.registerFacade(this);
    }

    @Override
    public void loggerFactoryUpdated(LoggerFactory factory) {
        delegee = factory.getLogger(context.getBundle(), name,
            org.osgi.service.log.Logger.class);
    }

    @Override
    public Level getLevel() {
        if (delegee.isTraceEnabled()) {
            return Level.TRACE;
        }
        if (delegee.isDebugEnabled()) {
            return Level.DEBUG;
        }
        if (delegee.isInfoEnabled()) {
            return Level.INFO;
        }
        if (delegee.isWarnEnabled()) {
            return Level.WARN;
        }
        if (delegee.isErrorEnabled()) {
            return Level.ERROR;
        }
        // Option: throw new IllegalStateException("Unknown SLF4JLevel");
        // Option: return Level.ALL;
        return Level.OFF;
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final Message data, final Throwable thr) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final CharSequence data, final Throwable thr) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final Object data, final Throwable thr) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String data) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String data, final Object... p1) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8, final Object p9) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker,
            final String data, final Throwable thr) {
        return isEnabledFor(level, marker);
    }

    private boolean isEnabledFor(final Level level, final Marker marker) {
        switch (level.getStandardLevel()) {
        case DEBUG:
            return delegee.isDebugEnabled();
        case TRACE:
            return delegee.isTraceEnabled();
        case INFO:
            return delegee.isInfoEnabled();
        case WARN:
            return delegee.isWarnEnabled();
        case ERROR:
            return delegee.isErrorEnabled();
        default:
            return delegee.isErrorEnabled();
        }
    }

    @Override
    public void logMessage(final String fqcn, final Level level,
            final Marker marker, final Message message, final Throwable thr) {
        switch (level.getStandardLevel()) {
        case TRACE:
            delegee.trace(message.getFormattedMessage(),
                message.getParameters(), thr);
            break;
        case DEBUG:
            delegee.debug(message.getFormattedMessage(),
                message.getParameters(), thr);
            break;
        case INFO:
            delegee.info(message.getFormattedMessage(),
                message.getParameters(), thr);
            break;
        case WARN:
            delegee.warn(message.getFormattedMessage(),
                message.getParameters(), thr);
            break;
        case ERROR:
            delegee.error(message.getFormattedMessage(),
                message.getParameters(), thr);
            break;
        default:
            delegee.error(message.getFormattedMessage(),
                message.getParameters(), thr);
            break;

        }
    }

}
