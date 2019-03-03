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
 * This package provides a binding of the SLF4J to OSGi logging. The
 * implementation uses the same core code as the Logging Facade for
 * OSGi (LF4OSGi). It's just another type of logger that acts as
 * facade for the OSGi logger.
 * <P>
 * Simply install this bundle in addition to de.mnl.osgi.lf4j and you
 * can use the SLF4J {@code LoggerFactory} as you're used to. As this
 * implementation shares everything "behind the facade" with LF4OSGi,
 * it also shares its configuration options.
 * <P>
 * If you want to use automatic resolution of bundles, make sure to
 * exclude any other providers of the {@code org.slf4j.impl}. E.g.:
 * <pre>
 * # - SLF4J 1.8 is still alpha and backward compatibility is unclear
 * # - Make sure that the import "org.slf4j.impl;version=1.6.0" is only 
 * #   satisfied by de.mnl.osgi.slf4j2osgi.
 * -runblacklist: \
 *     osgi.identity;filter:='(&amp;(osgi.identity=slf4j.api)(version&gt;=1.8.0))',\
 *     osgi.identity;filter:='(osgi.identity=slf4j.simple)',\
 *     osgi.identity;filter:='(osgi.identity=slf4j.log4j12)',\
 *     ... (more implementation bundles in your repositories)
 * </pre>
 */
@Version("1.6.0") // API requires an impl with at least this version
package org.slf4j.impl;

import org.osgi.annotation.versioning.Version;
