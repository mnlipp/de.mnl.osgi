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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkUtil;

/**
 * A manager for groups of logger depending on a bundle. Logger group
 * for a given bundle are discarded when a bundle is uninstalled.
 *
 * @param <T> the logger group type
 */
public class LoggerCatalogue<T> {

    private static final ContextHelper CTX_HLPR = new ContextHelper();

    private final Map<Bundle, T> groups = new ConcurrentHashMap<>();
    private final Function<Bundle, T> groupSupplier;

    /**
     * Instantiates a new logger catalogue.
     *
     * @param groupSupplier the supplier for new logger groups
     */
    public LoggerCatalogue(Function<Bundle, T> groupSupplier) {
        super();
        this.groupSupplier = groupSupplier;
        FrameworkUtil.getBundle(LoggerCatalogue.class).getBundleContext()
            .addBundleListener(new BundleListener() {
                @Override
                public void bundleChanged(BundleEvent event) {
                    if (event.getType() == BundleEvent.UNINSTALLED) {
                        groups.remove(event.getBundle());
                    }
                }
            });
    }

    /**
     * Find the class that creates the logger. This is done by searching
     * through the current stack trace for the invocation of the
     * getLogger method of the class that provides loggers. The
     * next frame in the stack trace then reveals the name of the
     * class that requests the logger.
     *
     * @param providingClass the providing class
     * @return the optional
     */
    private static Optional<Class<?>>
            findRequestingClass(String providingClass) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            if (ste.getClassName().equals(providingClass)
                && ste.getMethodName().equals("getLogger")) {
                Class<?>[] classes = CTX_HLPR.getClassContext();
                // Next in stack should caller of getLogger(), but
                // getClassContext() has added one level, so the
                // caller of getLogger should be at i+2. But...
                // from the JavaDoc: "Some virtual machines may, under
                // some circumstances, omit one or more stack frames
                // from the stack trace." So let's make sure that we
                // are really there.
                while (!classes[i + 1].getName().equals(providingClass)) {
                    i += 1;
                }
                return Optional.of(classes[i + 2]);
            }
        }
        return Optional.empty();
    }

    /**
     * Find the bundle that contains the class that wants to get
     * a logger using the current call stack. 
     * <P>
     * The bundle is determined from the class that invoked getLogger,
     * which is searched for in the call stack as caller of the 
     * getLogger method of the class that provides the loggers from 
     * the users point of view.
     *
     * @param providingClass the providing class
     * @return the optional
     */
    public static Optional<Bundle> findBundle(String providingClass) {
        return findRequestingClass(providingClass)
            .map(cls -> FrameworkUtil.getBundle(cls));
    }

    /**
     * Returns the logger group for the given bundle.
     *
     * @param bundle the bundle
     * @return the logger group
     */
    public T getLoggerGoup(Bundle bundle) {
        return groups.computeIfAbsent(bundle, b -> groupSupplier.apply(b));
    }

    /**
     * The Class ContextHelper.
     */
    private static class ContextHelper extends SecurityManager {
        @Override
        @SuppressWarnings("PMD.UselessOverridingMethod")
        public Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }
}
