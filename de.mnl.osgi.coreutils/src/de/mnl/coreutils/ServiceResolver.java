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

package de.mnl.coreutils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

/**
 * Maintains and attempts to resolve dependencies to services. 
 */
public class ServiceResolver implements AutoCloseable {

    private BundleContext context;
    private boolean isOpen;
    private Map<String, ServiceCollector<?, ?>> dependencies = new HashMap<>();
    private Map<String, ServiceCollector<?, ?>> optDependencies
        = new HashMap<>();
    private int resolved;
    private Runnable onResolved;
    private Runnable onDissolving;

    /**
     * Creates a new resolver that uses the given context.
     *
     * @param context the context
     */
    public ServiceResolver(BundleContext context) {
        this.context = context;
    }

    /**
     * Sets the function to called when the resolver has entered the
     * resolved state, i.e. when all mandatory services have been bound.
     *
     * @param onResolved the function
     * @return the service resolver
     */
    public ServiceResolver setOnResolved(Runnable onResolved) {
        this.onResolved = onResolved;
        return this;
    }

    /**
     * Sets the function to called when the resolver is about to leave the
     * resolved state, i.e. when one of the mandatory services is going
     * to be unbound.
     *
     * @param onDissolving the function
     * @return the service resolver
     */
    public ServiceResolver setOnDissolving(Runnable onDissolving) {
        this.onDissolving = onDissolving;
        return this;
    }

    /**
     * Adds a mandatory dependency on the service specified by the
     * class. The name of the class is used as name of the
     * dependency.
     *
     * @param clazz the class
     * @return the service resolver
     */
    public ServiceResolver addDependency(Class<?> clazz) {
        addDependency(clazz.getName(), clazz.getName());
        return this;
    }

    /**
     * Adds a mandatory dependency on the service specified by the
     * filter.
     *
     * @param name the name of the dependency
     * @param filter the filter
     * @return the service resolver
     */
    @SuppressWarnings("resource")
    public ServiceResolver addDependency(String name, Filter filter) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            dependencies.put(name, new ServiceCollector<>(context, filter)
                .setOnBound(this::onBound).setOnUnbinding(this::onUnbinding));
            return this;
        }
    }

    /**
     * Adds a mandatory dependency on the service specified by the
     * service reference.
     *
     * @param name the name of the dependency
     * @param reference the reference
     * @return the service resolver
     */
    @SuppressWarnings("resource")
    public ServiceResolver addDependency(String name,
            ServiceReference<?> reference) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            dependencies.put(name, new ServiceCollector<>(context, reference)
                .setOnBound(this::onBound).setOnUnbinding(this::onUnbinding));
            return this;
        }
    }

    /**
     * Adds a mandatory dependency on the service specified by the
     * service reference.
     *
     * @param name the name of the dependency
     * @param className the class name
     */
    @SuppressWarnings("resource")
    public ServiceResolver addDependency(String name, String className) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            dependencies.put(name, new ServiceCollector<>(context, className)
                .setOnBound(this::onBound).setOnUnbinding(this::onUnbinding));
            return this;
        }
    }

    /**
     * Adds an optional dependency on the service specified by the
     * class. The name of the class is used as identifier for the
     * dependency.
     *
     * @param clazz the class
     * @return the service resolver
     */
    public ServiceResolver addOptionalDependency(Class<?> clazz) {
        addOptionalDependency(clazz.getName(), clazz.getName());
        return this;
    }

    /**
     * Adds an optional dependency on the service specified by the
     * filter.
     *
     * @param name the name of the dependency
     * @param filter the filter
     * @return the service resolver
     */
    @SuppressWarnings("resource")
    public ServiceResolver addOptionalDependency(String name, Filter filter) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            optDependencies.put(name, new ServiceCollector<>(context, filter));
            return this;
        }
    }

    /**
     * Adds an optional dependency on the service specified by the
     * service reference.
     *
     * @param name the name of the dependency
     * @param reference the reference
     * @return the service resolver
     */
    @SuppressWarnings("resource")
    public ServiceResolver addOptionalDependency(String name,
            ServiceReference<?> reference) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            optDependencies.put(name,
                new ServiceCollector<>(context, reference));
            return this;
        }
    }

    /**
     * Adds an optional dependency on the service specified by the
     * service reference.
     *
     * @param name the name of the dependency
     * @param className the class name
     */
    @SuppressWarnings("resource")
    public ServiceResolver addOptionalDependency(String name,
            String className) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            optDependencies.put(name,
                new ServiceCollector<>(context, className));
            return this;
        }
    }

    private void onBound(ServiceReference<?> reference, Object service) {
        synchronized (this) {
            resolved += 1;
            if (onResolved != null && resolved == dependencies.size()) {
                onResolved.run();
            }
        }
    }

    private void onUnbinding(ServiceReference<?> reference, Object service) {
        synchronized (this) {
            if (onDissolving != null && resolved == dependencies.size()) {
                onDissolving.run();
            }
            resolved -= 1;
        }
    }

    /**
     * Starts the resolver. While open, the resolver attempts
     * to obtain service implementation for all registered
     * service dependencies.
     */
    public void open() {
        synchronized (this) {
            if (isOpen) {
                return;
            }
            resolved = 0;
        }
        for (ServiceCollector<?, ?> coll : dependencies.values()) {
            coll.open();
        }
        for (ServiceCollector<?, ?> coll : optDependencies.values()) {
            coll.open();
        }
    }

    /**
     * Stops the resolver. All maintained services are released.
     */
    public void close() {
        if (!isOpen) {
            return;
        }
        for (ServiceCollector<?, ?> coll : dependencies.values()) {
            coll.close();
        }
        for (ServiceCollector<?, ?> coll : optDependencies.values()) {
            coll.close();
        }
    }

    boolean resolved() {
        synchronized (this) {
            return resolved == dependencies.size();
        }
    }

    /**
     * Returns the service found for the mandatory dependency with the
     * given name. This method should only be called when the resolver
     * is in state resolved. 
     *
     * @param <T> the type of the service
     * @param name the name of the dependency
     * @param clazz the class of the service
     * @return the service or {@code null} if called in unresolved
     *     state and the dependency is not resolved
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name, Class<T> clazz) {
        return Optional.ofNullable(dependencies.get(name))
            .map(coll -> ((ServiceCollector<?, T>) coll).service().get())
            .orElse(null);
    }

    /**
     * Returns the service found for the mandatory dependency
     * using the name of the class as name of the dependency. 
     * This method should only be called when 
     * the resolver is in state resolved. 
     *
     * @param <T> the type of the service
     * @param clazz the class of the service
     * @return the service or {@code null} if called in unresolved
     *     state and the dependency is not resolved
     */
    public <T> T get(Class<T> clazz) {
        return get(clazz.getName(), clazz);
    }

    /**
     * Returns the (optional) service found for the optional dependency 
     * with the given name. 
     * 
     * @param <T> the type of the service
     * @param name the name of the dependency
     * @param clazz the class of the service
     * @return the result
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> optional(String name, Class<T> clazz) {
        return Optional.ofNullable(dependencies.get(name))
            .map(coll -> ((ServiceCollector<?, T>) coll).service())
            .orElse(Optional.empty());
    }

    /**
     * Returns the (optional) service found for the optional dependency, 
     * using the name of the class as name of the dependency. 
     * 
     * @param <T> the type of the service
     * @param name the name of the dependency
     * @param clazz the class of the service
     * @return the result
     */
    public <T> Optional<T> optional(Class<T> clazz) {
        return optional(clazz.getName(), clazz);
    }
}
