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

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.logging.Logger;
import org.jboss.threads.management.BoundedThreadPoolExecutorMBean;

/**
 * A queueless thread pool.  If one or more threads are waiting for work when a task is submitted, it will be used.
 * Otherwise, if fewer than the maximum threads are started, a new thread is created.
 */
public final class QueuelessExecutor extends AbstractExecutorService implements BlockingExecutorService, BoundedThreadPoolExecutorMBean, ShutdownListenable {

    private static final Logger log = Logger.getLogger("org.jboss.threads.executor");

    private final SimpleShutdownListenable shutdownListenable = new SimpleShutdownListenable();
    private final ThreadFactory threadFactory;
    private final DirectExecutor taskExecutor;

    private final Lock lock = new ReentrantLock(false);
    private final Condition runnableDequeued = lock.newCondition();
    private final Condition nextReady = lock.newCondition();
    private final Condition workerDequeued = lock.newCondition();
    private final Condition taskEnqueued = lock.newCondition();
    private final Condition threadDeath = lock.newCondition();

    /**
     * Protected by {@link #lock}
     */
    private final Set<Thread> runningThreads = new HashSet<Thread>(256);

    /**
     * Protected by {@link #lock}, signal {@link #runnableDequeued} on clear
     */
    private Runnable workRunnable;

    /**
     * Protected by {@link #lock}, signal {@link #workerDequeued} on clear
     */
    private Worker waitingWorker;

    /**
     * Configuration value.
     * Protected by {@link #lock}
     */
    private long keepAliveTime;

    /**
     * Configuration value.
     * Protected by {@link #lock}
     */
    private int maxThreads;

    /**
     * Specify whether this executor blocks when no threads are available.
     * Protected by {@link #lock}
     */
    private boolean blocking;

    private Executor handoffExecutor;

    private boolean stop;

    //-- statistics --
    private int largestPoolSize;
    private int rejectedCount;

    public QueuelessExecutor(final ThreadFactory threadFactory, final DirectExecutor taskExecutor, final Executor handoffExecutor, final long keepAliveTime) {
        this.threadFactory = threadFactory;
        this.taskExecutor = taskExecutor;
        this.handoffExecutor = handoffExecutor;
        this.keepAliveTime = keepAliveTime;
    }

    public int getMaxThreads() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return maxThreads;
        } finally {
            lock.unlock();
        }
    }

    public void setMaxThreads(final int newSize) {
        if (newSize < 1) {
            throw new IllegalArgumentException("Pool size must be at least 1");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            maxThreads = newSize;
        } finally {
            lock.unlock();
        }
    }

    public long getKeepAliveTime() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return keepAliveTime;
        } finally {
            lock.unlock();
        }
    }

    public void setKeepAliveTime(final long milliseconds) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            keepAliveTime = milliseconds;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getNumberOfFreeThreads() {
        return getMaxThreads() - getCurrentThreadCount();
    }

    public int getCurrentThreadCount() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return runningThreads.size();
        } finally {
            lock.unlock();
        }
    }

    public int getLargestThreadCount() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return largestPoolSize;
        } finally {
            lock.unlock();
        }
    }

    public int getRejectedCount() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return rejectedCount;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public int getQueueSize() {
        return 0;
    }

    public boolean isBlocking() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return blocking;
        } finally {
            lock.unlock();
        }
    }

    public void setBlocking(final boolean blocking) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.blocking = blocking;
        } finally {
            lock.unlock();
        }
    }

    public Executor getHandoffExecutor() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    public void setHandoffExecutor(final Executor handoffExecutor) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.handoffExecutor = handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        boolean callShutdownListener = false;
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (! stop) {
                if (runningThreads.isEmpty()) {
                    callShutdownListener = true;
                } else {
                    for (Thread runningThread : runningThreads) {
                        runningThread.interrupt();
                    }
                }
            }
            stop = true;
            // wake up all waiters
            runnableDequeued.signalAll();
            nextReady.signalAll();
            workerDequeued.signalAll();
            taskEnqueued.signalAll();
        } finally {
            lock.unlock();
            if (callShutdownListener)
                shutdownListenable.shutdown();
        }
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (! stop) {
                throw new IllegalStateException("Not shut down");
            }
            final Date deadline = new Date(clipHigh(unit.toMillis(timeout) + System.currentTimeMillis()));
            final Condition threadDeath = this.threadDeath;
            while (! runningThreads.isEmpty() && threadDeath.awaitUntil(deadline));
            return runningThreads.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public List<Runnable> shutdownNow() {
        shutdown();
        // tasks are never queued
        return Collections.emptyList();
    }

    public boolean isShutdown() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return stop;
        } finally {
            lock.unlock();
        }
    }

    public boolean isTerminated() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return stop && runningThreads.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void execute(final Runnable task) {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        final Executor executor;
        final Set<Thread> runningThreads = this.runningThreads;
        final Condition runnableDequeued = this.runnableDequeued;
        final Lock lock = this.lock;
        Runnable workRunnable;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor has been shut down");
                }
                final Worker waitingWorker;
                if ((waitingWorker = this.waitingWorker) != null) {
                    // a worker thread was waiting for a task; give it the task and wake it up
                    waitingWorker.setRunnable(task);
                    taskEnqueued.signal();
                    this.waitingWorker = null;
                    return;
                }
                // no worker thread was waiting
                final int currentSize = runningThreads.size();
                if (currentSize < maxThreads) {
                    // if we haven't reached the thread limit yet, start up another thread
                    final Thread thread = threadFactory.newThread(new Worker(task));
                    if (thread == null) {
                        throw new ThreadCreationException();
                    }
                    if (! runningThreads.add(thread)) {
                        throw new ThreadCreationException("Unable to add new thread to the running set");
                    }
                    if (currentSize >= largestPoolSize) {
                        largestPoolSize = currentSize + 1;
                    }
                    thread.start();
                    return;
                }
                if (! blocking) {
                    // not blocking, not accepted
                    executor = handoffExecutor;
                    rejectedCount++;
                    // fall out to execute outside of lock
                    break;
                }
                workRunnable = this.workRunnable;
                if (workRunnable != null) {
                    // someone else is waiting for a worker, so we wait for them
                    try {
                        nextReady.await();
                        continue;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ExecutionInterruptedException();
                    }
                }
                this.workRunnable = task;
                try {
                    runnableDequeued.await();
                    if (this.workRunnable == null) {
                        // task was accepted
                        nextReady.signal();
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ExecutionInterruptedException();
                } finally {
                    this.workRunnable = null;
                }
            }
        } finally {
            lock.unlock();
        }
        if (executor != null) {
            executor.execute(task);
        } else {
            throw new RejectedExecutionException();
        }
    }

    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        final Set<Thread> runningThreads = this.runningThreads;
        final Condition runnableDequeued = this.runnableDequeued;
        final Lock lock = this.lock;
        Runnable workRunnable;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor has been shut down");
                }
                final Worker waitingWorker;
                if ((waitingWorker = this.waitingWorker) != null) {
                    // a worker thread was waiting for a task; give it the task and wake it up
                    waitingWorker.setRunnable(task);
                    taskEnqueued.signal();
                    this.waitingWorker = null;
                    return;
                }
                // no worker thread was waiting
                final int currentSize = runningThreads.size();
                if (currentSize < maxThreads) {
                    // if we haven't reached the thread limit yet, start up another thread
                    final Thread thread = threadFactory.newThread(new Worker(task));
                    if (thread == null) {
                        throw new ThreadCreationException();
                    }
                    if (! runningThreads.add(thread)) {
                        throw new ThreadCreationException("Unable to add new thread to the running set");
                    }
                    if (currentSize >= largestPoolSize) {
                        largestPoolSize = currentSize + 1;
                    }
                    thread.start();
                    return;
                }
                workRunnable = this.workRunnable;
                if (workRunnable != null) {
                    // someone else is waiting for a worker, so we wait for them
                    nextReady.await();
                    continue;
                }
                this.workRunnable = task;
                try {
                    runnableDequeued.await();
                    if (this.workRunnable == null) {
                        // task was accepted
                        nextReady.signal();
                        return;
                    }
                } finally {
                    this.workRunnable = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void executeBlocking(final Runnable task, final long timeout, final TimeUnit unit) throws RejectedExecutionException, InterruptedException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        long now = System.currentTimeMillis();
        final long deadline = now + unit.toMillis(timeout);
        if (deadline < 0L) {
            executeBlocking(task);
            return;
        }
        final Set<Thread> runningThreads = this.runningThreads;
        final Condition runnableDequeued = this.runnableDequeued;
        final Lock lock = this.lock;
        Runnable workRunnable;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor has been shut down");
                }
                final Worker waitingWorker;
                if ((waitingWorker = this.waitingWorker) != null) {
                    // a worker thread was waiting for a task; give it the task and wake it up
                    waitingWorker.setRunnable(task);
                    taskEnqueued.signal();
                    this.waitingWorker = null;
                    return;
                }
                // no worker thread was waiting
                final int currentSize = runningThreads.size();
                if (currentSize < maxThreads) {
                    // if we haven't reached the thread limit yet, start up another thread
                    final Thread thread = threadFactory.newThread(new Worker(task));
                    if (thread == null) {
                        throw new ThreadCreationException();
                    }
                    if (! runningThreads.add(thread)) {
                        throw new ThreadCreationException("Unable to add new thread to the running set");
                    }
                    if (currentSize >= largestPoolSize) {
                        largestPoolSize = currentSize + 1;
                    }
                    thread.start();
                    return;
                }
                workRunnable = this.workRunnable;
                if (workRunnable != null) {
                    // someone else is waiting for a worker, so we wait for them
                    nextReady.await();
                    continue;
                }
                this.workRunnable = task;
                try {
                    final long remaining = deadline - now;
                    if (remaining <= 0L) {
                        throw new ExecutionTimedOutException();
                    }
                    runnableDequeued.await(remaining, TimeUnit.MILLISECONDS);
                    now = System.currentTimeMillis();
                    if (this.workRunnable == null) {
                        // task was accepted
                        nextReady.signal();
                        return;
                    }
                } finally {
                    this.workRunnable = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        final Executor executor;
        final Set<Thread> runningThreads = this.runningThreads;
        final Lock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor has been shut down");
                }
                final Worker waitingWorker;
                if ((waitingWorker = this.waitingWorker) != null) {
                    // a worker thread was waiting for a task; give it the task and wake it up
                    waitingWorker.setRunnable(task);
                    taskEnqueued.signal();
                    this.waitingWorker = null;
                    return;
                }
                // no worker thread was waiting
                final int currentSize = runningThreads.size();
                if (currentSize < maxThreads) {
                    // if we haven't reached the thread limit yet, start up another thread
                    final Thread thread = threadFactory.newThread(new Worker(task));
                    if (thread == null) {
                        throw new ThreadCreationException();
                    }
                    if (! runningThreads.add(thread)) {
                        throw new ThreadCreationException("Unable to add new thread to the running set");
                    }
                    if (currentSize >= largestPoolSize) {
                        largestPoolSize = currentSize + 1;
                    }
                    thread.start();
                    return;
                }
                // not blocking, not accepted
                executor = handoffExecutor;
                rejectedCount++;
                // fall out to execute outside of lock
                break;
            }
        } finally {
            lock.unlock();
        }
        if (executor != null) {
            executor.execute(task);
        } else {
            throw new RejectedExecutionException();
        }
    }

    /** {@inheritDoc} */
    public <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        shutdownListenable.addShutdownListener(shutdownListener, attachment);
    }

    private static long clipHigh(long value) {
        return value < 0 ? Long.MAX_VALUE : value;
    }

    private final class Worker implements Runnable {

        private Runnable runnable;

        private Worker(final Runnable runnable) {
            this.runnable = runnable;
        }

        private void setRunnable(final Runnable runnable) {
            this.runnable = runnable;
        }

        private boolean awaitTimed(Condition condition, long idleSince) {
            final long end = clipHigh(System.currentTimeMillis() + keepAliveTime);
            long remaining = end - idleSince;
            if (remaining < 0L) {
                return false;
            }
            if (stop) return false;
            try {
                condition.await(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            return ! stop;
        }

        public void run() {
            final Lock lock = QueuelessExecutor.this.lock;
            final Condition workerDequeued = QueuelessExecutor.this.workerDequeued;
            final Condition runnableDequeued = QueuelessExecutor.this.runnableDequeued;
            final Condition taskEnqueued = QueuelessExecutor.this.taskEnqueued;
            final Set<Thread> runningThreads = QueuelessExecutor.this.runningThreads;
            final DirectExecutor taskExecutor = QueuelessExecutor.this.taskExecutor;
            final Thread thread = Thread.currentThread();
            long idleSince = Long.MAX_VALUE;
            Runnable runnable = this.runnable;
            this.runnable = null;
            try {
                MAIN: for (;;) {

                    // Run task
                    try {
                        taskExecutor.execute(runnable);
                    } catch (Throwable t) {
                        log.errorf(t, "Task execution failed for task %s", runnable);
                    }
                    idleSince = System.currentTimeMillis();
                    // Get next task
                    lock.lock();
                    try {
                        if (stop || runningThreads.size() > maxThreads) {
                            if (runningThreads.remove(thread) && runningThreads.isEmpty()) {
                                threadDeath.signalAll();
                            }
                            return;
                        }
                        if ((runnable = workRunnable) != null) {
                            // there's a task, take it and continue on to the top of the loop
                            workRunnable = null;
                            runnableDequeued.signal();
                        } else {
                            // no executors are waiting, so we wait instead for an executor
                            while (waitingWorker != null) {
                                // wait... to wait
                                if (! awaitTimed(workerDequeued, idleSince)) return;
                                if ((runnable = workRunnable) != null) {
                                    // sniped an easy one by luck!
                                    continue MAIN;
                                }
                            }
                            waitingWorker = this;
                            try {
                                do {
                                    // wait for a job
                                    if (! awaitTimed(taskEnqueued, idleSince)) return;
                                } while ((runnable = this.runnable) == null);
                            } finally {
                                waitingWorker = null;
                                workerDequeued.signal();
                            }
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } finally {
                boolean last = false;
                lock.lock();
                try {
                    if (stop && runningThreads.remove(thread) && runningThreads.isEmpty()) {
                        threadDeath.signalAll();
                        last = true;
                    }
                } finally {
                    lock.unlock();
                }
                if (last) {
                    shutdownListenable.shutdown();
                }
            }
        }
    }
}
