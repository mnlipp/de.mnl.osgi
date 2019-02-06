package de.mnl.osgi.jul2osgi.test;

import java.util.logging.Logger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

@RunWith(MockitoJUnitRunner.class)
public class LoggerCallTest {

    protected static final Logger logger 
    	= Logger.getLogger(LoggerCallTest.class.getName());
    
	private final BundleContext context = FrameworkUtil
			.getBundle(LoggerCallTest.class).getBundleContext();

	@Test
	public void testExample() throws InterruptedException {
		for (Bundle bundle: context.getBundles()) {
			System.out.println(bundle);
		}
		logger.info("Calling Logger.");
		logger.severe("Calling Logger again.");
	}
}