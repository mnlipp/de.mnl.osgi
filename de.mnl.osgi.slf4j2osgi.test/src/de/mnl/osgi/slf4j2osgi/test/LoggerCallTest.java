package de.mnl.osgi.slf4j2osgi.test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class LoggerCallTest {

    protected static final Logger logger
        = LoggerFactory.getLogger("Logger Call Test");

    private final BundleContext context = FrameworkUtil
        .getBundle(LoggerCallTest.class).getBundleContext();

    private <T> T getService(Class<T> clazz) throws InterruptedException {
        ServiceTracker<T, T> st = new ServiceTracker<>(context, clazz, null);
        st.open();
        return st.waitForService(1000);
    }

    @Test
    public void testExample() throws InterruptedException {
        CountDownLatch recordLatch = new CountDownLatch(1);
        LogReaderService logService = getService(LogReaderService.class);
//        String expectedMessage = "de.mnl.osgi.jul2osgi.test.LoggerCallTest"
//            + ".testExample: Calling Logger from Test.";
        logService.addLogListener(new LogListener() {
            @Override
            public void logged(LogEntry entry) {
//                if (entry.getLoggerName().equals("Logger Call Test")
//                    && entry.getMessage().equals(expectedMessage)
//                    && entry.getBundle().getSymbolicName()
//                        .equals("de.mnl.osgi.jul2osgi.test")) {
//                    recordLatch.countDown();
//                }
            }
        });
        logger.info("Calling Logger from {0}.", "Test");
//        assertTrue(recordLatch.await(1000, TimeUnit.MILLISECONDS));
        String handled = null;
//        for (LogRecord record : TestHandlerRaw.records) {
//            if (record.getMessage().equals(expectedMessage)) {
//                handled = record.getMessage();
//            }
//        }
//        assertNotNull(handled);
//        handled = null;
//        for (LogRecord record : TestHandlerExt.records) {
//            if (record.getMessage().startsWith(expectedMessage
//                + " [de.mnl.osgi.jul2osgi.test|OSGi2JUL Test Bundle|")) {
//                handled = record.getMessage();
//            }
//        }
//        assertNotNull(handled);
    }
}