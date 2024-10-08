package de.mnl.osgi.slf4j2osgi.test;

import de.mnl.osgi.coreutils.ServiceCollector;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public class LoggerCallTest {

    protected static final Logger logger
        = LoggerFactory.getLogger("Logger Call Test");
    protected static final Logger classLogger
        = LoggerFactory.getLogger(LoggerCallTest.class);

    private final BundleContext context = FrameworkUtil
        .getBundle(LoggerCallTest.class).getBundleContext();

    @Test
    public void testBasicUsage() throws InterruptedException {
        AtomicBoolean gotIt = new AtomicBoolean();
        try (ServiceCollector<LogReaderService,
                LogReaderService> logReaderProvider
                    = new ServiceCollector<>(context, LogReaderService.class)) {
            logReaderProvider.open();
            LogReaderService logReader
                = logReaderProvider.waitForService(1000).get();
            CountDownLatch latch = new CountDownLatch(1);
            logReader.addLogListener(new LogListener() {
                @Override
                public void logged(LogEntry entry) {
                    if (entry.getMessage()
                        .startsWith("Calling Logger from Test.")) {
                        assertEquals("de.mnl.osgi.slf4j2osgi.test",
                            entry.getBundle().getSymbolicName());
                        gotIt.set(true);
                        latch.countDown();
                    }
                }
            });
            classLogger.info("Calling Logger from {}.", "Test");
            latch.await(1000, TimeUnit.MILLISECONDS);
            assertTrue(gotIt.get());
        }
    }

    @Test
    public void testClassUsage() throws InterruptedException {
        AtomicBoolean gotIt = new AtomicBoolean();
        try (ServiceCollector<LogReaderService,
                LogReaderService> logReaderProvider
                    = new ServiceCollector<>(context, LogReaderService.class)) {
            logReaderProvider.open();
            LogReaderService logReader
                = logReaderProvider.waitForService(1000).get();
            CountDownLatch latch = new CountDownLatch(1);
            logReader.addLogListener(new LogListener() {
                @Override
                public void logged(LogEntry entry) {
                    if (entry.getMessage()
                        .startsWith("Calling Logger from Test.")) {
                        assertEquals("de.mnl.osgi.slf4j2osgi.test",
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
        Bundle osgiLoggerFactory = context.getServiceReference(
            org.osgi.service.log.LoggerFactory.class).getBundle();
        assertNotNull(osgiLoggerFactory);
        try {
            osgiLoggerFactory.stop();
            logger.info("Calling Logger {}", "Info");
            logger.warn("Calling Logger Warn");
            logger.error("Calling Logger Error", new Throwable());
            osgiLoggerFactory.start();
            try (ServiceCollector<LogReaderService,
                    LogReaderService> logReaderProvider
                        = new ServiceCollector<>(context,
                            LogReaderService.class)) {
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
        Logger logger1 = LoggerFactory.getLogger("Test");
        Logger logger2 = LoggerFactory.getLogger("Test");
        assertTrue(logger1 == logger2);
    }
}