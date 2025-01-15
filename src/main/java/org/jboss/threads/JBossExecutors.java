package org.jboss.threads;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.security.PrivilegedAction;
import java.security.AccessController;

import org.jboss.logging.Logger;
import io.smallrye.common.constraint.Assert;
import sun.misc.Unsafe;

/**
 * JBoss thread- and executor-related utility and factory methods.
 */
@SuppressWarnings("deprecation")
public final class JBossExecutors {

    private static final Logger THREAD_ERROR_LOGGER = Logger.getLogger("org.jboss.threads.errors");

    private JBossExecutors() {}

    private static final RuntimePermission COPY_CONTEXT_CLASSLOADER_PERMISSION = new RuntimePermission("copyClassLoader");

    private static final ExecutorService REJECTING_EXECUTOR_SERVICE = new DelegatingExecutorService(RejectingExecutor.INSTANCE);
    private static final ExecutorService DISCARDING_EXECUTOR_SERVICE = new DelegatingExecutorService(DiscardingExecutor.INSTANCE);

    // ==================================================
    // DIRECT EXECUTORS
    // ==================================================

    /**
     * Get the direct executor.  This executor will immediately run any task it is given, and propagate back any
     * run-time exceptions thrown.
     *
     * @return the direct executor instance
     */
    public static Executor directExecutor() {
        return SimpleDirectExecutor.INSTANCE;
    }

    /**
     * Get the rejecting executor.  This executor will reject any task submitted to it.
     *
     * @return the rejecting executor instance
     */
    public static Executor rejectingExecutor() {
        return RejectingExecutor.INSTANCE;
    }

    /**
     * Get a rejecting executor.  This executor will reject any task submitted to it with the given message.
     *
     * @param message the reject message
     * @return the rejecting executor instance
     */
    public static Executor rejectingExecutor(final String message) {
        return new RejectingExecutor(message);
    }

    /**
     * Get the rejecting executor service.  This executor will reject any task submitted to it.  It cannot be shut down.
     *
     * @return the rejecting executor service instance
     */
    public static ExecutorService rejectingExecutorService() {
        return REJECTING_EXECUTOR_SERVICE;
    }

    /**
     * Get the rejecting executor service.  This executor will reject any task submitted to it with the given message.
     * It cannot be shut down.
     *
     * @param message the reject message
     * @return the rejecting executor service instance
     */
    public static ExecutorService rejectingExecutorService(final String message) {
        return protectedExecutorService(rejectingExecutor(message));
    }

    /**
     * Get the discarding executor.  This executor will silently discard any task submitted to it.
     *
     * @return the discarding executor instance
     */
    public static Executor discardingExecutor() {
        return DiscardingExecutor.INSTANCE;
    }

    /**
     * Get the discarding executor service.  This executor will silently discard any task submitted to it.  It cannot
     * be shut down.
     *
     * @return the discarding executor service instance
     */
    public static ExecutorService discardingExecutorService() {
        return DISCARDING_EXECUTOR_SERVICE;
    }

    /**
     * Create an executor which runs tasks with the given context class loader.
     *
     * @param delegate the executor to delegate to
     * @param taskClassLoader the context class loader to use
     * @return the new direct executor
     */
    public static Executor contextClassLoaderExecutor(final Executor delegate, final ClassLoader taskClassLoader) {
        return new DelegatingExecutor(delegate) {
            public void execute(final Runnable command) {
                super.execute(new ContextClassLoaderSavingRunnable(taskClassLoader, command));
            }
        };
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
        return new DelegatingExecutorService(target);
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
        return new ThreadFactory() {
            public Thread newThread(final Runnable r) {
                return delegate.newThread(new ThreadLocalResettingRunnable(r));
            }
        };
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

    private static final Runnable NULL_RUNNABLE = NullRunnable.getInstance();

    /**
     * Get the null runnable which does nothing.
     *
     * @return the null runnable
     */
    public static Runnable nullRunnable() {
        return NULL_RUNNABLE;
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
        return classLoaderPreservingTaskUnchecked(delegate);
    }

    static final ClassLoader SAFE_CL;

    static {
        ClassLoader safeClassLoader = JBossExecutors.class.getClassLoader();
        if (safeClassLoader == null) {
            safeClassLoader = ClassLoader.getSystemClassLoader();
        }
        if (safeClassLoader == null) {
            safeClassLoader = new ClassLoader() {
            };
        }
        SAFE_CL = safeClassLoader;
    }

    static Runnable classLoaderPreservingTaskUnchecked(final Runnable delegate) {
        Assert.checkNotNullParam("delegate", delegate);
        return new ContextClassLoaderSavingRunnable(getContextClassLoader(Thread.currentThread()), delegate);
    }

    static final Unsafe unsafe;

    static final long contextClassLoaderOffs;

    static {
        unsafe = AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
            public Unsafe run() {
                try {
                    final var lookup = MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup());
                    final var theUnsafeVH = lookup.findStaticVarHandle(Unsafe.class, "theUnsafe", Unsafe.class);
                    return (Unsafe) theUnsafeVH.get();
                } catch (IllegalAccessException e) {
                    throw new IllegalAccessError(e.getMessage());
                } catch (NoSuchFieldException e) {
                    throw new NoSuchFieldError(e.getMessage());
                }
            }
        });
        try {
            contextClassLoaderOffs = unsafe.objectFieldOffset(Thread.class.getDeclaredField("contextClassLoader"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    /**
     * Privileged method to get the context class loader of the given thread.
     *
     * @param thread the thread to introspect
     * @return the context class loader
     */
    static ClassLoader getContextClassLoader(final Thread thread) {
        return (ClassLoader) unsafe.getObject(thread, contextClassLoaderOffs);
    }

    /**
     * Privileged method to get and set the context class loader of the given thread.
     *
     * @param thread the thread to introspect
     * @param newClassLoader the new context class loader
     * @return the old context class loader
     */
    static ClassLoader getAndSetContextClassLoader(final Thread thread, final ClassLoader newClassLoader) {
        final ClassLoader currentClassLoader = (ClassLoader) unsafe.getObject(thread, contextClassLoaderOffs);
        if (currentClassLoader != newClassLoader) {
            // not using setContextClassLoader to save loading the current one again
            unsafe.putObject(thread, contextClassLoaderOffs, newClassLoader);
        }
        return currentClassLoader;
    }

    /**
     * Privileged method to set the context class loader of the given thread.
     *
     * @param thread the thread to introspect
     * @param classLoader the new context class loader
     */
    static void setContextClassLoader(final Thread thread, final ClassLoader classLoader) {
        if (unsafe.getObject(thread, contextClassLoaderOffs) != classLoader) {
            unsafe.putObject(thread, contextClassLoaderOffs, classLoader);
        }
    }

    /**
     * Privileged method to clear the context class loader of the given thread to a safe non-{@code null} value.
     *
     * @param thread the thread to introspect
     */
    static void clearContextClassLoader(final Thread thread) {
        unsafe.putObject(thread, contextClassLoaderOffs, SAFE_CL);
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
}
