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

package de.mnl.osgi.lf4osgi.provider;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * A manager for bundle contexts. This is simply a {@link WeakHashMap}
 * with {@link Bundle}s as key and {@link WeakReference}s to
 * {@link Bundle}s as values. This ensures that our caching never
 * prevents garbage collection.
 *
 * @param <L> the generic type
 */
public class LoggerFacadeContextRegistry<C extends LoggerFacadeContext<L>,
        L extends LoggerFacade> {

    private static final ContextHelper CTX_HLPR = new ContextHelper();
    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Map<Class<?>, WeakReference<Bundle>> bundles
        = new ConcurrentHashMap<>();

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<Bundle, WeakReference<C>> contextMap
        = Collections.synchronizedMap(new WeakHashMap<>());
    private final Function<Bundle, C> contextSupplier;

    /**
     * Instantiates a new logger context registry.
     *
     * @param contextSupplier the context supplier
     */
    public LoggerFacadeContextRegistry(Function<Bundle, C> contextSupplier) {
        super();
        this.contextSupplier = contextSupplier;
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

    private static Optional<Bundle> findBundle(Class<?> clazz) {
        if (clazz == null) {
            return Optional.empty();
        }
        Bundle bundle = Optional.ofNullable(bundles.get(clazz))
            .map(WeakReference::get).orElse(null);
        if (bundle != null) {
            return Optional.of(bundle);
        }
        bundle = FrameworkUtil.getBundle(clazz);
        if (bundle != null) {
            bundles.put(clazz, new WeakReference<>(bundle));
            return Optional.of(bundle);
        }
        return Optional.empty();
    }

    /**
     * Find the invoking bundle. It is determined
     * from the class that invoked getLogger, which is searched for
     * in the stacktrace as caller of the getLogger method of the class
     * that provides the loggers from the users point of view.
     *
     * @param providingClass the providing class
     * @return the optional
     */
    public static Optional<Bundle> findBundle(String providingClass) {
        return findRequestingClass(providingClass)
            .flatMap(LoggerFacadeContextRegistry::findBundle);
    }

    /**
     * Returns the bundle context for the given bundle.
     *
     * @param bundle the bundle
     * @return the bundle context
     */
    public C getBundleContext(Bundle bundle) {
        synchronized (contextMap) {
            C bdlCtx = contextMap.computeIfAbsent(bundle,
                b -> new WeakReference<>(contextSupplier.apply(b))).get();
            if (bdlCtx != null) {
                return bdlCtx;
            }
            // Was there but has been garbage collected.
            bdlCtx = contextSupplier.apply(bundle);
            contextMap.put(bundle, new WeakReference<>(bdlCtx));
            return bdlCtx;
        }
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
