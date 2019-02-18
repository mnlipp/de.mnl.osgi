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

import de.mnl.coreutils.ServiceCollector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import net.jodah.concurrentunit.Waiter;

import org.junit.After;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

@RunWith(MockitoJUnitRunner.class)
public class CollectorTests {

    protected static final Logger logger
        = Logger.getLogger("Logger Call Test");

    private final BundleContext context = FrameworkUtil
        .getBundle(CollectorTests.class).getBundleContext();

    private List<ServiceRegistration<?>> cleanup = new ArrayList<>();

    @After
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
    public void testUseAvailable() throws InterruptedException {
        cleanup.add(context.registerService(SampleService.class,
            new SampleService(), null));
        TestCollector<SampleService> coll1bck;
        try (TestCollector<SampleService> coll1
            = new TestCollector<SampleService>(
                context, SampleService.class)) {
            coll1bck = coll1;
            coll1.open();
            coll1.waitForService(1000);
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(1, coll1.callbacks.get("bound").intValue());
            assertNull(coll1.callbacks.get("modified"));
        }
        assertFalse(coll1bck.service().isPresent());
        assertEquals(1, coll1bck.callbacks.get("unbinding").intValue());
        assertEquals(1, coll1bck.callbacks.get("lastUnbinding").intValue());
        assertNull(coll1bck.callbacks.get("modified"));
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
        SampleService service1 = new SampleService();
        cleanup.add(context.registerService(SampleService.class,
            service1, props));
        props.put(Constants.SERVICE_RANKING, 2);
        SampleService service2 = new SampleService();
        cleanup.add(context.registerService(SampleService.class,
            service2, props));

        // Now run
        TestCollector<SampleService> coll1bck;
        try (TestCollector<SampleService> coll1
            = new TestCollector<SampleService>(context,
                SampleService.class)) {
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
        try (TestCollector<SampleService> coll1
            = new TestCollector<SampleService>(context,
                SampleService.class)) {
            coll1.open();
            assertTrue(coll1.isEmpty());

            // Add first service
            Hashtable<String, Object> props = new Hashtable<>();
            props.put(Constants.SERVICE_RANKING, 1);
            SampleService service1 = new SampleService();
            final ServiceRegistration<SampleService> reg1
                = context.registerService(
                    SampleService.class, service1, props);
            assertEquals(1, coll1.callbacks.get("firstBound").intValue());
            assertEquals(1, coll1.callbacks.get("bound").intValue());
            assertNull(coll1.callbacks.get("modified"));
            assertEquals(service1, coll1.service().get());

            // Add second
            props.put(Constants.SERVICE_RANKING, 2);
            SampleService service2 = new SampleService();
            final ServiceRegistration<SampleService> reg2
                = context.registerService(
                    SampleService.class, service2, props);
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
        try (TestCollector<SampleService> coll1
            = new TestCollector<SampleService>(context, SampleService.class)) {
            coll1.open();
            assertTrue(coll1.isEmpty());
            // Prepare threads
            for (int i = 0; i < N; i++) {
                final int count = i;
                new Thread(() -> {
                    started.countDown();
                    synchronized (starter) {
                        try {
                            starter.wait();
                        } catch (InterruptedException e) {
                            waiter.fail();
                        }
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
            TestCollector<SampleService> coll1, int count, int[] services) {
        // Add service
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, 1);
        SampleService service1 = new SampleService();
        final ServiceRegistration<SampleService> reg1
            = context.registerService(
                SampleService.class, service1, props);
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