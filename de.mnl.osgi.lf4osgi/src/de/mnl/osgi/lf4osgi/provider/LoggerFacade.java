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

import org.osgi.service.log.LoggerFactory;

/**
 * The interface that must be implemented by logger facades.
 */
public interface LoggerFacade {

    /**
     * Called when the logger factory changes. Implementing classes
     * must update the logger that they had previously obtained.
     *
     * @param factory the factory
     */
    void loggerFactoryUpdated(LoggerFactory factory);

}
