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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Collection;
import java.security.PrivilegedAction;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.Permission;
import org.jboss.logging.Logger;

/**
 * JBoss thread- and executor-related utility and factory methods.
 */
public final class JBossExecutors {

    private static final Logger THREAD_ERROR_LOGGER = Logger.getLogger("org.jboss.threads.errors");

    private JBossExecutors() {}

    private static final RuntimePermission MODIFY_THREAD_PERMISSION = new RuntimePermission("modifyThread");
    private static final RuntimePermission COPY_CONTEXT_CLASSLOADER_PERMISSION = new RuntimePermission("copyClassLoader");

    private static final DirectExecutorService DIRECT_EXECUTOR_SERVICE = new DelegatingDirectExecutorService(SimpleDirectExecutor.INSTANCE);
    private static final DirectExecutorService REJECTING_EXECUTOR_SERVICE = new DelegatingDirectExecutorService(RejectingExecutor.INSTANCE);
    private static final DirectExecutorService DISCARDING_EXECUTOR_SERVICE = new DelegatingDirectExecutorService(DiscardingExecutor.INSTANCE);

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
     * Get a rejecting executor.  This executor will reject any task submitted to it with the given message.
     *
     * @param message the reject message
     * @return the rejecting executor instance
     */
    public static DirectExecutor rejectingExecutor(final String message) {
        return new RejectingExecutor(message);
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
     * Get the rejecting executor service.  This executor will reject any task submitted to it with the given message.
     * It cannot be shut down.
     *
     * @param message the reject message
     * @return the rejecting executor service instance
     */
    public static DirectExecutorService rejectingExecutorService(final String message) {
        return protectedDirectExecutorService(rejectingExecutor(message));
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
     * Create a direct executor which runs with the privileges given by the supplied class' protection domain.
     *
     * @param delegate the executor to delegate to at the privileged level
     * @param targetClass the target class whose protection domain should be used
     * @return the new direct executor
     * @throws SecurityException if there is a security manager installed and the caller lacks the {@code "getProtectionDomain"} {@link RuntimePermission}
     */
    public static DirectExecutor privilegedExecutor(final DirectExecutor delegate, final Class<?> targetClass) throws SecurityException {
        return new PrivilegedExecutor(delegate, targetClass);
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
     * Create a direct executor which changes the thread name for the duration of a task using a formatted name.
     * The thread must be a {@link JBossThread}.
     *
     * @param delegate the executor to delegate to
     * @param newName the thread name to use
     * @return the new direct executor
     */
    public static DirectExecutor threadFormattedNameExecutor(final DirectExecutor delegate, final String newName) {
        return new ThreadFormattedNameExecutor(newName, delegate);
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
     * Create a direct executor which consumes and logs errors that are thrown.
     *
     * @param delegate the executor to delegate to
     * @param log the logger to which exceptions are written
     * @param level the level at which to log exceptions
     * @return the new direct executor
     */
    public static DirectExecutor exceptionLoggingExecutor(final DirectExecutor delegate, final Logger log, final Logger.Level level) {
        return new ExceptionLoggingExecutor(delegate, log, level);
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
     * Create an executor which runs the given initialization task before running its given task.
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
        return new DelegatingWrappedExecutor(delegate, taskWrapper);
    }

    /**
     * Create an executor that executes each task in a new thread.
     *
     * @param factory the thread factory to use
     * @return the executor
     */
    public static BlockingExecutor threadFactoryExecutor(final ThreadFactory factory) {
        return new ThreadFactoryExecutor(factory, Integer.MAX_VALUE, false, directExecutor());
    }

    /**
     * Create an executor that executes each task in a new thread.  By default up to the given number of threads may run
     * concurrently, after which new tasks will be rejected.
     *
     * @param factory the thread factory to use
     * @param maxThreads the maximum number of allowed threads
     * @return the executor
     */
    public static BlockingExecutor threadFactoryExecutor(final ThreadFactory factory, final int maxThreads) {
        return new ThreadFactoryExecutor(factory, maxThreads, false, directExecutor());
    }

    /**
     * Create an executor that executes each task in a new thread.  By default up to the given number of threads may run
     * concurrently, after which the caller will block or new tasks will be rejected, according to the setting of the
     * {@code blocking} parameter.
     *
     * @param factory the thread factory to use
     * @param maxThreads the maximum number of allowed threads
     * @param blocking {@code true} if the submitter should block when the maximum number of threads has been reached
     * @return the executor
     */
    public static BlockingExecutor threadFactoryExecutor(final ThreadFactory factory, final int maxThreads, final boolean blocking) {
        return new ThreadFactoryExecutor(factory, maxThreads, blocking, directExecutor());
    }

    /**
     * Create an executor that executes each task in a new thread.  By default up to the given number of threads may run
     * concurrently, after which the caller will block or new tasks will be rejected, according to the setting of the
     * {@code blocking} parameter.
     *
     * @param factory the thread factory to use
     * @param maxThreads the maximum number of allowed threads
     * @param blocking {@code true} if the submitter should block when the maximum number of threads has been reached
     * @param taskExecutor the executor which should run each task
     * @return the executor
     */
    public static BlockingExecutor threadFactoryExecutor(final ThreadFactory factory, final int maxThreads, final boolean blocking, final DirectExecutor taskExecutor) {
        return new ThreadFactoryExecutor(factory, maxThreads, blocking, taskExecutor);
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

    public static BlockingExecutor protectedBlockingExecutor(final BlockingExecutor target) {
        return new DelegatingBlockingExecutor(target);
    }

    /**
     * Wrap an executor with an {@code ExecutorService} instance which supports all the features of {@code ExecutorService}
     * except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static ExecutorService protectedExecutorService(final Executor target) {
        return new DelegatingExecutorService(target);
    }

    /**
     * Wrap a direct executor with an {@code DirectExecutorService} instance which supports all the features of {@code ExecutorService}
     * except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static DirectExecutorService protectedDirectExecutorService(final DirectExecutor target) {
        return new DelegatingDirectExecutorService(target);
    }

    /**
     * Wrap a scheduled executor with a {@code ScheduledExecutorService} instance which supports all the features of
     * {@code ScheduledExecutorService} except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static ScheduledExecutorService protectedScheduledExecutorService(final ScheduledExecutorService target) {
        return new DelegatingScheduledExecutorService(target);
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

    // ==================================================
    // RUNNABLES
    // ==================================================

    private static final Runnable NULL_RUNNABLE = new NullRunnable();
    private static final Runnable THREAD_LOCAL_RESETTER = new ThreadLocalResetter();

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

    static void checkAccess(Permission permission) {
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

    /**
     * Get an uncaught exception handler which logs to the given logger.
     *
     * @param categoryName the name of the logger category to log to
     * @return the handler
     */
    public static Thread.UncaughtExceptionHandler loggingExceptionHandler(final String categoryName) {
        return new LoggingUncaughtExceptionHandler(Logger.getLogger(categoryName));
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

    private static <R extends Runnable> void logError(final R runnable, final Throwable t, final String method) {
        THREAD_ERROR_LOGGER.errorf(t, "Notifier %s() method invocation failed for task %s", method, runnable);
    }

    private static <R extends Runnable, A> void started(TaskNotifier<? super R, ? super A> notifier, R runnable, A attachment) {
        try {
            notifier.started(runnable, attachment);
        } catch (Throwable t) {
            logError(runnable, t, "started");
        }
    }

    private static <R extends Runnable, A> void finished(TaskNotifier<? super R, ? super A> notifier, R runnable, A attachment) {
        try {
            notifier.finished(runnable, attachment);
        } catch (Throwable t) {
            logError(runnable, t, "finished");
        }
    }

    private static <R extends Runnable, A> void failed(TaskNotifier<? super R, ? super A> notifier, Throwable reason, R runnable, A attachment) {
        try {
            notifier.failed(runnable, reason, attachment);
        } catch (Throwable t) {
            logError(runnable, t, "failed");
        }
    }

    /**
     * Run a task through the given direct executor, invoking the given notifier with the given attachment.
     *
     * @param task the task
     * @param directExecutor the executor
     * @param notifier the notifier
     * @param attachment the attachment
     * @param <R> the task type
     * @param <A> the attachment type
     */
    public static <R extends Runnable, A> void run(R task, DirectExecutor directExecutor, TaskNotifier<? super R, ? super A> notifier, A attachment) {
        started(notifier, task, attachment);
        boolean ok = false;
        try {
            directExecutor.execute(task);
            ok = true;
        } catch (RuntimeException t) {
            failed(notifier, t, task, attachment);
            throw t;
        } catch (Error t) {
            failed(notifier, t, task, attachment);
            throw t;
        } catch (Throwable t) {
            failed(notifier, t, task, attachment);
            throw new RuntimeException("Unknown throwable received", t);
        } finally {
            if (ok) finished(notifier, task, attachment);
        }
    }

    /**
     * Run a task, invoking the given notifier with the given attachment.
     *
     * @param task the task
     * @param notifier the notifier
     * @param attachment the attachment
     * @param <R> the task type
     * @param <A> the attachment type
     */
    public static <R extends Runnable, A> void run(R task, TaskNotifier<? super R, ? super A> notifier, A attachment) {
        run(task, directExecutor(), notifier, attachment);
    }

    /**
     * Get a notifying runnable wrapper for a task.  The notifier will be invoked when the task is run.
     *
     * @param task the task
     * @param notifier the notifier
     * @param attachment the attachment
     * @param <R> the task type
     * @param <A> the attachment type
     * @return the wrapping runnable
     */
    public static <R extends Runnable, A> Runnable notifyingRunnable(R task, TaskNotifier<? super R, ? super A> notifier, A attachment) {
        return new NotifyingRunnable<R, A>(task, notifier, attachment);
    }

    /**
     * Get a notifying direct executor.  The notifier will be invoked when each task is run.
     *
     * @param delegate the executor which will actually run the task
     * @param notifier the notifier
     * @param attachment the attachment
     * @param <A> the attachment type
     * @return the direct executor
     */
    public static <A> DirectExecutor notifyingDirectExecutor(DirectExecutor delegate, TaskNotifier<Runnable, ? super A> notifier, A attachment) {
        return new NotifyingDirectExecutor<A>(delegate, notifier, attachment);
    }

    /**
     * Execute a task uninterruptibly.
     *
     * @param executor the executor to delegate to
     * @param task the task to execute
     * @throws RejectedExecutionException if the task was rejected by the executor
     */
    public static void executeUninterruptibly(Executor executor, Runnable task) throws RejectedExecutionException {
        boolean intr = Thread.interrupted();
        try {
            for (;;) {
                try {
                    executor.execute(task);
                    return;
                } catch (ExecutionInterruptedException e) {
                    intr |= Thread.interrupted();
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Get an executor which executes tasks uninterruptibly in the event of blocking.
     *
     * @param delegate the delegate executor
     * @return the uninterruptible executor
     */
    public static Executor uninterruptibleExecutor(final Executor delegate) {
        if (delegate instanceof UninterruptibleExecutor) {
            return delegate;
        } else {
            return new UninterruptibleExecutor(delegate);
        }
    }

    /**
     * Create a builder for a dependent task.
     *
     * @param executor the executor to use
     * @param task the task to run when all dependencies are met
     * @return the builder
     */
    public static DependencyTaskBuilder dependencyTaskBuilder(final Executor executor, final Runnable task) {
        return new DependencyTaskBuilder(executor, task);
    }
}
