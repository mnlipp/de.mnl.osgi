package de.mnl.osgi.log4j2osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.logging.log4j.spi.Provider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    private ServiceRegistration<Provider> serviceRegistration;

    @Override
    public void start(BundleContext context) throws Exception {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("APIVersion", "2.6.0");
        serviceRegistration = context.registerService(Provider.class,
            new OsgiProvider(), props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        serviceRegistration.unregister();
    }

}
