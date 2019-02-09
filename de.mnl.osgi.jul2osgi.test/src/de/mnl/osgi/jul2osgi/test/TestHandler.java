package de.mnl.osgi.jul2osgi.test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class TestHandler extends Handler {

    public static List<LogRecord> records = new ArrayList<>();
    
    public TestHandler() {
    }

    @Override
    public void publish(LogRecord record) {
        records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

}
