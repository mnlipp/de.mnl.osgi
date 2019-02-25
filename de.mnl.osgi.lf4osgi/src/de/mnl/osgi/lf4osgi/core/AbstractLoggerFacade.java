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

import org.osgi.framework.Bundle;
import org.osgi.service.log.LoggerFactory;

/**
 * The base class for OSGi logger facades.
 */
public abstract class AbstractLoggerFacade<T extends LoggerFacade>
        implements LoggerFacade {

    private final String name;
    private final LoggerGroup group;

    /**
     * Instantiates a new logger facade. The invoking bundle is determined
     * from the class that invoked {@code getLogger}. This class is searched 
     * for in the stacktrace as caller of the {@code getLogger} method of 
     * the class that provides the loggers (the logger factory) from the 
     * users point of view.
     * <P>
     * For LF4OSGi loggers the provider is always 
     * {@link de.mnl.osgi.lf4osgi.LoggerFactory}. But the name is taken 
     * from the  parameter in order to allow other facades to use this 
     * class as base class.
     * <P>
     * The new logger is automatically registered with the 
     * {@link LoggerFacadeManager}.
     *
     * @param group the logger group
     * @param name the name
     */
    public AbstractLoggerFacade(LoggerGroup group, String name) {
        this.group = group;
        this.name = name;
        LoggerFacadeManager.registerFacade(this);
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the bundle.
     *
     * @return the bundle
     */
    protected Bundle getBundle() {
        return group.getBundle();
    }

    /**
     * Called when the logger factory changes. Derived classes
     * must update the logger that they had previously obtained.
     *
     * @param factory the factory
     */
    public abstract void loggerFactoryUpdated(LoggerFactory factory);
}
