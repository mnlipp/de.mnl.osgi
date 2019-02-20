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
 * A bundle that provides a logging facade for the standard OSGI
 * {@link org.osgi.service.log.Logger}.
 * <P>
 * <a href="https://github.com/mnlipp/de.mnl.osgi/issues" target="_blank">
 *   <img alt="GitHub issues" src="https://img.shields.io/github/issues/mnlipp/de.mnl.osgi.svg"></a>
 * <a href="https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.lf4osgi%22" target="_blank">
 *   <img alt="Maven Central" src="https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.lf4osgi.svg"></a>
 * 
 * <h3>Installation</h3>
 * 
 * The bundle is deployed as any other OSGi bundle
 * 
 * <h3>Facade functionality</h3>
 * 
 * The OSGi Log Service has improved a lot with 
 * <a href="https://osgi.org/specification/osgi.cmpn/7.0.0/service.log.html">Version 1.4</a>.
 * Still, it is not as easy to use as the popular logging frameworks,
 * mainly because it lacks a static factory for loggers. In order to
 * get a logger, you first have to obtain a 
 * {@link de.mnl.osgi.lf4osgi.LoggerFactory} as service. Even with 
 * Declarative Services (or some other dependency managing framework), 
 * this makes the usage more cumbersome than you'd like it to be. And 
 * you still have to handle the problem that all loggers obtained become
 * invalid should the {@code LoggingFactory} service in use be replaced.
 * <P>
 * Yet another point is how to log when no {@code LoggingFactory} service
 * is available. This my be the case during startup.
 * <P>
 * This facade for OSGi logging overcomes these problems. The
 * tiny overhead to pay is an extra method invocation because the
 * {@link org.osgi.service.log.Logger} implementation provided as 
 * facade has to delegate to the logger provided by the 
 * {@code LoggingFactory} service (or a stand-in, see below). 
 * However, this should be neglectable under all circumstances,
 * especially if you use closures for logging.
 * <P>
 * While no {@code LoggingFactory} service is availabe, logging events
 * are stored in a buffer which is flushed to the service as soon as
 * it becomes available. The "thread info", however, cannot be 
 * forwarded to the service that becomes available, because it is
 * not an explicit parameter of the {@link org.osgi.service.log.Logger}'s
 * methods. Flushed log events therefore have the thread info from
 * the flushing thread. To make this obvious, the name
 * of is set to "{@code (log flusher)}" while the events are
 * flushed.
 * <P>
 * As long as there is no {@code LoggingFactory} service available,
 * the logging level cannot be determined. Therefore all events
 * with level {@link org.osgi.service.log.LogLevel#DEBUG} and up
 * are buffered. This can be changed using the bundle properties
 * (see below).
 * 
 * <h3>Bundle properties</h3>
 * 
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
 *       <td>{@code de.mnl.osgi.lf4osgi.bufferThreshold}</td>
 *       <td>The minimum {@link org.osgi.service.log.LogLevel}
 *       of messages that are to be buffered.</td>
 *       <td>{@link org.osgi.service.log.LogLevel#DEBUG}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code de.mnl.osgi.lf4osgi.bufferSize}</td>
 *       <td>The size of the buffer. If the buffer is full,
 *       the oldest event is discarded.</td>
 *       <td>100</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 */
package de.mnl.osgi.lf4osgi;