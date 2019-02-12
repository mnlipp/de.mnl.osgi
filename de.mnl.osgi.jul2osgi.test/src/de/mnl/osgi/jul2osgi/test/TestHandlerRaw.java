package de.mnl.osgi.jul2osgi.test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class TestHandlerRaw extends Handler {

    public static List<LogRecord> records = new ArrayList<>();

    public TestHandlerRaw() {
    }

    @Override
    public void publish(LogRecord record) {
        LogRecord copy = new LogRecord(record.getLevel(), record.getMessage());
        records.add(copy);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

}
