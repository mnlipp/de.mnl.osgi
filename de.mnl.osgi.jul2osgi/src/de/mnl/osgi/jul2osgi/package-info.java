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
 * <a href="https://github.com/mnlipp/de.mnl.osgi/issues" target="_blank">
 *   <img alt="GitHub issues" src="https://img.shields.io/github/issues/mnlipp/de.mnl.osgi.svg"></a>
 * <a href="https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.jul2osgi%22" target="_blank">
 *   <img alt="Maven Central" src="https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.jul2osgi.svg"></a>
 * 
 * <h2>Installation</h2>
 * 
 * This bundle (the main bundle) works together with the 
 * bundle {@code de.mnl.osgi.jul2osgi.lib} (the library bundle), 
 * which provides a replacement for the default 
 * {@link java.util.logging.LogManager}.
 * Replacing the {@code LogManager} has the advantage that
 * all interactions with JUL can be intercepted.
 * <P>
 * The library bundle must be put on the JVM bootstrap classpath or the
 * framework classpath by the launcher because the Java runtime accesses the 
 * {@code LogManager} early during
 * application boot. The replacement {@code LogManager} is made known to JUL 
 * by invoking the JVM with the corresponding system property: "{@code java 
 * -Djava.util.logging.manager=de.mnl.osgi.jul2osgi.lib.LogManager ...}".
 * Usually, such system properties can be configured in the OSGi launcher.
 * Until the main bundle is started,
 * the {@code LogManager} records all JUL {@link java.util.logging.LogRecord}
 * in a buffer. The size of the buffer can be configured with the system 
 * property {@code de.mnl.osgi.jul2osgi.bufferSize}, which defaults to 100.
 * <P>
 * The main bundle is deployed as any other OSGi bundle
 * 
 * <h3>bnd launcher before 4.3.0</h3>
 * 
 * The library bundle can be put on the framework
 * classpath with the {@code -runpath} instruction 
 * (<a href="https://bnd.bndtools.org/instructions/runpath.html">bnd</a>), 
 * the property is set with the {@code -runvm: ...} instruction. 
 * <P>
 * Example configuration:
 * <pre> -runpath: de.mnl.osgi.jul2osgi.lib;version='latest'
 * -runvm: -Djava.util.logging.manager=de.mnl.osgi.jul2osgi.lib.LogManager</pre>
 * 
 * <h3>bnd launcher starting with 4.3.0</h3>
 * 
 * With 4.3.0 the handling of the {@code -runpath} instruction 
 * <a href="https://groups.google.com/forum/#!topic/bndtools-users/-GkCoepqbTk">has 
 * changed</a>. The library must therefore be added to the bootstrap classpath.
 * Because this does not add the exported packages to the system capabilities,
 * this must be done explicitly.
 * <P>
 * Example configuration:
 * <pre> -runvm: -Xbootclasspath/a:${repo;de.mnl.osgi.jul2osgi.lib;latest}, \
 *   -Djava.util.logging.manager=de.mnl.osgi.jul2osgi.lib.LogManager
 * -runsystempackages: \
 *   de.mnl.osgi.jul2osgi.lib; version=1.4.2</pre>
 * 
 * <h2>Forwarding functionality</h2>
 * 
 * When the main bundle is started, it first requests an OSGI log service 
 * from the framework. When this service becomes available, the forwarder 
 * in the main bundle registers a callback with the {@code LogManager}. 
 * The {@code LogManager} uses the callback to send all recorded and 
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
 * The forwarder also attempts to determine the bundle within which
 * the JUL {@code Logger} was created and use it as origin of the event 
 * when forwarding to OSGI logging. Only if determining the bundle
 * fails will the log event appear to have been created by bundle
 * {@code de.mnl.osgi.jul2osgi}.
 * <P>
 * Special handling is applied by the forwarder if <i>recorded</i> log 
 * entries are forwarded to the OSGi {@link org.osgi.service.log.Logger}. 
 * The implementation of the logger automatically derives a "thread info" 
 * property for the newly created OSGI {@code LogRecord} from the calling 
 * thread. There is no way to override this automatically created value, 
 * which is obviously wrong when a thread forwards log events that have
 * been recorded while executing other threads. Therefore the current
 * thread's name is added to the recorded information. When flushing the
 * recorded log events later, the name of the flushing thread is 
 * temporarily set to the recorded name with "{@code [recorded]}"
 * appended.
 * <P>
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
 * The {@code format} method is invoked as: 
 * <P>
 * {@code format(logPattern,
 * message, millis, sequenceNumber, sourceClassName, sourceMethodName,
 * threadID)}
 * <P>
 * In order to e.g. add the source class name and method
 * to the log message the pattern "<code>{3}.{4}: {0}</code>" could be used.
 * 
 * <h2>Filtering</h2>
 * 
 * Calls to the JUL {@code Logger} are filtered before further processing
 * according to the result from (JUL)
 * {@link java.util.logging.Logger#isLoggable(java.util.logging.Level)} and
 * by any configured JUL filter. Calls that pass this barrier are 
 * forwarded to OSGi logging.
 * <P>
 * OSGi logging filters the forwarded events according to the level
 * configured for the originating bundle and OSGi logger before accepting
 * the event.
 * <P>
 * If you want log events to be delivered for levels lower than the 
 * default levels, you must therefore lower the levels in both configurations. 
 * Be aware that the default levels for JUL and OSGi logging differ. JUL
 * uses the default level {@code INFO} while OSGi logging uses the default 
 * level {@code WARNING}.
 * <P>
 * In order to avoid unnecessary processing of eventually discarded log
 * events, filtering should preferably be configured using JUL. Anything
 * that passes this first barrier can then be accepted by OSGi logging
 * without further restrictions. Following this approach, the default
 * level for bundles using JUL should thus best be set to 
 * {@link org.osgi.service.log.LogLevel#TRACE} (see bundle property below).
 * 
 * <h2>Bundle properties</h2>
 * 
 * The following bundle properties are used to configure the described 
 * behavior.
 * 
 * <table>
 *   <caption>Bundle parameters</caption>
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
 *       <td>{@code de.mnl.osgi.jul2osgi.adaptOSGiLevel}</td>
 *       <td>If set to {@code true}, the OSGi log level for the 
 *           originating bundle of a JUL log event will automatically
 *           ne set to {@link org.osgi.service.log.LogLevel#TRACE}.
 *           This results in the expected behavior that any log 
 *           message that has been enabled in JUL is visible in the
 *           OSGi log.</td>
 *       <td>{@code true}</td>
 *     </tr>
 *   </tbody>
 * </table>
 * 
 */

package de.mnl.osgi.jul2osgi;