/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

/**
 * A queueless thread pool.  If one or more threads are waiting for work when a task is submitted, it will be used.
 * Otherwise, if fewer than the maximum threads are started, a new thread is created.
 */
public final class ThreadPool {

    private final ThreadFactory threadFactory;

    private volatile long idleTimeout;
    private int maxThreads;
    private int runningThreads;

    private Task parkedTask;
    private final Lock poolLock = new ReentrantLock();
    // signal when parking a task
    private final Condition park = poolLock.newCondition();
    // signal when unparking a task
    private final Condition unpark = poolLock.newCondition();

    private State state = State.RUNNING;

    private enum State {
        RUNNING,
        KILL,
    }

    public ThreadPool(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        idleTimeout = 30000L;
        maxThreads = 20;
        runningThreads = 0;
    }

    public void executeBlocking(final Runnable runnable, final DirectExecutor taskExecutor) throws InterruptedException {
        if (runnable == null) {
            throw new NullPointerException("runnable is null");
        }
        final Lock poolLock = this.poolLock;
        final Condition park = this.park;
        final Condition unpark = this.unpark;
        Task task;
        poolLock.lockInterruptibly();
        try {
            for (;;) {
                if (state == State.KILL) {
                    throw new RejectedExecutionException("Thread pool is shut down");
                } else if ((task = parkedTask) != null) {
                    parkedTask = null;
                    unpark.signal();
                    break;
                } else {
                    final int runningThreads = this.runningThreads;
                    if (runningThreads < maxThreads) {
                        task = new Task(runnable, taskExecutor);
                        final ThreadFactory threadFactory = this.threadFactory;
                        final Thread newThread = threadFactory.newThread(task);
                        if (newThread == null) {
                            throw new RejectedExecutionException("Thread factory " + threadFactory + " will not create a thread");
                        }
                        newThread.start();
                        this.runningThreads = runningThreads + 1;
                        return;
                    } else {
                        park.await();
                    }
                }
            }
        } finally {
            poolLock.unlock();
        }
        synchronized (task) {
            task.runnable = runnable;
            task.taskExecutor = taskExecutor;
            task.notify();
        }
    }

    private final class Task implements Runnable {

        /**
         * Protected by {@code this}.
         */
        private Runnable runnable;
        private DirectExecutor taskExecutor;

        Task(final Runnable runnable, final DirectExecutor taskExecutor) {
            synchronized (this) {
                this.runnable = runnable;
                this.taskExecutor = taskExecutor;
            }
        }

        public void run() {
            final Lock poolLock = ThreadPool.this.poolLock;
            Runnable runnable;
            DirectExecutor taskExecutor;
            try {
                synchronized (this) {
                    runnable = this.runnable;
                    taskExecutor = this.taskExecutor;
                    this.runnable = null;
                    this.taskExecutor = null;
                }
                long idleSince = runnable == null ? System.currentTimeMillis() : Long.MAX_VALUE;
                for (;;) {
                    while (runnable == null) {
                        // no task; park
                        poolLock.lock();
                        try {
                            if (state == State.KILL) {
                                return;
                            }
                            // wait for the spot to open
                            Task task;
                            while ((task = parkedTask) != null && task != this) {
                                try {
                                    final long remaining = idleTimeout - (System.currentTimeMillis() - idleSince);
                                    if (remaining < 0L) {
                                        // idle timeout; don't bother finishing parking
                                        return;
                                    }
                                    unpark.await(remaining, TimeUnit.MILLISECONDS);
                                } catch (InterruptedException e) {
                                    if (state == State.KILL) {
                                        return;
                                    }
                                }
                            }
                            parkedTask = this;
                        } finally {
                            poolLock.unlock();
                        }
                        // parked!  Now just wait for a task to show up
                        synchronized (this) {
                            while ((runnable = this.runnable) == null) {
                                try {
                                    final long remaining = idleTimeout - (System.currentTimeMillis() - idleSince);
                                    if (remaining < 0L) {
                                        // idle timeout; unpark and return
                                        return;
                                    }
                                    wait(remaining);
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                            taskExecutor = this.taskExecutor;
                            this.runnable = null;
                            this.taskExecutor = null;
                        }
                    }
                    (taskExecutor == null ? JBossExecutors.directExecutor() : taskExecutor).execute(runnable);
                    synchronized (this) {
                        runnable = this.runnable;
                        taskExecutor = this.taskExecutor;
                        this.runnable = null;
                        this.taskExecutor = null;
                    }
                    if (runnable == null) idleSince = System.currentTimeMillis();
                }
            } finally {
                poolLock.lock();
                try {
                    // If this task is parked, unpark it so someone else can get in there
                    if (parkedTask == this) {
                        parkedTask = null;
                        unpark.signal();
                    }
                    runningThreads--;
                } finally {
                    poolLock.unlock();
                }
            }
        }
    }
}
