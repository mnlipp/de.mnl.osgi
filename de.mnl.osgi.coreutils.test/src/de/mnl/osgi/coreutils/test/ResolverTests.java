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

import de.mnl.osgi.coreutils.ServiceResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

public class ResolverTests {

    private final BundleContext context = FrameworkUtil
        .getBundle(ResolverTests.class).getBundleContext();

    private List<ServiceRegistration<?>> cleanup = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        for (ServiceRegistration<?> reg : cleanup) {
            reg.unregister();
        }
    }

    /**
     * Basic test.
     *
     * @throws InterruptedException the interrupted exception
     */
    @SuppressWarnings("resource")
    @Test
    public void testBasic() throws InterruptedException {
        // Now run
        Map<String, Object> results = new HashMap<>();
        ServiceResolver resolverBck;
        try (ServiceResolver resolver = new ServiceResolver(context)) {
            resolverBck = resolver;
            resolver.setOnResolved(() -> results.put("resolved", true));
            resolver.setOnDissolving(() -> results.put("dissolving", true));
            resolver.setOnRebound(name -> results.put("rebound", true));
            // Add dependencies
            resolver.addDependency(SampleService1.class);
            resolver.addDependency(SampleService2.class);
            resolver.open();
            assertTrue(resolver.isOpen());
            // Change services
            Hashtable<String, Object> props = new Hashtable<>();
            props.put(Constants.SERVICE_RANKING, 1);
            SampleService1 service1 = new SampleService1();

            // Register first, no effect
            final ServiceRegistration<SampleService1> reg1 = context
                .registerService(SampleService1.class, service1, props);
            assertFalse(resolver.isResolved());
            assertTrue(results.isEmpty());

            // Register second, resolved
            props.put(Constants.SERVICE_RANKING, 1);
            SampleService2 service2 = new SampleService2();
            final ServiceRegistration<SampleService2> reg2 = context
                .registerService(SampleService2.class, service2, props);
            assertTrue(resolver.isResolved());
            assertEquals(Boolean.TRUE, results.get("resolved"));
            assertEquals(1, results.size());
            results.clear();
            resolver.get(SampleService1.class).equals(service1);
            resolver.get(SampleService2.class).equals(service2);

            // Unregister one
            reg1.unregister();
            assertFalse(resolver.isResolved());
            assertEquals(Boolean.TRUE, results.get("dissolving"));
            assertEquals(1, results.size());
            results.clear();

            // Register again
            final ServiceRegistration<SampleService1> reg1a = context
                .registerService(SampleService1.class, service1, props);
            assertTrue(resolver.isResolved());
            assertEquals(Boolean.TRUE, results.get("resolved"));
            assertEquals(1, results.size());
            results.clear();

            // Replace SampleService2 with SampleService2a
            SampleService2 service2a = new SampleService2();
            props.put(Constants.SERVICE_RANKING, 2);
            final ServiceRegistration<SampleService2> reg2a = context
                .registerService(SampleService2.class, service2a, props);
            assertTrue(resolver.isResolved());
            assertEquals(Boolean.TRUE, results.get("rebound"));
            assertEquals(1, results.size());
            results.clear();
            resolver.get(SampleService1.class).equals(service1);
            resolver.get(SampleService2.class).equals(service2a);

            // Now unregister all
            reg1a.unregister();
            assertFalse(resolver.isResolved());
            assertEquals(Boolean.TRUE, results.get("dissolving"));
            assertEquals(1, results.size());
            results.clear();
            reg2.unregister();
            assertFalse(resolver.isResolved());
            assertEquals(0, results.size());
            reg2a.unregister();
            assertFalse(resolver.isResolved());
            assertEquals(0, results.size());
        }
        assertTrue(!resolverBck.isOpen());
    }

}