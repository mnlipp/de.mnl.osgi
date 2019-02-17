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

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;

@RunWith(MockitoJUnitRunner.class)
public class TrackerTests {

    protected static final Logger logger
        = Logger.getLogger("Logger Call Test");

    private final BundleContext context = FrameworkUtil
        .getBundle(TrackerTests.class).getBundleContext();

    @Test
    @SuppressWarnings("resource")
    public void testBasics() throws InterruptedException {
        context.registerService(SampleService.class, new SampleService(), null);
        Map<String, Object> results = new HashMap<>();
        ServiceCollector<SampleService> trk1bck;
        try (ServiceCollector<SampleService> trk1
            = new ServiceCollector<SampleService>(
                context, SampleService.class)) {
            trk1bck = trk1;
            trk1.setOnFirstBound(
                (ref, svc) -> {
                    results.put("firstCalled", true);
                    assertTrue(trk1.service().isPresent());
                    assertEquals(svc, trk1.service().get());
                    assertEquals(svc, trk1.services().get(0));
                    assertEquals(ref, trk1.serviceReference().get());
                    assertEquals(ref, trk1.serviceReferences().get(0));
                    assertEquals(ref,
                        trk1.collected().keySet().iterator().next());
                    assertEquals(svc,
                        trk1.collected().values().iterator().next());
                    assertEquals(1, trk1.size());
                })
                .setOnBound((ref, svc) -> results.put("availCalled", true))
                .setOnUnbinding(
                    (ref, svc) -> results.put("unavailCalled", true))
                .setOnLastUnbinding((ref, svc) -> {
                    results.put("lastCalled", true);
                    assertEquals(svc, trk1.service().get());
                });
            trk1.open();
            trk1.waitForService(1000);
            assertEquals(Boolean.TRUE, results.get("firstCalled"));
            assertEquals(Boolean.TRUE, results.get("availCalled"));
        }
        assertFalse(trk1bck.service().isPresent());
        assertEquals(Boolean.TRUE, results.get("unavailCalled"));
        assertEquals(Boolean.TRUE, results.get("lastCalled"));
    }

    @Test
    public void testRanking() throws InterruptedException {
        // Prepare
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, 1);
        SampleService service1 = new SampleService();
        context.registerService(SampleService.class, service1, props);
        props.put(Constants.SERVICE_RANKING, 2);
        SampleService service2 = new SampleService();
        context.registerService(SampleService.class, service2, props);

        // Now run
        Map<String, Object> results = new HashMap<>();
        try (ServiceCollector<SampleService> trk1
            = new ServiceCollector<SampleService>(
                context, SampleService.class)) {
            trk1.setOnFirstBound(
                (ref, svc) -> {
                    results.put("firstCalled", true);
                })
                .setOnBound((ref, svc) -> {
                    int invocation = (int) results
                        .computeIfAbsent("availCalled", k -> 0) + 1;
                    results.put("availCalled", invocation);
                    if (invocation == 1) {
                        return;
                    }
                    assertEquals(2, trk1.services().size());
                    assertEquals(2, trk1.serviceReferences().size());
                    assertEquals(2, trk1.collected().entrySet().size());
                    assertEquals(service2,
                        trk1.collected().values().iterator().next());
                    assertEquals(2, trk1.size());
                })
                .setOnUnbinding(
                    (ref, svc) -> {
                        int invocation = (int) results
                            .computeIfAbsent("unavailCalled", k -> 0) + 1;
                        results.put("unavailCalled", invocation);
                    })
                .setOnLastUnbinding((ref, svc) -> {
                    results.put("lastCalled", true);
                    assertEquals(svc, trk1.service().get());
                });
            trk1.open();
            trk1.waitForService(1000);
            assertEquals(Boolean.TRUE, results.get("firstCalled"));
            assertEquals(2, results.get("availCalled"));
        }
        assertEquals(2, results.get("unavailCalled"));
        assertEquals(Boolean.TRUE, results.get("lastCalled"));
    }
}