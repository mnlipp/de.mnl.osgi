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
 * A manager for groups of loggers associated with a bundle. A Logger 
 * group for a given bundle is automatically discarded when the 
 * associated bundle is uninstalled.
 *
 * @param <T> the logger group type
 */
public class LoggerCatalogue<T> {

    private static final ContextHelper CTX_HLPR = new ContextHelper();

    private boolean listenerInstalled;
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
        /*
         * This may be invoked from from anywhere, even a static context.
         * Therefore, it might not be possible to register the listener
         * immediately.
         */
        checkListener();
    }

    private void checkListener() {
        if (listenerInstalled) {
            return;
        }
        Optional.ofNullable(FrameworkUtil.getBundle(groupSupplier.getClass()))
            .map(Bundle::getBundleContext).ifPresent(ctx -> {
                ctx.addBundleListener(new BundleListener() {
                    @Override
                    public void bundleChanged(BundleEvent event) {
                        if (event.getType() == BundleEvent.UNINSTALLED) {
                            groups.remove(event.getBundle());
                        }
                    }
                });
                listenerInstalled = true;
                // This is unlikely to ever happen, but as this is delayed...
                for (Bundle bdl : groups.keySet()) {
                    if ((bdl.getState() & Bundle.UNINSTALLED) != 0) {
                        groups.remove(bdl);
                    }
                }
            });
    }

    /**
     * Find the class that attempts to get a logger. This is done 
     * by searching through the current call stack for the invocation 
     * of the {@code getLogger} method of the class that provides 
     * the loggers (the {@code providingClass}). The next frame in 
     * the call stack then reveals the name of the class that 
     * requests the logger.
     *
     * @param providingClass the providing class
     * @return the optional
     */
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.AvoidReassigningLoopVariables" })
    private static Optional<Class<?>>
            findRequestingClass(String providingClass) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            if (ste.getClassName().equals(providingClass)
                && "getLogger".equals(ste.getMethodName())) {
                Class<?>[] classes = CTX_HLPR.getClassContext();
                // getClassContext() adds one level, so the
                // call of getLogger (in classes) should be at i+1.
                i += 1;
                // But... from the JavaDoc: "Some virtual machines may, under
                // some circumstances, omit one or more stack frames
                // from the stack trace." So let's make sure that we
                // are really there.
                while (!classes[i].getName().equals(providingClass)) {
                    i += 1; // NOPMD
                }
                // Next one should now be the caller of getLogger. But
                // in some libraries, getLogger calls "itself", e.g.
                // getLogger(Class<?>) calls getLogger(String), proceed
                // until we're out of this
                while (classes[i].getName().equals(providingClass)) {
                    i += 1; // NOPMD
                }
                return Optional.of(classes[i]);
            }
        }
        return Optional.empty();
    }

    /**
     * Find the bundle that contains the class that wants to get
     * a logger, using the current call stack. 
     * <P>
     * The bundle is determined from the class that invoked 
     * {@code getLogger}, which&mdash;in turn&mdash;is searched for in 
     * the call stack as caller of the {@code getLogger} method of the 
     * class that provides the loggers from the users point of view.
     *
     * @param providingClass the providing class
     * @return the bundle
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
        checkListener();
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
