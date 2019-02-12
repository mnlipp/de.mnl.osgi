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

package de.mnl.osgi.osgi2jul;

import java.text.MessageFormat;
import java.util.logging.Handler;

/**
 * Holds the configuration for a handler.
 */
public class HandlerConfig {

    private final Handler handler;
    private final MessageFormat outputFormat;

    /**
     * Instantiates a new handler configuration.
     *
     * @param handler the handler
     * @param outputFormat the output format
     */
    public HandlerConfig(Handler handler, MessageFormat outputFormat) {
        super();
        this.handler = handler;
        this.outputFormat = outputFormat;
    }

    /**
     * Gets the handler.
     *
     * @return the handler
     */
    public Handler getHandler() {
        return handler;
    }

    /**
     * Gets the output format.
     *
     * @return the outputFormat
     */
    public MessageFormat getOutputFormat() {
        return outputFormat;
    }

}
