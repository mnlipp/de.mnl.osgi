/**
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
 * 
 * Method comments based on the comments in the ServiceTracker implementation
 * 
 * Copyright (c) OSGi Alliance (2000, 2014). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package de.mnl.coreutils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * The {@code ServiceCollector} class simplifies using services
 * from the OSGi Framework's service registry beyond the simplification
 * provided by the {@link ServiceTracker} from the 
 * <a href="https://osgi.org/specification/osgi.core/7.0.0/util.tracker.html">
 * OSGi Core Specification</a>.
 * <p>
 * The {@code ServiceCollector} calls registered callbacks when the first or any 
 * service becomes available. The implementation ensures that
 * at least one service can be obtained from the {@code ServiceCollector}
 * during the execution of these callbacks. At least one service will also
 * be available after the execution of the callbacks until the 
 * "last unavailable" callback has been invoked.
 * <P>
 * Functions enabled/disabled in the respective callbacks can therefore 
 * rely on a service being available while they are enabled. This
 * includes, as a special case, starting and stopping threads in the
 * respective callbacks.
 * <p>
 * A {@code ServiceCollector} object is constructed with search 
 * criteria. It can then be opened to begin collecting all services in 
 * the Framework's service registry that match the specified search 
 * criteria. The {@code ServiceCollector} is currently
 * implemented using a standard {@link ServiceTracker} as a delegee.
 * <p>
 * The {@code ServiceCollector} class is thread-safe.
 *
 * @param <S> the type of the service to be collected
 * @param <W> the type of the collected items
 *      (see {@link #setWrapper(Function)}). Usually, this is the 
 *      same type as {@code S}, i.e. services are not wrapped before
 *      being returned.
 */
public class ServiceCollector<S, W> implements AutoCloseable {

    private final ServiceTracker<S, W> delegee;
    /*
     * The service data known from ServiceTracker#addingService
     * but not yet known to have been added. Or the service data
     * already removed from the ServiceTracker but provided as a fallback
     * while calling onLastUnavailable in ServiceTracker#removedService.
     */
    private FallbackData fallback;
    private BiConsumer<ServiceReference<S>, W> onFirstAvailable;
    private BiConsumer<ServiceReference<S>, W> onAvailable;
    private BiConsumer<ServiceReference<S>, W> onUnavailable;
    private BiConsumer<ServiceReference<S>, W> onLastUnavailable;
    private BiConsumer<ServiceReference<S>, W> onModified;
    private Function<S, W> wrapper;
    private int minModificationCount;

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * of the specified class.
     *
     * @param context the bundle context used to interact with the framework
     * @param clazz the clazz
     */
    public ServiceCollector(BundleContext context, Class<S> clazz) {
        delegee = new MyTracker(context, clazz, null);
    }

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * matches by the specified filter.
     *
     * @param context the bundle context used to interact with the framework
     * @param filter the filter
     */
    public ServiceCollector(BundleContext context, Filter filter) {
        delegee = new MyTracker(context, filter, null);
    }

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * on the specified service reference.
     *
     * @param context the bundle context used to interact with the framework
     * @param reference the reference
     */
    public ServiceCollector(BundleContext context,
            ServiceReference<S> reference) {
        delegee = new MyTracker(context, reference, null);
    }

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * on the specified class name.
     *
     * @param context the bundle context used to interact with the framework
     * @param className the class name
     */
    public ServiceCollector(BundleContext context, String className) {
        delegee = new MyTracker(context, className, null);
    }

    /**
     * Starts collecting of service providers. Short for calling {@code open(false)}.
     *
     * @throws IllegalStateException If the {@code BundleConetxt}
     *     with which this {@code ServiceCollector} was created is no longer valid.
     */
    public void open() throws IllegalStateException {
        delegee.open();
        minModificationCount = delegee.getTrackingCount();
    }

    /**
     * Starts collecting services. Short for calling {@code open(false)}.
     * 
     * @param collectAllServices if <code>true</code>, then this 
     *     {@code ServiceCollector} will collect all matching services 
     *     regardless of class loader
     *     accessibility. If <code>false</code>, then 
     *     this {@code ServiceCollector} will only collect matching services 
     *     which are class loader
     *     accessible to the bundle whose <code>BundleContext</code> is 
     *     used by this {@code ServiceCollector}.
     * @throws IllegalStateException If the {@code BundleConetxt}
     *     with which this {@code ServiceCollector} was created is no longer valid.
     */
    public void open(boolean collectAllServices) throws IllegalStateException {
        delegee.open(collectAllServices);
    }

    /**
     * Stops collecting services.
     */
    public void close() {
        fallback = null;
        delegee.close();
    }

    /**
     * Sets a function to be called when the first service becomes 
     * available. The service reference to the new service 
     * and the service are passed as arguments.
     *
     * @param onFirstAvailable the function to be called
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnFirstAvailable(
            BiConsumer<ServiceReference<S>, W> onFirstAvailable) {
        this.onFirstAvailable = onFirstAvailable;
        return this;
    }

    /**
     * Sets a function to be called when a new service becomes 
     * available. The service reference to the new service 
     * and the service are passed as arguments. If both an
     * "onAvailable" and an "onFirstAvailable" function are
     * provided, the latter will be called first.
     *
     * @param onAvailable the function to be called
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnAvailable(
            BiConsumer<ServiceReference<S>, W> onAvailable) {
        this.onAvailable = onAvailable;
        return this;
    }

    /**
     * Sets a function to be called when one of the collected services
     * becomes unavailable. The service reference to the modified service 
     * and the service are passed as arguments.
     *
     * @param onUnavailable the function to call
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnUnavailable(
            BiConsumer<ServiceReference<S>, W> onUnavailable) {
        this.onUnavailable = onUnavailable;
        return this;
    }

    /**
     * Sets a function to be called when the last of the collected services
     * becomes unavailable. The service reference to the modified service 
     * and the service are passed as arguments. If both an
     * "onUnavailable" and an "onLastUnavailable" function are set,
     * the latter will be called last.
     *
     * @param onLastUnavailable the function to call
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnLastUnavailable(
            BiConsumer<ServiceReference<S>, W> onLastUnavailable) {
        this.onLastUnavailable = onLastUnavailable;
        return this;
    }

    /**
     * Sets a function to be called when one of the collected services
     * changes. The service reference to the modified service and
     * the service are passed as arguments.
     *
     * @param onModified the function to call
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnModfied(
            BiConsumer<ServiceReference<S>, W> onModified) {
        this.onModified = onModified;
        return this;
    }

    /**
     * Sets the wrapper function. The function is invoked for
     * every newly collected service. It allows to wrap a service
     * obtained from the registry in some other object that is
     * used as return value when retrieving services from this 
     * {@code ServiceCollector}.
     * <P>
     * The wrapper can also serve as a filter. If the wrapper returns
     * {@code null}, the service will not be added to the collection.
     *
     * @param wrapper the wrapper
     * @return the {@code ServiceCollector}
     * @throws IllegalStateException if the {@code ServiceCollector} is open
     */
    public ServiceCollector<S, W> setWrapper(
            Function<S, W> wrapper) {
        if (delegee.getTrackingCount() >= 0) {
            throw new IllegalStateException(
                "Wrapper cannot be set while service collector is open.");
        }
        this.wrapper = wrapper;
        return this;
    }

    /**
     * Wait for at least one service to be collected by this
     * {@code ServiceCollector}. This method will also return when this
     * {@code ServiceCollector} is closed from another thread.
     * <p>
     * It is strongly recommended that {@code waitForService} is not used 
     * during the calling of the {@code BundleActivator} methods.
     * {@code BundleActivator} methods are expected to complete in a short
     * period of time.
     * 
     * @param timeout The time interval in milliseconds to wait. If zero, the
     *     method will wait indefinitely.
     * @return Returns the result of {@link #service()}.
     * @throws InterruptedException If another thread has interrupted the
     *     current thread.
     * @throws IllegalArgumentException If the value of timeout is negative.
     */
    public W waitForService(long timeout) throws InterruptedException {
        return delegee.waitForService(timeout);
    }

    /**
     * Return an array of {@code ServiceReference}s for all services 
     * collected by this {@code ServiceCollector}.
     * 
     * @return Array of {@code ServiceReference}s or an empty array if no 
     *     services have been collected.
     */
    @SuppressWarnings("unchecked")
    public ServiceReference<S>[] serviceReferences() {
        FallbackData maybeAdd = fallback;
        if (maybeAdd == null) {
            return Optional.ofNullable(delegee.getServiceReferences())
                .orElse((ServiceReference<S>[]) new ServiceReference[0]);
        }
        // Add fallback service reference if not already in list returned
        // by delegee.
        Object maybeAddId = maybeAdd.svcRef().getProperty(Constants.SERVICE_ID);
        List<ServiceReference<S>> result = new ArrayList<>();
        ServiceReference<S>[] knownRefs = delegee.getServiceReferences();
        if (knownRefs != null) {
            for (ServiceReference<S> ref : knownRefs) {
                result.add(ref);
                if (ref.getProperty(Constants.SERVICE_ID).equals(maybeAddId)) {
                    // Known and therefore already added.
                    maybeAdd = null;
                }
            }
        }
        if (maybeAdd != null) {
            result.add(maybeAdd.svcRef());
        }
        return result.toArray(new ServiceReference[0]);
    }

    /**
     * Returns a {@code ServiceReference} for one of the services collected
     * by this {@code ServiceCollector}.
     * <p>
     * If multiple services have been collected, the service with the highest
     * ranking (as specified in its {@code service.ranking} property) is
     * returned. If there is a tie in ranking, the service with the lowest
     * service id (as specified in its {@code service.id} property); that is,
     * the service that was registered first is returned. This is the same
     * algorithm used by {@code BundleContext.getServiceReference}.
     * 
     * @return an optional {@code ServiceReference}
     */
    public Optional<ServiceReference<S>> serviceReference() {
        return Optional.ofNullable(delegee.getServiceReference())
            .map(ref -> Optional.of(ref))
            .orElse(Optional.ofNullable(fallback).map(FallbackData::svcRef));
    }

    /**
     * Returns the service object for the specified {@code ServiceReference} 
     * if the specified referenced service has been collected
     * by this {@code ServiceCollector}.
     * 
     * @param reference the reference to the desired service.
     * @return an optional service object
     */
    public Optional<W> service(ServiceReference<S> reference) {
        W known = delegee.getService(reference);
        if (known != null) {
            return Optional.of(known);
        }
        // Maybe fallback can be used...
        FallbackData maybe = fallback;
        return Optional.ofNullable(maybe.svcRef())
            .filter(ref -> ref.getProperty(Constants.SERVICE_ID)
                .equals(reference.getProperty(Constants.SERVICE_ID)))
            .map(found -> maybe.service());
    }

    /**
     * Returns a service object for one of the services collected by this
     * {@code ServiceCollector}.
     * 
     * @return an optional service object
     */
    public Optional<W> service() {
        return Optional.ofNullable(
            Optional.ofNullable(delegee.getService()).orElse(Optional
                .ofNullable(fallback).map(FallbackData::service).orElse(null)));
    }

    /**
     * Return an array of service objects for all services collected by this
     * {@code ServiceCollector}.
     * 
     * @return an array of service objects which may be empty
     */
    public Object[] services() {
        List<W> result = new ArrayList<>();
        for (ServiceReference<S> ref : serviceReferences()) {
            service(ref).ifPresent(s -> result.add(s));
        }
        return result.toArray(new Object[0]);
    }

    /**
     * Return the number of services collected by this
     * {@code ServiceCollector}. This value may not be correct during the
     * execution of handlers.
     * 
     * @return The number of services collected
     */
    public int size() {
        return serviceReferences().length;
    }

    /**
     * Returns the modification count for this {@code ServiceCollector}.
     * 
     * The modification count is initialized to 0 when this 
     * {@code ServiceCollector} is opened. Every time a service is added, 
     * modified or removed from this {@code ServiceCollector}, 
     * the modification count is incremented.
     * <p>
     * The modification count can be used to determine if this
     * {@code ServiceCollector} has added, modified or removed a service by
     * comparing a modification count value previously collected with the 
     * current modification count value. If the value has not changed, 
     * then no service has been added, modified or removed from this 
     * {@code ServiceCollector} since the previous modification count 
     * was collected.
     * 
     * @return The modification count for this {@code ServiceCollector} or
     *      -1 if this {@code ServiceCollector} is not open.
     */
    public int modificationCount() {
        return Math.max(minModificationCount, delegee.getTrackingCount());
    }

    /**
     * Return a {@code SortedMap} of the {@code ServiceReference}s and service
     * objects for all services collected by this {@code ServiceCollector}.
     * The map is sorted in reverse natural order of {@code ServiceReference}.
     * That is, the first entry is the service with the highest ranking and the
     * lowest service id.
     * 
     * @return A {@code SortedMap} with the {@code ServiceReference}s and
     *         service objects for all services collected by this
     *         {@code ServiceCollector}. If no services have been collected,
     *         then the returned map is empty.
     */
    public SortedMap<ServiceReference<S>, W> collected() {
        SortedMap<ServiceReference<S>, W> result = delegee.getTracked();
        FallbackData maybe = fallback;
        if (maybe != null) {
            Object maybeId = maybe.svcRef().getProperty(Constants.SERVICE_ID);
            // There's no equals/hashCode on ServiceReference, sigh.
            boolean found = false;
            for (ServiceReference<S> ref : result.keySet()) {
                if (ref.getProperty(Constants.SERVICE_ID).equals(maybeId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.put(maybe.svcRef(), maybe.service());
            }
        }
        return result;
    }

    /**
     * Return if this {@code ServiceCollector} is empty.
     * 
     * @return {@code true} if this {@code ServiceCollector} 
     *     has not collected any services.
     */
    public boolean isEmpty() {
        return delegee.isEmpty();
    }

    /**
     * Remove a service from this {@code ServiceCollector}.
     * 
     * The specified service will be removed from this {@code ServiceCollector}.
     * 
     * @param reference The reference to the service to be removed.
     */
    public void remove(ServiceReference<S> reference) {
        delegee.remove(reference);
    }

    private class FallbackData {
        private final ServiceReference<S> svcRef;
        private final W service;

        public FallbackData(ServiceReference<S> svcRef, W service) {
            this.svcRef = svcRef;
            this.service = service;
        }

        public final ServiceReference<S> svcRef() {
            return svcRef;
        }

        public final W service() {
            return service;
        }
    }

    private class MyTracker extends ServiceTracker<S, W> {

        public MyTracker(BundleContext context, Class<S> clazz,
                ServiceTrackerCustomizer<S, W> customizer) {
            super(context, clazz, customizer);
        }

        public MyTracker(BundleContext context, Filter filter,
                ServiceTrackerCustomizer<S, W> customizer) {
            super(context, filter, customizer);
        }

        public MyTracker(BundleContext context, ServiceReference<S> reference,
                ServiceTrackerCustomizer<S, W> customizer) {
            super(context, reference, customizer);
        }

        public MyTracker(BundleContext context, String clazz,
                ServiceTrackerCustomizer<S, W> customizer) {
            super(context, clazz, customizer);
        }

        @Override
        public W addingService(ServiceReference<S> reference) {
            synchronized (this) {
                minModificationCount = delegee.getTrackingCount() + 1;
                @SuppressWarnings("unchecked")
                W newService = Optional.ofNullable(wrapper)
                    .map(w -> w.apply(context.getService(reference)))
                    .orElse((W) context.getService(reference));
                fallback = new FallbackData(reference, newService);
                if (delegee.isEmpty()) {
                    Optional.ofNullable(onFirstAvailable)
                        .ifPresent(h -> h.accept(reference, newService));
                }
                if (onAvailable != null) {
                    Optional.ofNullable(onAvailable)
                        .ifPresent(h -> h.accept(reference, newService));
                }
                return newService;
            }
        }

        @Override
        public void removedService(ServiceReference<S> reference, W service) {
            synchronized (this) {
                fallback = new FallbackData(reference, service);
                Optional.ofNullable(onUnavailable)
                    .ifPresent(h -> h.accept(reference, service));
                if (delegee.isEmpty()) {
                    Optional.ofNullable(onLastUnavailable)
                        .ifPresent(h -> h.accept(reference, service));
                }
                fallback = null;
                super.removedService(reference, service);
            }
        }

        @Override
        public void modifiedService(ServiceReference<S> reference, W service) {
            Optional.ofNullable(onModified)
                .ifPresent(h -> h.accept(reference, service));
        }
    }
}
