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

import java.util.Arrays;

import org.osgi.framework.Bundle;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

/**
 * Provides a container for the buffered event information.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class BufferedEvent {

    private static final Object[] EMPTY_ARRAY = new Object[0];
    private final Bundle bundle;
    private final String name;
    private final LogLevel level;
    private final String message;
    private final Object[] arguments;

    /**
     * Instantiates a new buffered event.
     *
     * @param bundle the bundle
     * @param name the name
     * @param level the level
     * @param message the message
     * @param arguments the parameters
     */
    public BufferedEvent(Bundle bundle, String name, LogLevel level,
            String message, Object... arguments) {
        this.bundle = bundle;
        this.name = name;
        this.level = level;
        this.message = message;
        this.arguments = Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * Instantiates a new buffered event.
     *
     * @param bundle the bundle
     * @param name the name
     * @param level the level
     * @param message the message
     */
    public BufferedEvent(Bundle bundle, String name, LogLevel level,
            String message) {
        this.bundle = bundle;
        this.name = name;
        this.level = level;
        this.message = message;
        arguments = EMPTY_ARRAY;
    }

//    /**
//     * Forward the log event using the provided logger factory.
//     *
//     * @param factory the factory
//     */
//    public void forward(LoggerFactory factory) {
//        final Logger logger = factory.getLogger(bundle, name, Logger.class);
//        switch (level) {
//        case TRACE:
//            logger.trace(l -> l.trace(message, arguments));
//            break;
//        case DEBUG:
//            logger.debug(l -> l.debug(message, arguments));
//            break;
//        case INFO:
//            logger.info(l -> l.info(message, arguments));
//            break;
//        case WARN:
//            logger.warn(l -> l.warn(message, arguments));
//            break;
//        case ERROR:
//            logger.error(l -> l.error(message, arguments));
//            break;
//        case AUDIT:
//            logger.audit(message, arguments);
//            break;
//        }
//    }

    /**
     * Forward the log event using the provided logger factory.
     *
     * @param factory the factory
     */
    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    public void forward(LoggerFactory factory) {
        final Logger logger = factory.getLogger(bundle, name, Logger.class);
        switch (level) {
        case TRACE:
            if (arguments.length == 0) {
                logger.trace(l -> l.trace(message));
            } else {
                logger.trace(l -> l.trace(message, arguments));
            }
            break;
        case DEBUG:
            if (arguments.length == 0) {
                logger.debug(l -> l.debug(message));
            } else {
                logger.debug(l -> l.debug(message, arguments));
            }
            break;
        case INFO:
            if (arguments.length == 0) {
                logger.info(l -> l.info(message));
            } else {
                logger.info(l -> l.info(message, arguments));
            }
            break;
        case WARN:
            if (arguments.length == 0) {
                logger.warn(l -> l.warn(message));
            } else {
                logger.warn(l -> l.warn(message, arguments));
            }
            break;
        case ERROR:
            if (arguments.length == 0) {
                logger.error(l -> l.error(message));
            } else {
                logger.error(l -> l.error(message, arguments));
            }
            break;
        case AUDIT:
            if (arguments.length == 0) {
                logger.audit(message);
            } else {
                logger.audit(message, arguments);
            }
            break;
        }
    }

}
