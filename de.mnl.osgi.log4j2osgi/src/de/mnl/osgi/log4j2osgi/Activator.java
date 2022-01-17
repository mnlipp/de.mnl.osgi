/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2018 Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.log4j2osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.logging.log4j.spi.Provider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * The activator for this bundle.
 */
public class Activator implements BundleActivator {

    private ServiceRegistration<Provider> serviceRegistration;

    /**
     * Start.
     *
     * @param context the context
     * @throws Exception the exception
     */
    @Override
    public void start(BundleContext context) throws Exception {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("APIVersion", "2.6.0");
        serviceRegistration = context.registerService(Provider.class,
            new OsgiProvider(), props);
    }

    /**
     * Stop.
     *
     * @param context the context
     * @throws Exception the exception
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        serviceRegistration.unregister();
    }

}
