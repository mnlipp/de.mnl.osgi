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
 */

package de.mnl.osgi.coreutils.test;

import de.mnl.osgi.coreutils.ServiceCollector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import net.jodah.concurrentunit.Waiter;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

@ExtendWith(MockitoExtension.class)
public class CollectorTests {

    private final BundleContext context = FrameworkUtil
        .getBundle(CollectorTests.class).getBundleContext();

    private List<ServiceRegistration<?>> cleanup = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        for (ServiceRegistration<?> reg : cleanup) {
            reg.unregister();
        }
    }

    private class TestCollector<S> extends ServiceCollector<S, S> {

        public Map<String, Integer> callbacks = new HashMap<>();

        private void incCount(String counter) {
            callbacks.put(counter, callbacks.getOrDefault(counter, 0) + 1);
        }

        public TestCollector(BundleContext context, Class<S> clazz) {
            super(context, clazz);
            setOnBound(
                (ref, svc) -> {
                    incCount("firstBound");
                    assertTrue(service().isPresent());
                    assertEquals(svc, service().get());
                    assertEquals(svc, services().get(0));
                    assertEquals(ref, serviceReference().get());
                    assertEquals(ref, serviceReferences().get(0));
                    assertEquals(ref, collected().keySet().iterator().next());
                    assertEquals(svc, collected().values().iterator().next());
                    assertEquals(1, size());
                });
            setOnAdded((ref, svc) -> incCount("bound"));
            setOnRemoving((ref, svc) -> incCount("unbinding"));
            setOnUnbinding((ref, svc) -> {
                incCount("lastUnbinding");
                assertEquals(svc, service().get());
            });
            setOnModfied((ref, svc) -> incCount("modified"));
        }
    }

    /**
     * An available service must be found.
     *
     * @throws InterruptedException the interrupted exception
     */
    @Test
    @SuppressWarnings("resource")
    public void testFindAvailable() throws InterruptedException {
        cleanup.add(context.registerService(SampleService1.class,
            new SampleService1(), null));
        // Keep reference to auto closed object for checks
        TestCollector<SampleService1> coll1alias;
        try (TestCollector<SampleService1> coll1
            = new TestCollector<SampleService1>(
                context, SampleService1.class)) {
            coll1alias = coll1;
            coll1.open();
            coll1.waitForService(1000);
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(1, coll1.callbacks.get("bound").intValue());
            assertNull(coll1.callbacks.get("modified"));
        }
        assertFalse(coll1alias.service().isPresent());
        assertEquals(1, coll1alias.callbacks.get("unbinding").intValue());
        assertEquals(1, coll1alias.callbacks.get("lastUnbinding").intValue());
        assertNull(coll1alias.callbacks.get("modified"));
    }

    /**
     * A found service must be usable.
     *
     * @throws InterruptedException the interrupted exception
     */
    @Test
    public void testUseAvailable() throws InterruptedException {
        cleanup.add(context.registerService(SampleService1.class,
            new SampleService1(), null));
        try (TestCollector<SampleService1> coll1
            = new TestCollector<SampleService1>(
                context, SampleService1.class)) {
            coll1.open();
            coll1.waitForService(1000);
            assertEquals(42,
                coll1.withService(s -> s.add(20, 22)).get().intValue());
        }
    }

    /**
     * A service in use cannot be unregistered
     *
     * @throws InterruptedException the interrupted exception
     */
    @Test
    public void testDelayedRemoval() throws InterruptedException {
        var serviceRef = context.registerService(SampleService1.class,
            new SampleService1(), null);
        CountDownLatch toBeStarted = new CountDownLatch(1);
        Instant[] calcTimes = new Instant[2];
        new Thread() {
            public void run() {
                try (TestCollector<SampleService1> coll1
                    = new TestCollector<SampleService1>(
                        context, SampleService1.class)) {
                    coll1.open();
                    coll1.waitForService(1000);
                    coll1.withService(s -> {
                        toBeStarted.countDown();
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException e) {
                            // Empty
                        }
                        calcTimes[0] = Instant.now();
                        int result = s.add(20, 22);
                        calcTimes[1] = Instant.now();
                        return result;
                    });
                } catch (InterruptedException e) {
                    // Empty
                }
            }
        }.start();
        // Wait for execution start
        toBeStarted.await();
        Instant unregStart = Instant.now();
        serviceRef.unregister();
        Instant unregEnd = Instant.now();

        // Calculation must start after unreg start (else it's not a test)
        assertTrue(calcTimes[0].isAfter(unregStart));
        // Removal must finish after calc end (else locking doesn't work)
        assertTrue(unregEnd.isAfter(calcTimes[1]));
        // Make sure
        assertTrue(unregEnd.toEpochMilli() - unregStart.toEpochMilli() > 100);
    }

    /**
     * Two services must be found and rankings must be observed.
     *
     * @throws InterruptedException the interrupted exception
     */
    @SuppressWarnings("resource")
    @Test
    public void testRanking() throws InterruptedException {
        // Prepare
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, 1);
        SampleService1 service1 = new SampleService1();
        cleanup.add(context.registerService(SampleService1.class,
            service1, props));
        props.put(Constants.SERVICE_RANKING, 2);
        SampleService1 service2 = new SampleService1();
        cleanup.add(context.registerService(SampleService1.class,
            service2, props));

        // Now run
        TestCollector<SampleService1> coll1bck;
        try (TestCollector<SampleService1> coll1
            = new TestCollector<SampleService1>(context,
                SampleService1.class)) {
            coll1bck = coll1;
            coll1.open();
            coll1.waitForService(1000);
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(2, coll1.callbacks.get("bound").intValue());
            // Weak test, may be lucky
            assertEquals(service2,
                coll1.collected().values().iterator().next());
        }
        assertFalse(coll1bck.service().isPresent());
        assertEquals(2, coll1bck.callbacks.get("unbinding").intValue());
        assertEquals(1, coll1bck.callbacks.get("lastUnbinding").intValue());
    }

    /**
     * Adding and removing must have effects.
     *
     * @throws InterruptedException the interrupted exception
     */
    @Test
    public void testDynamic() throws InterruptedException {
        try (TestCollector<SampleService1> coll1
            = new TestCollector<SampleService1>(context,
                SampleService1.class)) {
            coll1.open();
            assertTrue(coll1.isEmpty());

            // Add first service
            Hashtable<String, Object> props = new Hashtable<>();
            props.put(Constants.SERVICE_RANKING, 1);
            SampleService1 service1 = new SampleService1();
            final ServiceRegistration<SampleService1> reg1
                = context.registerService(
                    SampleService1.class, service1, props);
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(1, coll1.callbacks.get("bound").intValue());
            assertNull(coll1.callbacks.get("modified"));
            assertEquals(service1, coll1.service().get());

            // Add second
            props.put(Constants.SERVICE_RANKING, 2);
            SampleService1 service2 = new SampleService1();
            final ServiceRegistration<SampleService1> reg2
                = context.registerService(
                    SampleService1.class, service2, props);
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(2, coll1.callbacks.get("bound").intValue());
            assertEquals(1, coll1.callbacks.get("modified").intValue());
            assertEquals(service2, coll1.service().get());

            // Remove second
            reg2.unregister();
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(2, coll1.callbacks.get("bound").intValue());
            assertEquals(1, coll1.callbacks.get("unbinding").intValue());
            assertEquals(2, coll1.callbacks.get("modified").intValue());
            assertEquals(service1, coll1.service().get());

            // Remove first
            reg1.unregister();
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(2, coll1.callbacks.get("bound").intValue());
            assertEquals(2, coll1.callbacks.get("unbinding").intValue());
            assertEquals(1, coll1.callbacks.get("lastUnbinding").intValue());
            assertEquals(2, coll1.callbacks.get("modified").intValue());
            assertFalse(coll1.service().isPresent());
        }
    }

    /**
     * Attempt to test concurrency.
     *
     * @throws TimeoutException the timeout exception
     * @throws InterruptedException the interrupted exception
     */
    @Test
    public void testConcurrent() throws TimeoutException, InterruptedException {
        Object starter = new Object();
        Waiter waiter = new Waiter();
        final int N = 100;
        CountDownLatch started = new CountDownLatch(N);
        int[] services = new int[N];
        // Threads prepared
        try (TestCollector<SampleService1> coll1
            = new TestCollector<SampleService1>(context,
                SampleService1.class)) {
            coll1.open();
            assertTrue(coll1.isEmpty());
            // Prepare threads
            for (int i = 0; i < N; i++) {
                final int count = i;
                new Thread(() -> {
                    started.countDown();
                    try {
                        started.await();
                    } catch (InterruptedException e1) {
                        waiter.fail();
                    }
                    try {
                        Thread.sleep(100 + (count / 4) * 8);
                    } catch (InterruptedException e) {
                        waiter.fail(e);
                    }
                    runTestInThread(waiter, coll1, count, services);
                }).start();
            }
            started.await();
            synchronized (starter) {
                starter.notifyAll();
            }
            waiter.await(10000, N);
            assertEquals(N, coll1.callbacks.get("bound").intValue());
            assertEquals(N, coll1.callbacks.get("unbinding").intValue());
            assertFalse(coll1.service().isPresent());
        }
    }

    private void runTestInThread(Waiter waiter,
            TestCollector<SampleService1> coll1, int count, int[] services) {
        // Add service
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, 1);
        SampleService1 service1 = new SampleService1();
        final ServiceRegistration<SampleService1> reg1
            = context.registerService(
                SampleService1.class, service1, props);
        services[count] = coll1.serviceReferences().size();
        try {
            Thread.sleep(count % 10);
        } catch (InterruptedException e) {
            waiter.fail(e);
        }

        // Remove service
        reg1.unregister();
        waiter.resume();
    }
}