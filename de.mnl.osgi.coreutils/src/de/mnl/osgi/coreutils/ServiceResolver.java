/*
 * Copyright (C) 2019,2022 Michael N. Lipp (http://www.mnl.de)
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

package de.mnl.osgi.coreutils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

/**
 * Maintains and attempts to resolve dependencies on services.
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
 *         // Enable usage or register as service or start worker thread ...
 *     }
 *     
 *     &#x40;Override
 *     protected void onDissolving() {
 *         // Disable usage or unregister or stop and join worker thread ...
 *     }
 *     
 *     &#x40;Override
 *     protected void onRebound(String dependency) {
 *         // Only required if there is a "long term" usage of a dependency.
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
 * In addition to the management of mandatory dependencies, the
 * {@code ServiceResolver} can also keep track of optional
 * service dependencies. This allows the {@code ServiceResolver}
 * to be provided as unified service access point. Apart from
 * a single {@link #open()} and {@link #close()} function, the
 * {@code ServiceProvider} does not implement additional functions
 * for optional service dependencies compared to the underlying
 * {@link ServiceCollector}s.
 */
@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public class ServiceResolver implements AutoCloseable, BundleActivator {

    /**
     * The bundle context. Made available for derived classes.
     */
    protected BundleContext context;
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile boolean isOpen;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<String, ServiceCollector<?, ?>> dependencies
        = new HashMap<>();
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<String, ServiceCollector<?, ?>> optDependencies
        = new HashMap<>();
    private int resolvedCount;
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
    @SuppressWarnings("PMD.UncommentedEmptyConstructor")
    protected ServiceResolver() {
    }

    /**
     * Called by the framework when using the {@code ServiceResolver} as 
     * base class for a bundle activator.
     * <P>
     * The implementation sets the {@link #context} attribute and calls 
     * {@link #configure()} and {@link #open()}. 
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
     * Configures the {@code ServiceResolver}. Must be overridden 
     * by the derived class when using the {@code ServiceResolver}
     * as base class for a bundle activator.
     * The derived class must configure the resolver with calls to
     * at least one of the {@code addDependency} methods.
     * <P>
     * The default implementation does nothing.
     */
    protected void configure() {
        // Default does nothing.
    }

    /**
     * Called when all dependencies have been resolved. Overriding this
     * method is an alternative to setting a callback with
     * {@link #setOnResolved(Runnable)}.
     */
    protected void onResolved() {
        // Default does nothing.
    }

    /**
     * Called when the resolver is about to leave the resolved state,
     * i.e. when one of the mandatory services is going to be unbound.
     * Overriding this method is an alternative to setting a callback with
     * {@link #setOnDissolving(Runnable)}.
     */
    protected void onDissolving() {
        // Default does nothing.
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
        // Default does nothing.
    }

    /**
     * Called by the framework when using the {@code ServiceResolver} as 
     * base class for a bundle activator. The implementatoin calls
     * {@link #close()}.
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
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
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
                    "Cannot add dependencies to open resolver.");
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

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void boundCb(ServiceReference<?> reference, Object service) {
        synchronized (this) {
            resolvedCount += 1;
            if (resolvedCount == dependencies.size()) {
                Optional.ofNullable(onResolved).ifPresent(cb -> cb.run());
                onResolved();
            }
        }
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void unbindingCb(ServiceReference<?> reference, Object service) {
        synchronized (this) {
            if (resolvedCount == dependencies.size()) {
                Optional.ofNullable(onDissolving).ifPresent(cb -> cb.run());
                onDissolving();
            }
            resolvedCount -= 1;
        }
    }

    private void modifiedCb(String dependency) {
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
            resolvedCount = 0;
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
        synchronized (this) {
            if (!isOpen) {
                return;
            }
            isOpen = false;
            for (ServiceCollector<?, ?> coll : dependencies.values()) {
                coll.close();
            }
            for (ServiceCollector<?, ?> coll : optDependencies.values()) {
                coll.close();
            }
            dependencies.clear();
            optDependencies.clear();
        }
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
            return resolvedCount == dependencies.size();
        }
    }

    /**
     * Returns the service found for the mandatory dependency with the
     * given name. This method should only be called when the resolver
     * is in state resolved.
     * 
     * Note that due to the threaded nature of the environment, 
     * this method can return {@code null}, even if the resolver
     * was in state resolved before its invocation, because the service
     * can go away between the check and the invocation of this method.
     * 
     * If you want to be sure that services haven't been unregistered
     * concurrently, the check for resolved and the invocation of this
     * method must be in a block synchronized on this resolver.
     * 
     * Consider using {@link ServiceResolver#with(String, Function)}
     * or {@link #with(Class, Function)} as an alternative.
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
     *
     * @param <T> the type of the service
     * @param clazz the class of the service
     * @return the service or {@code null} if called in unresolved
     *     state and the dependency is not resolved
     * @see #get(String, Class)
     */
    public <T> T get(Class<T> clazz) {
        return get(clazz.getName(), clazz);
    }

    /**
     * Returns the service found for the optional dependency 
     * with the given name, if it exists. 
     * 
     * @param <T> the type of the service
     * @param name the name of the dependency
     * @param clazz the class of the service
     * @return the result
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> optional(String name, Class<T> clazz) {
        return Optional.ofNullable(optDependencies.get(name))
            .map(coll -> ((ServiceCollector<?, T>) coll).service())
            .orElse(Optional.empty());
    }

    /**
     * Returns the service found for the optional dependency
     * using the name of the class as name of the dependency,
     * if it exists. 
     *
     * @param <T> the type of the service
     * @param clazz the class of the service
     * @return the result
     */
    public <T> Optional<T> optional(Class<T> clazz) {
        return optional(clazz.getName(), clazz);
    }

    /**
     * Convenience method to invoke the a function with the
     * service registered as mandatory or optional dependency
     * while holding a lock on the underlying service collector. 
     *
     * @param <T> the type of the service
     * @param <R> the result type
     * @param name the name of the dependency
     * @param function the function to invoke with the service as argument
     * @return the result or {@link Optional#empty()} of the service
     * was not available.
     */
    @SuppressWarnings("unchecked")
    public <T, R> Optional<R> with(String name,
            Function<T, ? extends R> function) {
        return Optional.ofNullable(
            dependencies.getOrDefault(name, optDependencies.get(name)))
            .flatMap(
                coll -> ((ServiceCollector<?, T>) coll).withService(function));
    }

    /**
     * Convenience method to invoke the a function with the
     * service registered as mandatory or optional dependency
     * while holding a lock on the underlying service collector. 
     *
     * @param <T> the type of the service
     * @param <R> the result type
     * @param clazz the class of the service
     * @param function the function to invoke with the service as argument
     * @return the result or {@link Optional#empty()} of the service
     * was not available.
     */
    public <T, R> Optional<R> with(Class<T> clazz,
            Function<T, ? extends R> function) {
        return with(clazz.getName(), function);
    }

}
