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

import java.util.function.Supplier;

/**
 * An extended OSGi {@link org.osgi.service.log.Logger} interface.
 * It provides the additional methods that accept a {@link Supplier}
 * for the message as a parameter. This allows you to write
 * statements such as:
 * <pre>
 * logger.warn(() -&gt; String.format("Value is %d.", 42));
 * </pre>
 */
public interface Logger extends org.osgi.service.log.Logger {

    /**
     * If trace level is enabled, get the message from the supplier
     * and log it.
     *
     * @param messageSupplier the message supplier
     */
    void trace(Supplier<String> messageSupplier);

    /**
     * If trace level is enabled, get the message from the supplier
     * and log it together withe the provided throwable.
     *
     * @param messageSupplier the message supplier
     * @param thr the Throwable
     */
    void trace(Supplier<String> messageSupplier, Throwable thr);

    /**
     * If debug level is enabled, get the message from the supplier
     * and log it.
     *
     * @param messageSupplier the message supplier
     */
    void debug(Supplier<String> messageSupplier);

    /**
     * If debug level is enabled, get the message from the supplier
     * and log it together withe the provided throwable.
     *
     * @param messageSupplier the message supplier
     * @param thr the Throwable
     */
    void debug(Supplier<String> messageSupplier, Throwable thr);

    /**
     * If info level is enabled, get the message from the supplier
     * and log it.
     *
     * @param messageSupplier the message supplier
     */
    void info(Supplier<String> messageSupplier);

    /**
     * If info level is enabled, get the message from the supplier
     * and log it together withe the provided throwable.
     *
     * @param messageSupplier the message supplier
     * @param thr the Throwable
     */
    void info(Supplier<String> messageSupplier, Throwable thr);

    /**
     * If warn level is enabled, get the message from the supplier
     * and log it.
     *
     * @param messageSupplier the message supplier
     */
    void warn(Supplier<String> messageSupplier);

    /**
     * If warn level is enabled, get the message from the supplier
     * and log it together withe the provided throwable.
     *
     * @param messageSupplier the message supplier
     * @param thr the Throwable
     */
    void warn(Supplier<String> messageSupplier, Throwable thr);

    /**
     * If error level is enabled, get the message from the supplier
     * and log it.
     *
     * @param messageSupplier the message supplier
     */
    void error(Supplier<String> messageSupplier);

    /**
     * If error level is enabled, get the message from the supplier
     * and log it together withe the provided throwable.
     *
     * @param messageSupplier the message supplier
     * @param thr the Throwable
     */
    void error(Supplier<String> messageSupplier, Throwable thr);

}
