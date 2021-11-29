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
 * A bundle that forwards events from the OSGi logging service
 * to java.util.logging (JUL).
 * <P>
 * <a href="https://github.com/mnlipp/de.mnl.osgi/issues" target="_blank">
 *   <img alt="GitHub issues" src="https://img.shields.io/github/issues/mnlipp/de.mnl.osgi.svg"></a>
 * <a href="https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.osgi2jul%22" target="_blank">
 *   <img alt="Maven Central" src="https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.osgi2jul.svg"></a>
 * 
 * <h2>Installation</h2>
 * 
 * The bundle is deployed as any other OSGi bundle
 * 
 * <h2>Forwarding functionality</h2>
 * 
 * The bundle installs a {@link org.osgi.service.log.LogListener}
 * on every discovered {@link org.osgi.service.log.LogService}.
 * The listener forwards each received {@link org.osgi.service.log.LogEntry}
 * to the configured JUL {@link java.util.logging.Handler}s.
 * <P>
 * The mapping of JUL {@code LogEntry} properties to OSGi
 * {@code LogRecord} properties should be obvious with two
 * exceptions:
 * <ul>
 *   <li>{@link org.osgi.service.log.LogLevel#AUDIT}: is mapped
 *       to a JUL {@link java.util.logging.Level} with integer value
 *       {@code MAX_VALUE} .</li>
 *   <li>Bundle: as this information has no direct representation
 *       in the JUL {@code LogRecord}, it can be inserted into
 *       the log message (see below).</li>
 * </ul>
 * If a {@code format} property is set for a handler, the log message
 * is post-processed with a {@link java.text.MessageFormat} instance
 * created from the specified {@code format}. The formatter is invoked
 * with the parameters "original message", "bundle symbolic name",
 * "bundle name", "bundle version", "thread info".
 * <P>
 * In order to e.g. add the bundle name to the message, the
 * format pattern "<code>{0} [{2}]</code>" could be used.
 * 
 * <h2>Bundle properties</h2>
 * 
 * The following bundle properties configure the described 
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
 *       <td>{@code de.mnl.osgi.osgi2jul.handlers}</td>
 *       <td>A comma separated list of {@link java.util.logging.Handler}
 *           classes. If the handlers are to be taken from a bundle,
 *           use "{@code <bundle symbolic name>:<class name>}".
 *           For further configuration, an id can be appended as
 *           "{@code [<id>]}".</td>
 *       <td><code>{@link java.util.logging.ConsoleHandler}</code></td>
 *     </tr>
 *     <tr>
 *       <td>{@code de.mnl.osgi.osgi2jul.handler.<id>.format}</td>
 *       <td>The format used to post-process messages
 *           before passing them to the handler (see above).</td>
 *       <td>No post-processing</td>
 *     </tr>
 *     <tr>
 *       <td>{@code de.mnl.osgi.osgi2jul.handler.<id>.level}</td>
 *       <td>Used to set the level property of the handler.</td>
 *       <td>None</td>
 *     </tr>
 *   </tbody>
 * </table>
 * 
 */

package de.mnl.osgi.osgi2jul;