package de.mnl.osgi.jul2osgi.test;

import de.mnl.osgi.coreutils.ServiceCollector;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;

@ExtendWith(MockitoExtension.class)
public class LoggerCallTest {

    protected static final Logger logger = Logger.getLogger("Logger Call Test");

    private final BundleContext context
        = FrameworkUtil.getBundle(getClass()).getBundleContext();

    private <T> T getService(Class<T> clazz) throws InterruptedException {
        ServiceTracker<T, T> st = new ServiceTracker<>(context, clazz, null);
        st.open();
        return st.waitForService(1000);
    }

    @Test
    public void testExample() throws InterruptedException {
        CountDownLatch recordLatch = new CountDownLatch(1);
        LogReaderService logService = getService(LogReaderService.class);
        String expectedMessage = "de.mnl.osgi.jul2osgi.test.LoggerCallTest"
            + ".testExample: Calling Logger from Test.";
        logService.addLogListener(new LogListener() {
            @Override
            public void logged(LogEntry entry) {
                if (entry.getLoggerName().equals("Logger Call Test")
                    && entry.getMessage().equals(expectedMessage)
                    && entry.getBundle().getSymbolicName()
                        .equals("de.mnl.osgi.jul2osgi.test")) {
                    recordLatch.countDown();
                }
            }
        });
        logger.log(Level.INFO, "Calling Logger from {0}.", "Test");
        assertTrue(recordLatch.await(1000, TimeUnit.MILLISECONDS));
        String handled = null;
        for (LogRecord record : TestHandlerRaw.records) {
            if (record.getMessage().equals(expectedMessage)) {
                handled = record.getMessage();
            }
        }
        assertNotNull(handled);
        handled = null;
        for (LogRecord record : TestHandlerExt.records) {
            if (record.getMessage().startsWith(expectedMessage
                + " [de.mnl.osgi.jul2osgi.test|OSGi2JUL Test Bundle|")) {
                handled = record.getMessage();
            }
        }
        assertNotNull(handled);
    }

    @Test
    public void testBuffered() throws InterruptedException, BundleException {
        Bundle osgiLoggerFactory = context
            .getServiceReference(org.osgi.service.log.LoggerFactory.class)
            .getBundle();
        assertNotNull(osgiLoggerFactory);
        try {
            osgiLoggerFactory.stop();
            logger.log(Level.INFO, "Calling Logger {0}", "Info");
            logger.log(Level.WARNING, "Calling Logger Warn");
            logger.log(Level.SEVERE, "Calling Logger Error", new Throwable());
            osgiLoggerFactory.start();
            try (ServiceCollector<LogReaderService,
                    LogReaderService> logReaderProvider
                        = new ServiceCollector<>(
                            context, LogReaderService.class)) {
                logReaderProvider.open();
                LogReaderService logReader
                    = logReaderProvider.waitForService(1000).get();
                CountDownLatch latch = new CountDownLatch(3);
                for (LogEntry entry : Collections.list(logReader.getLog())) {
                    if (entry.getMessage().indexOf("Calling Logger Info") >= 0
                        && entry.getLogLevel() == LogLevel.INFO) {
                        latch.countDown();
                    }
                    if (entry.getMessage().indexOf("Calling Logger Warn") >= 0
                        && entry.getLogLevel() == LogLevel.WARN) {
                        latch.countDown();
                    }
                    if (entry.getMessage().indexOf("Calling Logger Error") >= 0
                        && entry.getLogLevel() == LogLevel.ERROR) {
                        assertNotNull(entry.getException());
                        latch.countDown();
                    }
                }
                latch.await(1000, TimeUnit.MILLISECONDS);
                assertEquals(0, latch.getCount());
            }
        } finally {
            osgiLoggerFactory.start();
        }
    }
}