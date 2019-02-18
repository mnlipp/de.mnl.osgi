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
import java.util.function.Consumer;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

/**
 * Maintains and attempts to resolve dependencies to services.
 * <P>
 * The class supports two usage pattern. The first is to use the
 * {@code ServiceResolver} as base class for the bundle's activator.
 * The derived class must override {@link #configure()} to add
 * at least one dependency.
 * <P>
 * The methods {@link #onResolved()}, {@link #onDissolving()} and
 * {@link #onRebound(String)} can be overridden as required.
 * 
 * <pre>
 * public class Activator extends ServiceResolver {
 *     &#x40;Override
 *     protected void configure() {
 *         addDependency(...);
 *     }
 *     
 *     &#x40;Override
 *     protected void onResolved() {
 *         // ...
 *     }
 *     
 *     &#x40;Override
 *     protected void onDissolving() {
 *         // ...
 *     }
 *     
 *     &#x40;Override
 *     protected void onRebound(String dependency) {
 *         // ...
 *     }
 * }
 * </pre>
 * 
 * The second usage pattern is to create a {@code ServiceResolver} with
 * a {@link BundleContext}, add dependencies and callbacks as required
 * and call {@link #open()}. When resolving is no longer required,
 * {@link #close()} must be called.
 * 
 * <pre>
 * // Usually executed during bundle start...
 * ServiceResolver resolver = new ServiceResolver(context);
 * // Add dependencies
 * resolver.addDependency(...);
 * resolver.setOnResolved(() -&gt; ...);
 * resolver.setOnDissolving(() -&gt; ...);
 * resolver.setOnRebound(name -&gt; ...);
 * resolver.open();
 * 
 * // Usually executed during bundle stop...
 * resolver.close();
 * </pre>
 * 
 */
public class ServiceResolver implements AutoCloseable, BundleActivator {

    /**
     * The bundle context. Made available for derived classes.
     */
    protected BundleContext context;
    private boolean isOpen;
    private Map<String, ServiceCollector<?, ?>> dependencies = new HashMap<>();
    private Map<String, ServiceCollector<?, ?>> optDependencies
        = new HashMap<>();
    private int resolved;
    private Runnable onResolved;
    private Runnable onDissolving;
    private Consumer<String> onRebound;

    /**
     * Creates a new resolver that uses the given context.
     *
     * @param context the context
     */
    public ServiceResolver(BundleContext context) {
        this.context = context;
    }

    /**
     * Constructor for using the {@code ServiceResolver} as base
     * class for a {@link BundleActivator}.
     */
    protected ServiceResolver() {
    }

    /**
     * Called by the framework when using the {@code ServiceResolver} as 
     * base class for a bundle activator.
     * <P>
     * The implementation first calls {@link #configure()} and then
     * {@link #open()}. 
     *
     * @param context the context
     * @throws Exception if a problem occurs
     */
    @Override
    public void start(BundleContext context) throws Exception {
        this.context = context;
        configure();
        open();
    }

    /**
     * Configures the resolver. Must be overridden by the derived class
     * when using the resolver as base class for a bundle activator.
     * The derived class must configure the resolver with calls to
     * at least one of the {@code addDependency} methods.
     * <P>
     * The default implementation does nothing.
     */
    protected void configure() {
    }

    /**
     * Called when all dependencies have been resolved. Overriding this
     * method is an alternative to setting a callback with
     * {@link #setOnResolved(Runnable)}.
     */
    protected void onResolved() {
    }

    /**
     * Called when the resolver is about to leave the resolved state,
     * i.e. when one of the mandatory services is going to be unbound.
     * Overriding this method is an alternative to setting a callback with
     * {@link #setOnDissolving(Runnable)}.
     */
    protected void onDissolving() {
    }

    /**
     * Called when the preferred service of a resolved dependency 
     * changes. The change may either be a change of properties 
     * reported by the framework or the replacement of the preferred 
     * service with another service. Overriding this method is an 
     * alternative to setting a callback with
     * {@link #setOnRebound(Consumer)}.
     *
     * @param dependency the dependency that has been rebound
     */
    protected void onRebound(String dependency) {
    }

    /**
     * Called by the framework when using the {@code ServiceResolver} as 
     * base class for a bundle activator.
     *
     * @param context the context
     * @throws Exception if a problem occurs
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        close();
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
     * Sets the function to be called when the preferred service
     * of a resolved dependency changes. The change may either be
     * a change of properties reported by the framework or the
     * replacement of the preferred service with another service. 
     *
     * @param onRebound the on rebound
     * @return the service resolver
     */
    public ServiceResolver setOnRebound(Consumer<String> onRebound) {
        this.onRebound = onRebound;
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
    public ServiceResolver addDependency(String name, Filter filter) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            dependencies.put(name, new ServiceCollector<>(context, filter)
                .setOnBound(this::boundCb).setOnUnbinding(this::unbindingCb)
                .setOnModfied((ref, svc) -> modifiedCb(name)));
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
    public ServiceResolver addDependency(String name,
            ServiceReference<?> reference) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            dependencies.put(name, new ServiceCollector<>(context, reference)
                .setOnBound(this::boundCb).setOnUnbinding(this::unbindingCb)
                .setOnModfied((ref, svc) -> modifiedCb(name)));
            return this;
        }
    }

    /**
     * Adds a mandatory dependency on the service specified by the
     * service reference.
     *
     * @param name the name of the dependency
     * @param className the class name
     * @return the service resolver
     */
    public ServiceResolver addDependency(String name, String className) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            dependencies.put(name, new ServiceCollector<>(context, className)
                .setOnBound(this::boundCb).setOnUnbinding(this::unbindingCb)
                .setOnModfied((ref, svc) -> modifiedCb(name)));
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
    public ServiceResolver addOptionalDependency(String name, Filter filter) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            optDependencies.put(name, new ServiceCollector<>(context, filter)
                .setOnModfied((ref, svc) -> modifiedCb(name)));
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
    public ServiceResolver addOptionalDependency(String name,
            ServiceReference<?> reference) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            optDependencies.put(name,
                new ServiceCollector<>(context, reference)
                    .setOnModfied((ref, svc) -> modifiedCb(name)));
            return this;
        }
    }

    /**
     * Adds an optional dependency on the service specified by the
     * service reference.
     *
     * @param name the name of the dependency
     * @param className the class name
     * @return the service resolver
     */
    public ServiceResolver addOptionalDependency(String name,
            String className) {
        synchronized (this) {
            if (isOpen) {
                throw new IllegalStateException(
                    "Cannot add dependencies to open reolver.");
            }
            optDependencies.put(name,
                new ServiceCollector<>(context, className)
                    .setOnModfied((ref, svc) -> modifiedCb(name)));
            return this;
        }
    }

    private void boundCb(ServiceReference<?> reference, Object service) {
        synchronized (this) {
            resolved += 1;
            if (resolved == dependencies.size()) {
                Optional.ofNullable(onResolved).ifPresent(cb -> cb.run());
                onResolved();
            }
        }
    }

    private void unbindingCb(ServiceReference<?> reference, Object service) {
        synchronized (this) {
            if (resolved == dependencies.size()) {
                Optional.ofNullable(onDissolving).ifPresent(cb -> cb.run());
                onDissolving();
            }
            resolved -= 1;
        }
    }

    protected void modifiedCb(String dependency) {
        synchronized (this) {
            Optional.ofNullable(onRebound)
                .ifPresent(cb -> cb.accept(dependency));
            onRebound(dependency);
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
        isOpen = true;
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
        isOpen = false;
    }

    /**
     * Checks if the resolver is open.
     *
     * @return true, if open
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Checks if the resolver is in the resolved state.
     *
     * @return the result
     */
    public boolean isResolved() {
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
     * @param clazz the class of the service
     * @return the result
     */
    public <T> Optional<T> optional(Class<T> clazz) {
        return optional(clazz.getName(), clazz);
    }
}
