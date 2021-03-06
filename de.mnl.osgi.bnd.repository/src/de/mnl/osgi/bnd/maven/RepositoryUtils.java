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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The Class Utils.
 */
public final class RepositoryUtils {

    /** The Constant LIST_ITEM_SEPARATOR. */
    public static final Pattern LIST_ITEM_SEPARATOR
        = Pattern.compile("\\s*,\\s*");

    private RepositoryUtils() {
    }

    /**
     * Itemize list.
     *
     * @param list the list
     * @return the stream
     */
    public static Stream<String> itemizeList(String list) {
        if (list == null) {
            return Stream.empty();
        }
        return LIST_ITEM_SEPARATOR.splitAsStream(list);
    }

    /**
     * Run ignoring any throwable.
     *
     * @param runnable the function to be executed
     */
    @SuppressWarnings({ "PMD.AvoidCatchingThrowable", "PMD.EmptyCatchBlock",
        "PMD.AvoidDuplicateLiterals" })
    public static void runIgnoring(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            // Ignored
        }
    }

    /**
     * Run ignoring any throwable. If an exception occurs, the
     * fallback value is returned.
     *
     * @param <T> the return type
     * @param callable the function to be executed
     * @param fallback the fallback value
     * @return the t
     */
    @SuppressWarnings({ "PMD.AvoidCatchingThrowable", "PMD.EmptyCatchBlock" })
    public static <T> T runIgnoring(Callable<T> callable, T fallback) {
        try {
            return callable.call();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * A runnable that may throw an exception.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {

        /**
         * The operation to run.
         *
         * @throws Exception the exception
         */
        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        void run() throws Exception;
    }

    /**
     * Converts any exception thrown by the supplier to an
     * {@link UndeclaredThrowableException}.
     *
     * @param <T> the return value
     * @param supplier the supplier
     * @return the t
     */
    @SuppressWarnings({ "PMD.AvoidCatchingThrowable",
        "PMD.AvoidCatchingGenericException", "PMD.AvoidRethrowingException",
        "PMD.AvoidDuplicateLiterals" })
    public static <T> T unthrow(Callable<T> supplier) {
        try {
            return supplier.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }

    /**
     * Converts any exception thrown by the runnable to an
     * {@link UndeclaredThrowableException}.
     *
     * @param runnable the runnable
     */
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.AvoidRethrowingException", "PMD.AvoidCatchingThrowable" })
    public static void unthrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }

    /**
     * Catches {@link UndeclaredThrowableException}s and unwraps
     * any underlying exception of the given type.
     *
     * @param <T> the return type
     * @param <E> the type of exception that is unwrapped
     * @param rethrown the type of exception that is rethrown
     * @param supplier the supplier
     * @return the result from invoking the {@code supplier}
     * @throws E the exception type
     */
    @SuppressWarnings({ "unchecked", "PMD.AvoidCatchingGenericException",
        "PMD.AvoidRethrowingException" })
    public static <T, E extends Throwable> T rethrow(Class<E> rethrown,
            Callable<T> supplier) throws E {
        try {
            return supplier.call();
        } catch (UndeclaredThrowableException e) {
            if (rethrown
                .isAssignableFrom(e.getUndeclaredThrowable().getClass())) {
                throw (E) e.getUndeclaredThrowable();
            }
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Catches {@link UndeclaredThrowableException}s and unwraps
     * any underlying exception of the given type.
     *
     * @param <T> the return type
     * @param <E1> the first exception type
     * @param <E2> the second exception type
     * @param rethrown1 the class of the first rethrown exception type
     * @param rethrown2 the class of the second rethrown exception type
     * @param function the function to be executed
     * @return the result from invoking the {@code supplier}
     * @throws E1 the e1
     * @throws E2 the e2
     */
    @SuppressWarnings({ "unchecked", "PMD.AvoidCatchingGenericException",
        "PMD.AvoidRethrowingException" })
    public static <T, E1 extends Throwable, E2 extends Throwable> T
            rethrow(Class<E1> rethrown1, Class<E2> rethrown2,
                    Callable<T> function) throws E1, E2 {
        try {
            return function.call();
        } catch (UndeclaredThrowableException e) {
            if (rethrown1
                .isAssignableFrom(e.getUndeclaredThrowable().getClass())) {
                throw (E1) e.getUndeclaredThrowable();
            }
            if (rethrown2
                .isAssignableFrom(e.getUndeclaredThrowable().getClass())) {
                throw (E2) e.getUndeclaredThrowable();
            }
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Catches {@link UndeclaredThrowableException}s and unwraps
     * any underlying exception of the given type.
     *
     * @param <E> the type of exception that is unwrapped
     * @param rethrown the type of exception that is rethrown
     * @param runnable the runnable
     * @throws E the exception type
     */
    @SuppressWarnings({ "unchecked", "PMD.AvoidCatchingGenericException" })
    public static <E extends Throwable> void rethrow(Class<E> rethrown,
            ThrowingRunnable runnable) throws E {
        try {
            runnable.run();
        } catch (UndeclaredThrowableException e) {
            if (rethrown
                .isAssignableFrom(e.getUndeclaredThrowable().getClass())) {
                throw (E) e.getUndeclaredThrowable();
            }
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }
}
