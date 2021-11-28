/*
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

package de.mnl.osgi.jul2osgi.lib;

import de.mnl.osgi.jul2osgi.lib.LogManager.LogInfo;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A {@link Logger} that forwards the {@link LogRecord}s to OSGi.
 */
public class ForwardingLogger extends Logger {

    private LogManager manager;
    private Class<?> definingClass;
    private static final ContextHelper CTX_HLPR = new ContextHelper();

    public ForwardingLogger(LogManager manager, Logger orig) {
        super(orig.getName(), orig.getResourceBundleName());
        this.manager = manager;

        // Find class that invoked getLogger
        if (getName().length() == 0 || getName().equals("global")) {
            return;
        }
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            if (ste.getMethodName().equals("getLogger")) {
                Class<?>[] classes = CTX_HLPR.getClassContext();
                // Skip all calls within logging package
                while (classes[i].getPackageName()
                    .equals("java.util.logging")) {
                    i += 1;
                }
                definingClass = classes[i];
                break;
            }
        }
    }

    @Override
    public void log(LogRecord record) {
        if (!isLoggable(record.getLevel())) {
            return;
        }
        Filter theFilter = getFilter();
        if (theFilter != null && !theFilter.isLoggable(record)) {
            return;
        }

        // Unless parent handlers were to be ignored, we now log to OSGi
        manager.log(new LogInfo(definingClass, record));

        // We still invoke explicitly attached handlers. Note that the
        // default root handler will automatically *not* be invoked
        // because it is not wrapped as ForwardingLogger.

        Logger logger = this;
        while (logger != null) {
            final Handler[] loggerHandlers = logger.getHandlers();
            for (Handler handler : loggerHandlers) {
                handler.publish(record);
            }
            final boolean useParentHdls = logger.getUseParentHandlers();
            if (!useParentHdls) {
                break;
            }
            logger = logger.getParent();
        }
    }

    private static class ContextHelper extends SecurityManager {
        @Override
        public Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }
}
