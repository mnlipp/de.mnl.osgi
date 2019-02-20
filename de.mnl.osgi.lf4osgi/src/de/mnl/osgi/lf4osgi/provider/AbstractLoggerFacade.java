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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.log.LoggerFactory;

/**
 * The base class for OSGi logger facades.
 */
public abstract class AbstractLoggerFacade {

    private static final ContextHelper CTX_HLPR = new ContextHelper();
    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Map<Class<?>, WeakReference<Bundle>> bundles
        = new ConcurrentHashMap<>();
    private final String name;
    private final Bundle bundle;

    private static Optional<Class<?>> findCreatingClass() {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            if (ste.getMethodName().equals("getLogger")) {
                Class<?>[] classes = CTX_HLPR.getClassContext();
                // Next in stack is caller of getLogger(), but
                // getClassContext() has added one level
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
     * Instantiates a new logger facade.
     *
     * @param name the name
     */
    public AbstractLoggerFacade(String name) {
        this.name = name;
        bundle = findCreatingClass().flatMap(AbstractLoggerFacade::findBundle)
            .orElse(null);
        LogFacadeManager.addLoggerFacade(this);
    }

    /**
     * Instantiates a new logger facade for the given bundle.
     *
     * @param bundle the bundle
     * @param name the name
     */
    public AbstractLoggerFacade(Bundle bundle, String name) {
        this.bundle = bundle;
        this.name = name;
        LogFacadeManager.addLoggerFacade(this);
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the bundle.
     *
     * @return the bundle
     */
    protected Bundle getBundle() {
        return bundle;
    }

    /**
     * Called when the logger factory changes. Derived classes
     * must update the logger that they had previously obtained.
     *
     * @param factory the factory
     */
    public abstract void loggerFactoryUpdated(LoggerFactory factory);

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
