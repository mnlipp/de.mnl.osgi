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

/**
 * A bundle that forwards java.util.logging (JUL) records to the
 * OSGi logging service.
 * <P>
 * This bundle (the main bundle) works together with the 
 * bundle {@code de.mnl.osgi.jul2osgi.lib} (the library bundle), 
 * which provides a replacement for the default 
 * {@link java.util.logging.LogManager}.
 * Replacing the {@code LogManager} has the advantage that
 * all interactions with JUL can be intercepted.
 * <P>
 * The library bundle must be put on the framework
 * classpath by the launcher (e.g. with the {@code -runpath} instruction 
 * when using
 * <a href="https://bnd.bndtools.org/instructions/runpath.html">bnd</a>), 
 * because the Java runtime accesses the {@code LogManager} early during
 * application boot. The replacement {@code LogManager} is made known to JUL 
 * by invoking the JVM with the corresponding system property: "{@code java 
 * -Djava.util.logging.manager=de.mnl.osgi.jul2osgi.lib.LogManager ...}".
 * Usually, such system properties can be configured in the OSGi launcher
 * (e.g. {@code bnd}: {@code -runvm: ...}). Until the main bundle is started,
 * the {@code LogManager} buffers all JUL {@link java.util.logging.LogRecord}.
 * The size of the buffer can be configured with the system property
 * {@code de.mnl.osgi.jul2osgi.bufferSize}, which defaults to 100.
 * <P>
 * When the main bundle is started, it first requests an OSGI log service 
 * from the framework. When this service becomes available, the forwarder 
 * in the main bundle registers a callback with the {@code LogManager}. 
 * The {@code LogManager} uses the callback to send all buffered and 
 * future JUL {@link java.util.logging.LogRecord}s
 * to the main bundle. The forwarder converts the information from the
 * {@code LogRecords} to calls of one of the methods of an OSGi
 * {@link org.osgi.service.log.Logger}.
 * <P>
 * JUL {@code LogRecord} properties are mapped in the following way:
 * <ul>
 *   <li>logger name: used as logger name when requesting the
 *       OSGi Logger.</li>
 *   <li>message: passed as message parameter (see below).</li>
 *   <li>level: used to choose the method to call. {@code FINE} is mapped
 *       to a call to {@code debug}, anything below is mapped to
 *       a call to {@code trace}.
 *   <li>thrown: passed to the OSGi logger, becomes the exception property
 *       of the OSGi {@code LogRecord}.</li>
 * </ul>
 *
 * When using JUL, the message passed to the JUL {@code Logger} isn't
 * necessarily what you see in your log. It is first used to lookup
 * a mapping in the {@link java.util.ResourceBundle} associated with
 * the {@code LogRecord}. Then, it is passed to a formatter that may
 * insert representations of parameters associated with the 
 * {@code LogRecord}. This processing is supposed to take place 
 * during the final processing, i.e. in a JUL 
 * {@link java.util.logging.Handler}.
 * <P>
 * The OSGi log service does not provide such sophisticated post-processing
 * of log entries. The message text and the parameters from the JUL
 * {@code LogRecord} are therefore processed before forwarding the message
 * to the OSGi {@code Logger}. In this respect, the forwarder behaves like a
 * JUL {@code Handler}.
 * <P>
 * Before passing the post-processed message to the OSGi {@code Logger}
 * the forwarder applies another formatting operation. It invokes
 * {@link java.text.MessageFormat#format(String, Object...)} with
 * a format string, the post-processed message and the remaining
 * information from the JUL {@code LogRecord}. This allows the remaining
 * information to be added to the message sent to the OSGi log service.
 * <P>
 * The {@code format} method is invoked as: {@code format(logPattern,
 * message, millis, sequenceNumber, sourceClassName, sourceMethodName,
 * threadID)}. In order to e.g. add the source class name and method
 * to the log message the pattern "<code>{3}.{4}: {0}</code>" could be used.
 * <P>
 * The following bundle properties are used to configure the described 
 * behavior.
 * 
 * <table summary="Bundle parameters">
 *   <thead>
 *     <tr>
 *       <th>Property</th><th>Description</th><th>Default</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code de.mnl.osgi.jul2osgi.logPattern}</td>
 *       <td>The final formating pattern</td>
 *       <td><code>{0}</code></td>
 *     </tr>
 *     <tr>
 *       <td>{@code de.mnl.osgi.jul2osgi.contextLevel}</td>
 *       <td>Each OSGi {@code Logger} has a log level that prevents
 *           method invocations associated with lower levels from 
 *           effectively creating a record in the log service. By 
 *           default the forwarder sets the level to
 *           {@code TRACE}, assuming that log level management
 *           is done with JUL. It may be set to any valid log level
 *           or "{@code NONE}". The latter value prevents the forwarder
 *           from setting a log level at all and log levels for the
 *           messages from JUL must be managed by some other means
 *           (e.g. ConfigAdmin).</td>
 *       <td>{@code TRACE}</td>
 *     </tr>
 *   </tbody>
 * </table>
 * 
 */

package de.mnl.osgi.jul2osgi;