/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.threads;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Collection;
import java.security.PrivilegedAction;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.Permission;
import java.lang.reflect.Field;
import org.jboss.logging.Logger;

/**
 * JBoss thread- and executor-related utility and factory methods.
 */
public final class JBossExecutors {

    private static final Logger THREAD_ERROR_LOGGER = Logger.getLogger("org.jboss.threads.errors");

    private JBossExecutors() {}

    private static final RuntimePermission MODIFY_THREAD_PERMISSION = new RuntimePermission("modifyThread");
    private static final RuntimePermission CREATE_PRIVILEGED_THREAD_PERMISSION = new RuntimePermission("createPrivilegedThreads");
    private static final RuntimePermission COPY_CONTEXT_CLASSLOADER_PERMISSION = new RuntimePermission("copyClassLoader");

    private static final AccessControlContext PRIVILEGED_CONTEXT = AccessController.doPrivileged(new PrivilegedAction<AccessControlContext>() {
        public AccessControlContext run() {
            return AccessController.getContext();
        }
    });

    private static final DirectExecutorService DIRECT_EXECUTOR_SERVICE = new ProtectedDirectExecutorService(SimpleDirectExecutor.INSTANCE);
    private static final DirectExecutorService REJECTING_EXECUTOR_SERVICE = new ProtectedDirectExecutorService(RejectingExecutor.INSTANCE);
    private static final DirectExecutorService DISCARDING_EXECUTOR_SERVICE = new ProtectedDirectExecutorService(DiscardingExecutor.INSTANCE);

    // ==================================================
    // DIRECT EXECUTORS
    // ==================================================

    /**
     * Get the direct executor.  This executor will immediately run any task it is given, and propagate back any
     * run-time exceptions thrown.
     *
     * @return the direct executor instance
     */
    public static DirectExecutor directExecutor() {
        return SimpleDirectExecutor.INSTANCE;
    }

    /**
     * Get the direct executor service.  This executor will immediately run any task it is given, and propagate back any
     * run-time exceptions thrown.  It cannot be shut down.
     *
     * @return the direct executor service instance
     */
    public static DirectExecutorService directExecutorService() {
        return DIRECT_EXECUTOR_SERVICE;
    }

    /**
     * Get the rejecting executor.  This executor will reject any task submitted to it.
     *
     * @return the rejecting executor instance
     */
    public static DirectExecutor rejectingExecutor() {
        return RejectingExecutor.INSTANCE;
    }

    /**
     * Get the rejecting executor service.  This executor will reject any task submitted to it.  It cannot be shut down.
     *
     * @return the rejecting executor service instance
     */
    public static DirectExecutorService rejectingExecutorService() {
        return REJECTING_EXECUTOR_SERVICE;
    }

    /**
     * Get the discarding executor.  This executor will silently discard any task submitted to it.
     *
     * @return the discarding executor instance
     */
    public static DirectExecutor discardingExecutor() {
        return DiscardingExecutor.INSTANCE;
    }

    /**
     * Get the discarding executor service.  This executor will silently discard any task submitted to it.  It cannot
     * be shut down.
     *
     * @return the discarding executor service instance
     */
    public static DirectExecutorService discardingExecutorService() {
        return DISCARDING_EXECUTOR_SERVICE;
    }

    /**
     * Create a direct executor which runs with the privileges given by the supplied {@code AccessControlContext} instance.
     *
     * @param delegate the executor to delegate to at the privileged level
     * @param context the {@code AccessControlContext} to use
     * @return the new direct executor
     */
    public static DirectExecutor privilegedExecutor(final DirectExecutor delegate, final AccessControlContext context) {
        return new PrivilegedExecutor(delegate, context);
    }

    /**
     * Create a direct executor which runs with the privileges given by the current access control context.
     *
     * @param delegate the executor to delegate to at the privileged level
     * @return the new direct executor
     */
    public static DirectExecutor privilegedExecutor(final DirectExecutor delegate) {
        return privilegedExecutor(delegate, AccessController.getContext());
    }

    /**
     * Create an executor which executes tasks at the privilege level of this library.
     * TODO - is this the best approach?
     *
     * @param delegate the executor to delegate to at the privileged level
     * @return the new direct executor
     */
    static DirectExecutor highPrivilegeExecutor(final DirectExecutor delegate) {
        checkAccess(CREATE_PRIVILEGED_THREAD_PERMISSION);
        return privilegedExecutor(delegate, PRIVILEGED_CONTEXT);
    }

    /**
     * Create a direct executor which runs tasks with the given context class loader.
     *
     * @param delegate the executor to delegate to
     * @param taskClassLoader the context class loader to use
     * @return the new direct executor
     */
    public static DirectExecutor contextClassLoaderExecutor(final DirectExecutor delegate, final ClassLoader taskClassLoader) {
        return new ContextClassLoaderExecutor(taskClassLoader, delegate);
    }

    /**
     * Create a direct executor which changes the thread name for the duration of a task.
     *
     * @param delegate the executor to delegate to
     * @param newName the thread name to use
     * @return the new direct executor
     */
    public static DirectExecutor threadNameExecutor(final DirectExecutor delegate, final String newName) {
        return new ThreadNameExecutor(newName, delegate);
    }

    /**
     * Create a direct executor which adds a note to the thread name for the duration of a task.
     *
     * @param delegate the executor to delegate to
     * @param notation the note to use
     * @return the new direct executor
     */
    public static DirectExecutor threadNameNotateExecutor(final DirectExecutor delegate, final String notation) {
        return new ThreadNameNotatingExecutor(notation, delegate);
    }

    /**
     * Create a direct executor which consumes and logs errors that are thrown.
     *
     * @param delegate the executor to delegate to
     * @param log the logger to which exceptions are written at the {@code error} level
     * @return the new direct executor
     */
    public static DirectExecutor exceptionLoggingExecutor(final DirectExecutor delegate, final Logger log) {
        return new ExceptionLoggingExecutor(delegate, log);
    }

    /**
     * Create a direct executor which consumes and logs errors that are thrown to the default thread error category
     * {@code "org.jboss.threads.errors"}.
     *
     * @param delegate the executor to delegate to
     * @return the new direct executor
     */
    public static DirectExecutor exceptionLoggingExecutor(final DirectExecutor delegate) {
        return exceptionLoggingExecutor(delegate, THREAD_ERROR_LOGGER);
    }

    /**
     * Create a direct executor which delegates tasks to the given executor, and then clears <b>all</b> thread-local
     * data after each task completes (regardless of outcome).  You must have the {@link RuntimePermission}{@code ("modifyThread")}
     * permission to use this method.
     *
     * @param delegate the delegate direct executor
     * @return a resetting executor
     * @throws SecurityException if the caller does not have the {@link RuntimePermission}{@code ("modifyThread")} permission
     */
    public static DirectExecutor resettingExecutor(final DirectExecutor delegate) throws SecurityException {
        return cleanupExecutor(delegate, threadLocalResetter());
    }

    /**
     * Create an executor which runs the given initization task before running its given task.
     *
     * @param delegate the delegate direct executor
     * @param initializer the initialization task
     * @return an initializing executor
     */
    public static DirectExecutor initializingExecutor(final DirectExecutor delegate, final Runnable initializer) {
        return new InitializingExecutor(initializer, delegate);
    }

    /**
     * Create an executor which runs the given cleanup task after running its given task.
     *
     * @param delegate the delegate direct executor
     * @param cleaner the cleanup task
     * @return an initializing executor
     */
    public static DirectExecutor cleanupExecutor(final DirectExecutor delegate, final Runnable cleaner) {
        return new CleanupExecutor(cleaner, delegate);
    }

    // ==================================================
    // EXECUTORS
    // ==================================================

    /**
     * An executor which delegates to another executor, wrapping each task in a task wrapper.
     *
     * @param taskWrapper the task wrapper
     * @param delegate the delegate executor
     * @return a wrapping executor
     */
    public static Executor wrappingExecutor(final DirectExecutor taskWrapper, final Executor delegate) {
        return executor(wrappingExecutor(delegate), taskWrapper);
    }

    /**
     * Create a wrapping executor for a delegate executor which creates an {@link #executorTask(DirectExecutor, Runnable)} for
     * each task.
     *
     * @param delegate the delegate executor
     * @return the wrapping executor
     */
    public static WrappingExecutor wrappingExecutor(final Executor delegate) {
        return new DelegatingWrappingExecutor(delegate);
    }

    /**
     * An executor which delegates to a wrapping executor, wrapping each task in a task wrapper.
     *
     * @param delegate the delegate executor
     * @param taskWrapper the task wrapper
     * @return a wrapping executor
     */
    public static Executor executor(final WrappingExecutor delegate, final DirectExecutor taskWrapper) {
        return new Executor() {
            public void execute(final Runnable command) {
                delegate.execute(taskWrapper, command);
            }
        };
    }

    /**
     * Create an executor that executes each task in a new thread.
     *
     * @param factory the thread factory to use
     * @return the executor
     */
    public static Executor threadFactoryExecutor(final ThreadFactory factory) {
        return new ThreadFactoryExecutor(factory);
    }

    // ==================================================
    // REJECTED EXECUTION HANDLERS
    // ==================================================

    private static final RejectedExecutionHandler ABORT_POLICY = new ThreadPoolExecutor.AbortPolicy();
    private static final RejectedExecutionHandler CALLER_RUNS_POLICY = new ThreadPoolExecutor.CallerRunsPolicy();
    private static final RejectedExecutionHandler DISCARD_OLDEST_POLICY = new ThreadPoolExecutor.DiscardOldestPolicy();
    private static final RejectedExecutionHandler DISCARD_POLICY = new ThreadPoolExecutor.DiscardPolicy();

    /**
     * Get the abort policy for a {@link java.util.concurrent.ThreadPoolExecutor}.
     *
     * @return the abort policy
     * @see java.util.concurrent.ThreadPoolExecutor.AbortPolicy
     */
    public static RejectedExecutionHandler abortPolicy() {
        return ABORT_POLICY;
    }

    /**
     * Get the caller-runs policy for a {@link java.util.concurrent.ThreadPoolExecutor}.
     *
     * @return the caller-runs policy
     * @see java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
     */
    public static RejectedExecutionHandler callerRunsPolicy() {
        return CALLER_RUNS_POLICY;
    }

    /**
     * Get the discard-oldest policy for a {@link java.util.concurrent.ThreadPoolExecutor}.
     *
     * @return the discard-oldest policy
     * @see java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy
     */
    public static RejectedExecutionHandler discardOldestPolicy() {
        return DISCARD_OLDEST_POLICY;
    }

    /**
     * Get the discard policy for a {@link java.util.concurrent.ThreadPoolExecutor}.
     *
     * @return the discard policy
     * @see java.util.concurrent.ThreadPoolExecutor.DiscardPolicy
     */
    public static RejectedExecutionHandler discardPolicy() {
        return DISCARD_POLICY;
    }

    /**
     * Get a handoff policy for a {@link java.util.concurrent.ThreadPoolExecutor}.  The returned instance will
     * delegate to another executor in the event that the task is rejected.
     *
     * @param target the target executor
     * @return the new handoff policy implementation
     */
    public static RejectedExecutionHandler handoffPolicy(final Executor target) {
        return new HandoffRejectedExecutionHandler(target);
    }

    // ==================================================
    // PROTECTED EXECUTOR SERVICE WRAPPERS
    // ==================================================

    /**
     * Wrap an executor with an {@code ExecutorService} instance which supports all the features of {@code ExecutorService}
     * except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static ExecutorService protectedExecutorService(final Executor target) {
        return new ProtectedExecutorService(target);
    }

    /**
     * Wrap a direct executor with an {@code DirectExecutorService} instance which supports all the features of {@code ExecutorService}
     * except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static DirectExecutorService protectedDirectExecutorService(final DirectExecutor target) {
        return new ProtectedDirectExecutorService(target);
    }

    /**
     * Wrap a scheduled executor with a {@code ScheduledExecutorService} instance which supports all the features of
     * {@code ScheduledExecutorService} except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static ScheduledExecutorService protectedScheduledExecutorService(final ScheduledExecutorService target) {
        return new ProtectedScheduledExecutorService(target);
    }

    // ==================================================
    // THREAD FACTORIES
    // ==================================================

    /**
     * Create a thread factory which resets all thread-local storage and delegates to the given thread factory.
     * You must have the {@link RuntimePermission}{@code ("modifyThread")} permission to use this method.
     *
     * @param delegate the delegate thread factory
     * @return the resetting thread factory
     * @throws SecurityException if the caller does not have the {@link RuntimePermission}{@code ("modifyThread")}
     * permission
     */
    public static ThreadFactory resettingThreadFactory(final ThreadFactory delegate) throws SecurityException {
        return wrappingThreadFactory(resettingExecutor(directExecutor()), delegate);
    }

    /**
     * Creates a thread factory which executes the thread task via the given task wrapping executor.
     *
     * @param taskWrapper the task wrapping executor
     * @param delegate the delegate thread factory
     * @return the wrapping thread factory
     */
    public static ThreadFactory wrappingThreadFactory(final DirectExecutor taskWrapper, final ThreadFactory delegate) {
        return new WrappingThreadFactory(delegate, taskWrapper);
    }

    private static final Runnable TCCL_RESETTER = new Runnable() {
        public void run() {
            Thread.currentThread().setContextClassLoader(null);
        }

        public String toString() {
            return "ContextClassLoader-resetting Runnable";
        }
    };

    private static final Runnable THREAD_LOCAL_RESETTER = new ThreadLocalResetter();

    private static final class ThreadLocalResetter implements Runnable {
        private static final Field THREAD_LOCAL_MAP_FIELD;
        private static final Field INHERITABLE_THREAD_LOCAL_MAP_FIELD;

        static {
            THREAD_LOCAL_MAP_FIELD = AccessController.doPrivileged(new PrivilegedAction<Field>() {
                public Field run() {
                    final Field field;
                    try {
                        field = Thread.class.getDeclaredField("threadLocals");
                        field.setAccessible(true);
                    } catch (NoSuchFieldException e) {
                        return null;
                    }
                    return field;
                }
            });
            INHERITABLE_THREAD_LOCAL_MAP_FIELD = AccessController.doPrivileged(new PrivilegedAction<Field>() {
                public Field run() {
                    final Field field;
                    try {
                        field = Thread.class.getDeclaredField("inheritableThreadLocals");
                        field.setAccessible(true);
                    } catch (NoSuchFieldException e) {
                        return null;
                    }
                    return field;
                }
            });
        }

        private ThreadLocalResetter() {
        }

        public void run() {
            final Thread thread = Thread.currentThread();
            clear(thread, THREAD_LOCAL_MAP_FIELD);
            clear(thread, INHERITABLE_THREAD_LOCAL_MAP_FIELD);
        }

        private static void clear(final Thread currentThread, final Field field) {
            try {
                if (field != null) field.set(currentThread, null);
            } catch (IllegalAccessException e) {
                // ignore
            }
        }

        public String toString() {
            return "Thread-local resetting Runnable";
        }
    }

    // ==================================================
    // RUNNABLES
    // ==================================================

    private static final Runnable NULL_RUNNABLE = new NullRunnable();

    /**
     * Get the null runnable which does nothing.
     *
     * @return the null runnable
     */
    public static Runnable nullRunnable() {
        return NULL_RUNNABLE;
    }

    /**
     * Get a {@code Runnable} which, when executed, clears the thread-local storage of the calling thread.
     * You must have the {@link RuntimePermission}{@code ("modifyThread")}
     * permission to use this method.
     *
     * @return the runnable
     * @throws SecurityException if the caller does not have the {@link RuntimePermission}{@code ("modifyThread")}
     * permission
     */
    public static Runnable threadLocalResetter() throws SecurityException {
        checkAccess(MODIFY_THREAD_PERMISSION);
        return THREAD_LOCAL_RESETTER;
    }

    /**
     * Get a {@code Runnable} which, when executed, clears the thread context class loader (if the caller has sufficient
     * privileges).
     *
     * @return the runnable
     */
    public static Runnable contextClassLoaderResetter() {
        return TCCL_RESETTER;
    }

    /**
     * Get a task that runs the given task through the given direct executor.
     *
     * @param executor the executor to run the task through
     * @param task the task to run
     * @return an encapsulating task
     */
    public static Runnable executorTask(final DirectExecutor executor, final Runnable task) {
        return new ExecutorTask(executor, task);
    }

    /**
     * Create a task that is a composite of several other tasks.
     *
     * @param runnables the tasks
     * @return the composite task
     */
    public static Runnable compositeTask(final Runnable... runnables) {
        return new CompositeTask(runnables.clone());
    }

    /**
     * Create a task that delegates to the given task, preserving the context classloader which was in effect when
     * this method was invoked.
     *
     * @param delegate the delegate runnable
     * @return the wrapping runnable
     * @throws SecurityException if a security manager exists and the caller does not have the {@code "copyClassLoader"}
     * {@link RuntimePermission}.
     */
    public static Runnable classLoaderPreservingTask(final Runnable delegate) throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(COPY_CONTEXT_CLASSLOADER_PERMISSION);
        }
        final ClassLoader loader = getContextClassLoader(Thread.currentThread());
        return new ContextClassLoaderSavingRunnable(loader, delegate);
    }

    /**
     * Privileged method to get the context class loader of the given thread.
     *
     * @param thread the thread to introspect
     * @return the context class loader
     */
    static ClassLoader getContextClassLoader(final Thread thread) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return thread.getContextClassLoader();
                }
            });
        } else {
            return thread.getContextClassLoader();
        }
    }

    /**
     * Privileged method to get and set the context class loader of the given thread.
     *
     * @param thread the thread to introspect
     * @param newClassLoader the new context class loader
     * @return the old context class loader
     */
    static ClassLoader getAndSetContextClassLoader(final Thread thread, final ClassLoader newClassLoader) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    final ClassLoader old = thread.getContextClassLoader();
                    thread.setContextClassLoader(newClassLoader);
                    return old;
                }
            });
        } else {
            final ClassLoader old = thread.getContextClassLoader();
            thread.setContextClassLoader(newClassLoader);
            return old;
        }
    }

    /**
     * Privileged method to set the context class loader of the given thread.
     *
     * @param thread the thread to introspect
     * @param classLoader the new context class loader
     */
    static void setContextClassLoader(final Thread thread, final ClassLoader classLoader) {
        if (System.getSecurityManager() != null) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    thread.setContextClassLoader(classLoader);
                    return null;
                }
            });
        } else {
            thread.setContextClassLoader(classLoader);
        }
    }

    /**
     * Create a task that is a composite of several other tasks.
     *
     * @param runnables the tasks
     * @return the composite task
     */
    public static Runnable compositeTask(final Collection<Runnable> runnables) {
        return new CompositeTask(runnables.toArray(new Runnable[runnables.size()]));
    }

    private static void checkAccess(Permission permission) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(permission);
        }
    }

    // ==================================================
    // UNCAUGHT EXCEPTION HANDLERS
    // ==================================================

    /**
     * Get an uncaught exception handler which logs to the given logger.
     *
     * @param log the logger
     * @return the handler
     */
    public static Thread.UncaughtExceptionHandler loggingExceptionHandler(final Logger log) {
        return new LoggingUncaughtExceptionHandler(log);
    }

    private static final Thread.UncaughtExceptionHandler LOGGING_HANDLER = loggingExceptionHandler(THREAD_ERROR_LOGGER);

    /**
     * Get an uncaught exception handler which logs to the default error logger.
     *
     * @return the handler
     */
    public static Thread.UncaughtExceptionHandler loggingExceptionHandler() {
        return LOGGING_HANDLER;
    }
}
