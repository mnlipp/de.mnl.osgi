/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.bnd.maven;

import aQute.service.reporter.Reporter;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A helper for awaiting asynchronously executing tasks.
 */
public class TaskCollection {

    private final Reporter reporter;
    private final ExecutorService execSvc;
    private final Queue<Future<?>> executing = new ConcurrentLinkedQueue<>();

    /**
     * Instantiates a new task collection that uses the given
     * executor service for executing tasks.
     *
     * @param executorService the executor service
     * @param reporter the reporter
     */
    public TaskCollection(ExecutorService executorService, Reporter reporter) {
        this.reporter = reporter;
        execSvc = executorService;
    }

    /**
     * Submit the given task.
     *
     * @param task the task
     * @return the future
     */
    public Future<?> submit(Runnable task) {
        Future<?> result = execSvc.submit(task);
        executing.add(result);
        return result;
    }

    /**
     * Submit the given task.
     *
     * @param <T> the generic type
     * @param task the task
     * @return the future
     */
    public <T> Future<T> submit(Callable<T> task) {
        Future<T> result = execSvc.submit(task);
        executing.add(result);
        return result;
    }

    /**
     * Await the completion of all submitted tasks.
     *
     * @throws InterruptedException the interrupted exception
     */
    public void await() throws InterruptedException {
        while (!executing.isEmpty()) {
            try {
                executing.poll().get();
            } catch (ExecutionException e) {
                reporter.exception(e.getCause(),
                    "Asynchronously executed task failed: %s", e.getMessage());
            }
        }
    }
}
