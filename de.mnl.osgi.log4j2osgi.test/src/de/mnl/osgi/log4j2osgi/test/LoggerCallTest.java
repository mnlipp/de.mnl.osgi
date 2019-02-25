package de.mnl.osgi.log4j2osgi.test;

import de.mnl.osgi.coreutils.ServiceCollector;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;

@RunWith(MockitoJUnitRunner.class)
public class LoggerCallTest {

    protected static final Logger logger
        = LogManager.getLogger("Logger Call Test");

    private final BundleContext context
        = FrameworkUtil.getBundle(LoggerCallTest.class).getBundleContext();

    @Test
    public void testBasicUsage() throws InterruptedException {
        AtomicBoolean gotIt = new AtomicBoolean();
        try (ServiceCollector<LogReaderService,
                LogReaderService> logReaderProvider
                    = new ServiceCollector<>(context,
                        LogReaderService.class)) {
            logReaderProvider.open();
            LogReaderService logReader
                = logReaderProvider.waitForService(1000).get();
            CountDownLatch latch = new CountDownLatch(1);
            logReader.addLogListener(new LogListener() {
                @Override
                public void logged(LogEntry entry) {
                    if (entry.getMessage()
                        .startsWith("Calling Logger from Test.")) {
                        assertEquals("de.mnl.osgi.log4j2osgi.test",
                            entry.getBundle().getSymbolicName());
                        gotIt.set(true);
                        latch.countDown();
                    }
                }
            });
            logger.info("Calling Logger from {}.", "Test");
            latch.await(1000, TimeUnit.MILLISECONDS);
            assertTrue(gotIt.get());
        }
    }

    @Test
    public void testBuffered() throws InterruptedException, BundleException {
        Bundle osgiLoggerFactory = context
            .getServiceReference(org.osgi.service.log.LoggerFactory.class)
            .getBundle();
        assertNotNull(osgiLoggerFactory);
        try {
            osgiLoggerFactory.stop();
            logger.info("Calling Logger {}", "Info");
            logger.warn(() -> "Calling Logger " + "Warn");
            logger.error("Calling Logger Error", new Throwable());
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
                    if (entry.getMessage().startsWith("Calling Logger Info")
                        && entry.getLogLevel() == LogLevel.INFO) {
                        latch.countDown();
                    }
                    if (entry.getMessage().startsWith("Calling Logger Warn")
                        && entry.getLogLevel() == LogLevel.WARN) {
                        latch.countDown();
                    }
                    if (entry.getMessage().startsWith("Calling Logger Error")
                        && entry.getLogLevel() == LogLevel.ERROR
                        && entry.getException() != null) {
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

    /*
     * (Too) basic.
     */
    @Test
    public void testLoggerCache() throws InterruptedException, BundleException {
        Logger logger1 = LogManager.getLogger("Test");
        Logger logger2 = LogManager.getLogger("Test");
        assertTrue(logger1 == logger2);
    }

}