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
 * 
 * Based on the ServiceTracker implementation from OSGi.
 * 
 * Copyright (c) OSGi Alliance (2000, 2014). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package de.mnl.coreutils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.osgi.framework.AllServiceListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

/**
 * Maintains a collection of services matching some criteria.
 * <P>
 * An instance can be created with different criteria for matching
 * services of a given type. When the instance has been opened, it 
 * represents the collection of all matching services available
 * in the framework. Changes of the collection are reported using
 * the registered handlers.
 * <P>
 * Because OSGi is a threaded environment, the registered services 
 * can vary any time. Results from queries on the collection are 
 * therefore only reliable while synchronizing on the 
 * collection. The synchronization puts other threads that attempt 
 * to change the collection on hold. Note that this implies the 
 * risk of creating deadlocks.
 * <P>
 * Callbacks are also synchronized on this collector, else
 * the state of the collector during the execution of the
 * callback might not reflect the state that is signalled by
 * the callback.
 * <P>
 * The services obtained from the framework may optionally be 
 * wrapped in another type by setting a wrapper function.
 * The invocation of the wrapper is also synchronized on this 
 * collector.
 *
 * @param <S> the type of the service
 * @param <W> the type of values returned by queries, usually the same
 *      type as the type of the service
 */
public class ServiceCollector<S, W> implements AutoCloseable {

    /**
     * The Bundle Context used by this {@code ServiceCollector}.
     */
    protected final BundleContext context;
    /**
     * The Filter used by this {@code ServiceCollector} which s
     * pecifies the search criteria for the services to collect.
     */
    protected final Filter filter;
    /**
     * Filter string for use when adding the ServiceListener. If this field is
     * set, then certain optimizations can be taken since we don't have a user
     * supplied filter.
     */
    final String listenerFilter;
    /**
     * The registered listener.
     */
    ServiceListener listener;
    /**
     * Class name to be collected. If this field is set, then we are 
     * collecting by class name.
     */
    private final String collectClass;
    /**
     * Reference to be collected. If this field is set, then we are 
     * collecting a single ServiceReference.
     */
    private final ServiceReference<S> collectReference;
    /**
     * Initial service references, processed in open.
     */
    private final Set<ServiceReference<S>> initialReferences = new HashSet<>();
    /**
     * Collected services: {@code ServiceReference} -> customized Object and
     * {@code ServiceListener} object
     */
    private final SortedMap<ServiceReference<S>, W> collected
        = new TreeMap<>(Collections.reverseOrder());
    /**
     * Can be used for waiting.
     */
    int[] modificationCount = new int[] { -1 };
    // The callbacks.
    private BiConsumer<ServiceReference<S>, W> onBoundFirst;
    private BiConsumer<ServiceReference<S>, W> onBound;
    private BiConsumer<ServiceReference<S>, W> onUnbinding;
    private BiConsumer<ServiceReference<S>, W> onUnbindingLast;
    private BiConsumer<ServiceReference<S>, W> onModified;
    private Function<S, W> wrapper;
    // Speed up getService.
    private volatile W cachedService;

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * of the specified class.
     *
     * @param context the bundle context used to interact with the framework
     * @param clazz the clazz
     */
    public ServiceCollector(BundleContext context, Class<S> clazz) {
        this(context, clazz.getName());
    }

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * matches by the specified filter.
     *
     * @param context the bundle context used to interact with the framework
     * @param filter the filter
     */
    public ServiceCollector(BundleContext context, Filter filter) {
        this.context = context;
        this.collectReference = null;
        this.collectClass = null;
        this.listenerFilter = filter.toString();
        this.filter = filter;
        if ((context == null) || (filter == null)) {
            /*
             * we throw a NPE here to be consistent with the other constructors
             */
            throw new NullPointerException();
        }
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
        this.context = context;
        this.collectReference = reference;
        this.collectClass = null;
        this.listenerFilter = "(" + Constants.SERVICE_ID + "="
            + reference.getProperty(Constants.SERVICE_ID).toString() + ")";
        try {
            this.filter = context.createFilter(listenerFilter);
        } catch (InvalidSyntaxException e) {
            /*
             * we could only get this exception if the ServiceReference was
             * invalid
             */
            IllegalArgumentException iae = new IllegalArgumentException(
                "unexpected InvalidSyntaxException: " + e.getMessage());
            iae.initCause(e);
            throw iae;
        }
    }

    /**
     * Instantiates a new {@code ServiceCollector} that collects services
     * on the specified class name.
     *
     * @param context the bundle context used to interact with the framework
     * @param className the class name
     */
    public ServiceCollector(BundleContext context, String className) {
        this.context = context;
        this.collectReference = null;
        this.collectClass = className;
        // we call clazz.toString to verify clazz is non-null!
        this.listenerFilter
            = "(" + Constants.OBJECTCLASS + "=" + className + ")";
        try {
            this.filter = context.createFilter(listenerFilter);
        } catch (InvalidSyntaxException e) {
            /*
             * we could only get this exception if the clazz argument was
             * malformed
             */
            IllegalArgumentException iae = new IllegalArgumentException(
                "unexpected InvalidSyntaxException: " + e.getMessage());
            iae.initCause(e);
            throw iae;
        }
    }

    /**
     * Sets the wrapper function. Each service obtained from the 
     * framework can optionally be wrapped in another type if required.
     * <P>
     * If the wrapper returns {@code null}, the service is not added
     * to the collection. The wrapper function can thus also be used
     * as a filter.
     *
     * @param wrapper the wrapper function
     */
    public ServiceCollector<S, W> setWrapper(Function<S, W> wrapper) {
        this.wrapper = wrapper;
        return this;
    }

    /**
     * Sets a function to be called when the first service becomes 
     * available. The service reference to the new service 
     * and the service are passed as arguments.
     *
     * @param onBoundFirst the function to be called
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnBoundFirst(
            BiConsumer<ServiceReference<S>, W> onBoundFirst) {
        this.onBoundFirst = onBoundFirst;
        return this;
    }

    /**
     * Sets a function to be called when a new service becomes 
     * available. The service reference to the new service 
     * and the service are passed as arguments. If both an
     * "onAvailable" and an "onFirstAvailable" function are
     * provided, the latter will be called first.
     *
     * @param onBound the function to be called
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnBound(
            BiConsumer<ServiceReference<S>, W> onBound) {
        this.onBound = onBound;
        return this;
    }

    /**
     * Sets a function to be called when one of the collected services
     * becomes unavailable. The service reference to the modified service 
     * and the service are passed as arguments.
     *
     * @param onUnbinding the function to call
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnUnbinding(
            BiConsumer<ServiceReference<S>, W> onUnbinding) {
        this.onUnbinding = onUnbinding;
        return this;
    }

    /**
     * Sets a function to be called when the last of the collected services
     * becomes unavailable. The service reference to the modified service 
     * and the service are passed as arguments. If both an
     * "onUnavailable" and an "onLastUnavailable" function are set,
     * the latter will be called last.
     *
     * @param onUnbindingLast the function to call
     * @return the {@code ServiceCollector}
     */
    public ServiceCollector<S, W> setOnUnbindingLast(
            BiConsumer<ServiceReference<S>, W> onUnbindingLast) {
        this.onUnbindingLast = onUnbindingLast;
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

    private void modified() {
        synchronized (modificationCount) {
            modificationCount[0] = modificationCount[0] + 1;
            cachedService = null;
            modificationCount.notifyAll();
        }
    }

    /**
     * Starts collecting of service providers. Short for calling 
     * {@code open(false)}.
     *
     * @throws IllegalStateException If the {@code BundleConetxt}
     *     with which this {@code ServiceCollector} was created is 
     *     no longer valid.
     */
    public void open() throws IllegalStateException {
        open(false);
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
     *     with which this {@code ServiceCollector} was created is no 
     *     longer valid.
     */
    public void open(boolean collectAllServices) throws IllegalStateException {
        synchronized (this) {
            if (isOpen()) {
                // Already open, don't treat as error.
                return;
            }
            modificationCount[0] = 0;
            try {
                registerListener(collectAllServices);
                if (collectClass != null) {
                    initialReferences.addAll(getInitialReferences(
                        collectAllServices, collectClass, null));
                } else if (collectReference != null) {
                    if (collectReference.getBundle() != null) {
                        initialReferences.add(collectReference);
                    }
                } else { /* user supplied filter */
                    initialReferences.addAll(getInitialReferences(
                        collectAllServices, null, listenerFilter));
                }
                processInitial();
            } catch (InvalidSyntaxException e) {
                throw new RuntimeException(
                    "unexpected InvalidSyntaxException: " + e.getMessage(),
                    e);
            }
        }
    }

    private void registerListener(boolean collectAllServices)
            throws InvalidSyntaxException {
        if (collectAllServices) {
            listener = new AllServiceListener() {
                @Override
                public void serviceChanged(ServiceEvent event) {
                    ServiceCollector.this.serviceChanged(event);
                }
            };
        } else {
            listener = new ServiceListener() {

                @Override
                public void serviceChanged(ServiceEvent event) {
                    ServiceCollector.this.serviceChanged(event);
                }

            };
        }
        context.addServiceListener(listener, listenerFilter);
    }

    /**
     * Returns the list of initial {@code ServiceReference}s that will be
     * collected by this {@code ServiceCollector}.
     * 
     * @param collectAllServices If {@code true}, use
     *        {@code getAllServiceReferences}.
     * @param className The class name with which the service was registered, or
     *        {@code null} for all services.
     * @param filterString The filter criteria or {@code null} for all services.
     * @return The list of initial {@code ServiceReference}s.
     * @throws InvalidSyntaxException If the specified filterString has an
     *         invalid syntax.
     */
    private List<ServiceReference<S>> getInitialReferences(
            boolean collectAllServices, String className, String filterString)
            throws InvalidSyntaxException {
        @SuppressWarnings("unchecked")
        ServiceReference<S>[] result
            = (ServiceReference<S>[]) ((collectAllServices)
                ? context.getAllServiceReferences(className, filterString)
                : context.getServiceReferences(className, filterString));
        if (result == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(result);
    }

    private void processInitial() {
        while (true) {
            ServiceReference<S> reference;
            synchronized (this) {
                if (!isOpen() || (initialReferences.size() == 0)) {
                    return; /* we are done */
                }
                // Get one...
                Iterator<ServiceReference<S>> iter
                    = initialReferences.iterator();
                reference = iter.next();
                iter.remove();
            }
            // Process as if it was just registered.
            addToCollected(reference);
        }
    }

    private void serviceChanged(ServiceEvent event) {
        /*
         * Check if we had a delayed call (which could happen when we
         * close).
         */
        if (!isOpen()) {
            return;
        }
        @SuppressWarnings("unchecked")
        final ServiceReference<S> reference
            = (ServiceReference<S>) event.getServiceReference();

        switch (event.getType()) {
        case ServiceEvent.REGISTERED:
            addToCollected(reference);
            break;
        case ServiceEvent.MODIFIED:
            synchronized (this) {
                W service = collected.get(reference);
                if (service == null) {
                    // Probably still in initialReferences, ignore.
                    return;
                }
                modified();
                Optional.ofNullable(onModified)
                    .ifPresent(cb -> cb.accept(reference, service));
            }
            break;
        case ServiceEvent.MODIFIED_ENDMATCH:
        case ServiceEvent.UNREGISTERING:
            removeFromCollected(reference);
            break;
        }

    }

    /**
     * Add the given reference to the collected services.
     * 
     * @param reference reference to be collected.
     */
    private void addToCollected(final ServiceReference<S> reference) {
        synchronized (this) {
            if (!isOpen()) {
                // We have been closed.
                return;
            }
            if (collected.get(reference) != null) {
                /* if we are already collecting this reference */
                return; /* skip this reference */
            }
            if (initialReferences.contains(reference)) {
                // Skip, will be added as initial.
                return;
            }

            S obtained = context.getService(reference);
            @SuppressWarnings("unchecked")
            W service = (wrapper == null) ? (W) obtained
                : wrapper.apply(context.getService(reference));
            if (service == null) {
                // Has vanished in the meantime, should not happen when
                // processing a REGISTERED event, but may happen when
                // processing a reference from initialReferences.
                return;
            }
            boolean wasEmpty = collected.isEmpty();
            collected.put(reference, service);
            modified();
            if (wasEmpty) {
                Optional.ofNullable(onBoundFirst)
                    .ifPresent(cb -> cb.accept(reference, service));
            }
            Optional.ofNullable(onBound)
                .ifPresent(cb -> cb.accept(reference, service));
        }
    }

    private void removeFromCollected(ServiceReference<S> reference) {
        synchronized (this) {
            W service = collected.get(reference);
            if (service == null) {
                return;
            }
            Optional.ofNullable(onUnbinding)
                .ifPresent(cb -> cb.accept(reference, service));
            if (collected.size() == 1) {
                Optional.ofNullable(onUnbindingLast)
                    .ifPresent(cb -> cb.accept(reference, service));
            }
            context.ungetService(reference);
            collected.remove(reference);
            if (isOpen()) {
                modified();
            }
        }
    }

    /**
     * Stops collecting services.
     */
    public void close() {
        synchronized (this) {
            context.removeServiceListener(listener);
            synchronized (modificationCount) {
                modificationCount[0] = -1;
                modificationCount.notifyAll();
            }
            while (!collected.isEmpty()) {
                removeFromCollected(collected.lastKey());
            }
            cachedService = null;
        }
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
    public Optional<W> waitForService(long timeout)
            throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        W service = service().orElse(null);
        if (service != null) {
            return Optional.of(service);
        }

        final long endTime
            = (timeout == 0) ? 0 : (System.currentTimeMillis() + timeout);
        while (true) {
            synchronized (modificationCount) {
                if (modificationCount() < 0) {
                    return Optional.empty();
                }
                modificationCount.wait(timeout);
            }
            Optional<W> found = service();
            if (found.isPresent()) {
                return found;
            }
            if (endTime > 0) { // if we have a timeout
                timeout = endTime - System.currentTimeMillis();
                if (timeout <= 0) { // that has expired
                    return Optional.empty();
                }
            }
        }
    }

    /**
     * Return the current set of {@code ServiceReference}s for all services 
     * collected by this {@code ServiceCollector}.
     * 
     * @return the set.
     */
    public List<ServiceReference<S>> serviceReferences() {
        synchronized (this) {
            return Collections
                .unmodifiableList(new ArrayList<>(collected.keySet()));
        }
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
        try {
            synchronized (this) {
                return Optional.of(collected.firstKey());
            }
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
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
        synchronized (this) {
            return Optional.ofNullable(collected.get(reference));
        }
    }

    /**
     * Returns a service object for one of the services collected by this
     * {@code ServiceCollector}. This is effectively the same as 
     * {@code serviceReference().flatMap(ref -> service(ref))}.
     * <P>
     * The result value is cached, so refrain from implementing another
     * cache in the invoking code.
     * 
     * @return an optional service object
     */
    public Optional<W> service() {
        final W cached = cachedService;
        if (cached != null) {
            return Optional.of(cached);
        }
        synchronized (this) {
            Iterator<W> iter = collected.values().iterator();
            if (iter.hasNext()) {
                cachedService = iter.next();
                return Optional.of(cachedService);
            }
        }
        return Optional.empty();
    }

    /**
     * Return the list of service objects for all services collected by this
     * {@code ServiceCollector}. This is effectively a mapping of
     * {@link #serviceReferences()} to services.
     * 
     * @return a list of service objects which may be empty
     */
    public List<W> services() {
        synchronized (this) {
            return Collections
                .unmodifiableList(new ArrayList<>(collected.values()));
        }
    }

    /**
     * Return the number of services collected by this
     * {@code ServiceCollector}. This value may not be correct during the
     * execution of handlers.
     * 
     * @return The number of services collected
     */
    public int size() {
        synchronized (this) {
            return collected.size();
        }
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
        return modificationCount[0];
    }

    /**
     * Checks if this {@code ServiceCollector} is open.
     *
     * @return true, if is open
     */
    public boolean isOpen() {
        return modificationCount[0] >= 0;
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
        synchronized (this) {
            if (collected.isEmpty()) {
                return new TreeMap<>(Collections.reverseOrder());
            }
            return Collections.unmodifiableSortedMap(new TreeMap<>(collected));
        }
    }

    /**
     * Return if this {@code ServiceCollector} is empty.
     * 
     * @return {@code true} if this {@code ServiceCollector} 
     *     has not collected any services.
     */
    public boolean isEmpty() {
        synchronized (this) {
            return collected.isEmpty();
        }
    }

    /**
     * Remove a service from this {@code ServiceCollector}.
     * 
     * The specified service will be removed from this {@code ServiceCollector}.
     * 
     * @param reference The reference to the service to be removed.
     */
    public void remove(ServiceReference<S> reference) {
        removeFromCollected(reference);
    }

}
