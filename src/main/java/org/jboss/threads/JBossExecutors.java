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
import java.security.PrivilegedAction;
import java.security.AccessController;
import java.security.AccessControlContext;

/**
 *
 */
public final class JBossExecutors {

    private JBossExecutors() {}

    private static final DirectExecutor DIRECT_EXECUTOR = new DirectExecutor() {
        public void execute(final Runnable command) {
            command.run();
        }
    };

    private static final DirectExecutor REJECTING_EXECUTOR = new DirectExecutor() {
        public void execute(final Runnable command) {
            throw new RejectedExecutionException();
        }
    };

    private static final DirectExecutor DISCARDING_EXECUTOR = new DirectExecutor() {
        public void execute(final Runnable command) {
            // nothing
        }
    };

    private static final DirectExecutorService DIRECT_EXECUTOR_SERVICE = new ProtectedDirectExecutorService(DIRECT_EXECUTOR);
    private static final DirectExecutorService REJECTING_EXECUTOR_SERVICE = new ProtectedDirectExecutorService(REJECTING_EXECUTOR);
    private static final DirectExecutorService DISCARDING_EXECUTOR_SERVICE = new ProtectedDirectExecutorService(DISCARDING_EXECUTOR);

    /**
     * Get the direct executor.  This executor will immediately run any task it is given, and propagate back any
     * run-time exceptions thrown.
     *
     * @return the direct executor instance
     */
    public static DirectExecutor directExecutor() {
        return DIRECT_EXECUTOR;
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
        return REJECTING_EXECUTOR;
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
        return DISCARDING_EXECUTOR;
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
     * Get a task that runs the given task through the given direct executor.
     *
     * @param executor the executor to run the task through
     * @param task the task to run
     * @return an encapsulating task
     */
    public static Runnable executorTask(final DirectExecutor executor, final Runnable task) {
        return new Runnable() {
            public void run() {
                executor.execute(task);
            }
        };
    }

    /**
     * An executor which delegates to another executor, wrapping each task in a task wrapper.
     *
     * @param delegate the delegate executor
     * @param taskWrapper the task wrapper
     * @return a wrapping executor
     */
    public static Executor wrappingExecutor(final Executor delegate, final DirectExecutor taskWrapper) {
        return new Executor() {
            public void execute(final Runnable command) {
                delegate.execute(executorTask(taskWrapper, command));
            }
        };
    }

    /**
     * Create a direct executor which runs with the privileges given by the supplied {@code AccessControlContext} instance.
     *
     * @param delegate the executor to delegate to at the privileged level
     * @param context the {@code AccessControlContext} to use
     * @return the new direct executor
     */
    public static DirectExecutor privilegedExecutor(final DirectExecutor delegate, final AccessControlContext context) {
        return new DirectExecutor() {
            public void execute(final Runnable command) {
                final SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    AccessController.doPrivileged(new PrivilegedAction<Void>() {
                        public Void run() {
                            delegate.execute(command);
                            return null;
                        }
                    }, context);
                } else {
                    delegate.execute(command);
                }
            }
        };
    }

    /**
     * Create a direct executor which runs tasks with the given context class loader.
     *
     * @param delegate the executor to delegate to
     * @param taskClassLoader the context class loader to use
     * @return the new direct executor
     */
    public static DirectExecutor contextClassLoaderExecutor(final DirectExecutor delegate, final ClassLoader taskClassLoader) {
        return new DirectExecutor() {
            public void execute(final Runnable command) {
                final Thread thr = Thread.currentThread();
                ClassLoader old = thr.getContextClassLoader();
                thr.setContextClassLoader(taskClassLoader);
                try {
                    delegate.execute(command);
                } finally {
                    thr.setContextClassLoader(old);
                }
            }
        };
    }

    /**
     * Create a direct executor which changes the thread name for the duration of a task.
     *
     * @param delegate the executor to delegate to
     * @param newName the thread name to use
     * @return the new direct executor
     */
    public static DirectExecutor threadNameExecutor(final DirectExecutor delegate, final String newName) {
        return new DirectExecutor() {
            public void execute(final Runnable command) {
                final Thread thr = Thread.currentThread();
                final String oldName = thr.getName();
                thr.setName(newName);
                try {
                    delegate.execute(command);
                } finally {
                    thr.setName(oldName);
                }
            }
        };
    }

    /**
     * Create a direct executor which adds a note to the thread name for the duration of a task.
     *
     * @param delegate the executor to delegate to
     * @param notation the note to use
     * @return the new direct executor
     */
    public static DirectExecutor threadNameNotateExecutor(final DirectExecutor delegate, final String notation) {
        return new DirectExecutor() {
            public void execute(final Runnable command) {
                final Thread thr = Thread.currentThread();
                final String oldName;
                oldName = thr.getName();
                thr.setName(oldName + " (" + notation + ')');
                try {
                    delegate.execute(command);
                } finally {
                    thr.setName(oldName);
                }
            }
        };
    }

    /**
     * Create a direct executor which consumes and logs errors that are thrown.
     *
     * @param delegate the executor to delegate to
     * @param log the logger to which exceptions are written at the {@code error} level
     * @return the new direct executor
     */
    public static DirectExecutor exceptionLoggingExecutor(final DirectExecutor delegate, final Object log) {
        return new DirectExecutor() {
            public void execute(final Runnable command) {
                try {
                    delegate.execute(command);
                } catch (Throwable t) {
                    // todo log it
                    // log.error(t, "Exception thrown from thread task");
                }
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
        return new Executor() {
            public void execute(final Runnable command) {
                factory.newThread(command).start();
            }
        };
    }

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
        return new RejectedExecutionHandler() {
            public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
                target.execute(r);
            }
        };
    }

    /**
     * Wrap an executor with an {@code ExecutorService} instance, which supports all the features of {@code ExecutorService}
     * except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static ExecutorService protectedExecutorService(final Executor target) {
        return new ProtectedExecutorService(target);
    }

    /**
     * Wrap a direct executor with an {@code DirectExecutorService} instance, which supports all the features of {@code ExecutorService}
     * except for shutting down the executor.
     *
     * @param target the target executor
     * @return the executor service
     */
    public static DirectExecutorService protectedDirectExecutorService(final DirectExecutor target) {
        return new ProtectedDirectExecutorService(target);
    }
}
