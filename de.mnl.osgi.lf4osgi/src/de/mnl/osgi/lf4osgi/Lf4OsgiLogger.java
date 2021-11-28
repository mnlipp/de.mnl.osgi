/*
 * Copyright (C) 2019-2021 Michael N. Lipp (http://www.mnl.de)
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

import de.mnl.osgi.lf4osgi.core.AbstractLoggerFacade;
import de.mnl.osgi.lf4osgi.core.LoggerGroup;
import java.util.function.Supplier;
import org.osgi.service.log.LoggerConsumer;
import org.osgi.service.log.LoggerFactory;

/**
 * The implementation of the facade for OSGi loggers. This type should not be
 * used by consumers of this API. Rather, declare your loggers to be of type
 * (OSGi) {@link org.osgi.service.log.Logger}, if you want the OSGi API only, or
 * of type {@link Logger} (from this package) if you want to use the message
 * supplying closures.
 */
@SuppressWarnings({ "PMD.TooManyMethods", "PMD.ExcessivePublicCount" })
public class Lf4OsgiLogger extends AbstractLoggerFacade<Lf4OsgiLogger> implements Logger {

	@SuppressWarnings("PMD.LoggerIsNotStaticFinal")
	private org.osgi.service.log.Logger delegee;

	/**
	 * Instantiates a new logger for the given bundle with the provided name.
	 *
	 * @param context the context
	 * @param name    the name
	 */
	public Lf4OsgiLogger(LoggerGroup context, String name) {
		super(context, name);
	}

	@Override
	public void loggerFactoryUpdated(LoggerFactory factory) {
		delegee = factory.getLogger(getBundle(), getName(), Logger.class);
	}

	@Override
	public boolean isTraceEnabled() {
		return delegee.isTraceEnabled();
	}

	@Override
	public void trace(String message) {
		delegee.trace(message);
	}

	@Override
	public void trace(String format, Object arg) {
		delegee.trace(format, arg);
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		delegee.trace(format, arg1, arg2);
	}

	@Override
	public void trace(String format, Object... arguments) {
		delegee.trace(format, arguments);
	}

	@Override
	public <E extends Exception> void trace(LoggerConsumer<E> consumer) throws E {
		delegee.trace(consumer);
	}

	@Override
	public void trace(Supplier<String> messageSupplier) {
		if (delegee.isTraceEnabled()) {
			delegee.trace(messageSupplier.get());
		}
	}

	@Override
	public void trace(Supplier<String> messageSupplier, Throwable thr) {
		if (delegee.isTraceEnabled()) {
			delegee.trace(messageSupplier.get(), thr);
		}
	}

	@Override
	public boolean isDebugEnabled() {
		return delegee.isDebugEnabled();
	}

	@Override
	public void debug(String message) {
		delegee.debug(message);
	}

	@Override
	public void debug(String format, Object arg) {
		delegee.debug(format, arg);
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		delegee.debug(format, arg1, arg2);
	}

	@Override
	public void debug(String format, Object... arguments) {
		delegee.debug(format, arguments);
	}

	@Override
	public <E extends Exception> void debug(LoggerConsumer<E> consumer) throws E {
		delegee.debug(consumer);
	}

	@Override
	public void debug(Supplier<String> messageSupplier) {
		if (delegee.isDebugEnabled()) {
			delegee.debug(messageSupplier.get());
		}
	}

	@Override
	public void debug(Supplier<String> messageSupplier, Throwable thr) {
		if (delegee.isDebugEnabled()) {
			delegee.debug(messageSupplier.get(), thr);
		}
	}

	@Override
	public boolean isInfoEnabled() {
		return delegee.isInfoEnabled();
	}

	@Override
	public void info(String message) {
		delegee.info(message);
	}

	@Override
	public void info(String format, Object arg) {
		delegee.info(format, arg);
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		delegee.info(format, arg1, arg2);
	}

	@Override
	public void info(String format, Object... arguments) {
		delegee.info(format, arguments);
	}

	@Override
	public <E extends Exception> void info(LoggerConsumer<E> consumer) throws E {
		delegee.info(consumer);
	}

	@Override
	public void info(Supplier<String> messageSupplier) {
		if (delegee.isInfoEnabled()) {
			delegee.info(messageSupplier.get());
		}
	}

	@Override
	public void info(Supplier<String> messageSupplier, Throwable thr) {
		if (delegee.isInfoEnabled()) {
			delegee.info(messageSupplier.get(), thr);
		}
	}

	@Override
	public boolean isWarnEnabled() {
		return delegee.isWarnEnabled();
	}

	@Override
	public void warn(String message) {
		delegee.warn(message);
	}

	@Override
	public void warn(String format, Object arg) {
		delegee.warn(format, arg);
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		delegee.warn(format, arg1, arg2);
	}

	@Override
	public void warn(String format, Object... arguments) {
		delegee.warn(format, arguments);
	}

	@Override
	public void warn(Supplier<String> messageSupplier) {
		if (delegee.isWarnEnabled()) {
			delegee.warn(messageSupplier.get());
		}
	}

	@Override
	public <E extends Exception> void warn(LoggerConsumer<E> consumer) throws E {
		delegee.warn(consumer);
	}

	@Override
	public void warn(Supplier<String> messageSupplier, Throwable thr) {
		if (delegee.isWarnEnabled()) {
			delegee.warn(messageSupplier.get(), thr);
		}
	}

	@Override
	public boolean isErrorEnabled() {
		return delegee.isErrorEnabled();
	}

	@Override
	public void error(String message) {
		delegee.error(message);
	}

	@Override
	public void error(String format, Object arg) {
		delegee.error(format, arg);
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		delegee.error(format, arg1, arg2);
	}

	@Override
	public void error(String format, Object... arguments) {
		delegee.error(format, arguments);
	}

	@Override
	public <E extends Exception> void error(LoggerConsumer<E> consumer) throws E {
		delegee.error(consumer);
	}

	@Override
	public void error(Supplier<String> messageSupplier) {
		if (delegee.isErrorEnabled()) {
			delegee.error(messageSupplier.get());
		}
	}

	@Override
	public void error(Supplier<String> messageSupplier, Throwable thr) {
		if (delegee.isErrorEnabled()) {
			delegee.error(messageSupplier.get(), thr);
		}
	}

	@Override
	public void audit(String message) {
		delegee.audit(message);
	}

	@Override
	public void audit(String format, Object arg) {
		delegee.audit(format, arg);
	}

	@Override
	public void audit(String format, Object arg1, Object arg2) {
		delegee.audit(format, arg1, arg2);
	}

	@Override
	public void audit(String format, Object... arguments) {
		delegee.audit(format, arguments);
	}

}
