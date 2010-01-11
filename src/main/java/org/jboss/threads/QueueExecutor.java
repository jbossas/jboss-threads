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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import org.jboss.logging.Logger;
import org.jboss.threads.management.BoundedQueueThreadPoolExecutorMBean;

/**
 * An executor which uses a regular queue to hold tasks.  The executor may be tuned at runtime in many ways.
 */
public final class QueueExecutor extends AbstractExecutorService implements ExecutorService, BoundedQueueThreadPoolExecutorMBean {
    private static final Logger log = Logger.getLogger("org.jboss.threads.executor");

    private final String name;
    private final Lock lock = new ReentrantLock();
    // signal when a task is written to the queue
    private final Condition enqueueCondition = lock.newCondition();
    // signal when the queue is read
    private final Condition removeCondition = lock.newCondition();
    // signalled when threads terminate
    private final Condition threadExitCondition = lock.newCondition();
    private final ThreadFactory threadFactory;

    // all protected by poolLock...
    private int corePoolSize;
    private int maxPoolSize;
    private int largestPoolSize;
    private int rejectCount;
    private boolean allowCoreThreadTimeout;
    private long keepAliveTime;
    private TimeUnit keepAliveTimeUnit;
    private boolean blocking;
    private Executor handoffExecutor;

    private int threadCount;
    private Set<Thread> workers = new HashSet<Thread>();

    private boolean stop;
    private boolean interrupt;

    private Queue<Runnable> queue;

    /**
     * Create a new instance.
     *
     * @param name the name of the executor
     * @param corePoolSize the number of threads to create before enqueueing tasks
     * @param maxPoolSize the maximum number of threads to create
     * @param keepAliveTime the amount of time that an idle thread should remain active
     * @param keepAliveTimeUnit the unit of time for {@code keepAliveTime}
     * @param queue the queue to use for tasks
     * @param threadFactory the thread factory to use for new threads
     * @param blocking {@code true} if the executor should block when the queue is full and no threads are available, {@code false} to use the handoff executor
     * @param handoffExecutor the executor which is called when blocking is disabled and a task cannot be accepted, or {@code null} to reject the task
     */
    public QueueExecutor(final String name, final int corePoolSize, final int maxPoolSize, final long keepAliveTime, final TimeUnit keepAliveTimeUnit, final Queue<Runnable> queue, final ThreadFactory threadFactory, final boolean blocking, final Executor handoffExecutor) {
        this.name = name;
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory is null");
        }
        if (queue == null) {
            throw new NullPointerException("queue is null");
        }
        if (keepAliveTimeUnit == null) {
            throw new NullPointerException("keepAliveTimeUnit is null");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.threadFactory = threadFactory;
            // configurable...
            this.keepAliveTime = keepAliveTime;
            this.keepAliveTimeUnit = keepAliveTimeUnit;
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queue = queue;
            this.blocking = blocking;
            this.handoffExecutor = handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Create a new instance.
     *
     * @param name the name of the executor
     * @param corePoolSize the number of threads to create before enqueueing tasks
     * @param maxPoolSize the maximum number of threads to create
     * @param keepAliveTime the amount of time that an idle thread should remain active
     * @param keepAliveTimeUnit the unit of time for {@code keepAliveTime}
     * @param queueLength the fixed queue length to use for tasks
     * @param threadFactory the thread factory to use for new threads
     * @param blocking {@code true} if the executor should block when the queue is full and no threads are available, {@code false} to use the handoff executor
     * @param handoffExecutor the executor which is called when blocking is disabled and a task cannot be accepted, or {@code null} to reject the task
     */
    public QueueExecutor(final String name, final int corePoolSize, final int maxPoolSize, final long keepAliveTime, final TimeUnit keepAliveTimeUnit, final int queueLength, final ThreadFactory threadFactory, final boolean blocking, final Executor handoffExecutor) {
        this.name = name;
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory is null");
        }
        if (queue == null) {
            throw new NullPointerException("queue is null");
        }
        if (keepAliveTimeUnit == null) {
            throw new NullPointerException("keepAliveTimeUnit is null");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.threadFactory = threadFactory;
            // configurable...
            this.keepAliveTime = keepAliveTime;
            this.keepAliveTimeUnit = keepAliveTimeUnit;
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            queue = new ArrayQueue<Runnable>(queueLength);
            this.blocking = blocking;
            this.handoffExecutor = handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute a task.
     *
     * @param task the task to execute
     * @throws RejectedExecutionException when a task is rejected by the handoff executor
     * @throws StoppedExecutorException when the executor is terminating
     * @throws ExecutionInterruptedException when blocking is enabled and the current thread is interrupted before a task could be accepted
     */
    public void execute(final Runnable task) throws RejectedExecutionException {
        final Executor executor;
        final Lock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor is stopped");
                }
                // Try core thread first, then queue, then extra thread
                final int count = threadCount;
                if (count < corePoolSize) {
                    startNewThread(task);
                    threadCount = count + 1;
                    return;
                }
                // next queue...
                final Queue<Runnable> queue = this.queue;
                if (queue.offer(task)) {
                    enqueueCondition.signal();
                    return;
                }
                // extra threads?
                if (count < maxPoolSize) {
                    startNewThread(task);
                    threadCount = count + 1;
                    return;
                }
                if (blocking) {
                    try {
                        removeCondition.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ExecutionInterruptedException("Thread interrupted");
                    }
                } else {
                    // delegate the task outside of the lock.
                    rejectCount++;
                    executor = handoffExecutor;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        if (executor != null) {
            executor.execute(task);
        }
        return;
    }

    /** {@inheritDoc} */
    public void shutdown() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (! stop) {
                stop = true;
                // wake up the whole town
                removeCondition.signalAll();
                enqueueCondition.signalAll();
                for (Thread worker : workers) {
                    worker.interrupt();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public List<Runnable> shutdownNow() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            stop = true;
            removeCondition.signalAll();
            enqueueCondition.signalAll();
            for (Thread worker : workers) {
                worker.interrupt();
            }
            final Queue<Runnable> queue = this.queue;
            final ArrayList<Runnable> list = new ArrayList<Runnable>(queue);
            queue.clear();
            return list;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public boolean isShutdown() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return stop;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public boolean isTerminated() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return stop && threadCount == 0;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        final Lock lock = this.lock;
        lock.lockInterruptibly();
        try {
            if (workers.contains(Thread.currentThread())) {
                throw new IllegalStateException("Cannot await termination of a thread pool from one of its threads");
            }
            final long start = System.currentTimeMillis();
            long elapsed = 0L;
            while (! stop && threadCount > 0) {
                final long remaining = timeout - elapsed;
                if (remaining <= 0) {
                    return false;
                }
                threadExitCondition.await(remaining, unit);
                elapsed = unit.convert(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public boolean isAllowCoreThreadTimeout() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return allowCoreThreadTimeout;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.allowCoreThreadTimeout = allowCoreThreadTimeout;
            if (allowCoreThreadTimeout) {
                // wake everyone up so core threads can time out
                enqueueCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public int getCorePoolSize() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return corePoolSize;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public void setCorePoolSize(final int corePoolSize) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            final int oldLimit = this.corePoolSize;
            if (maxPoolSize < corePoolSize) {
                // don't let the max thread limit be less than the core thread limit.
                // the called method will signal as needed
                setMaxPoolSize(corePoolSize);
            } else if (oldLimit < corePoolSize) {
                // we're growing the number of core threads
                // therefore signal anyone waiting to add tasks; there might be more threads to add
                removeCondition.signalAll();
            } else if (oldLimit > corePoolSize) {
                // we're shrinking the number of core threads
                // therefore signal anyone waiting to remove tasks so the pool can shrink properly
                enqueueCondition.signalAll();
            } else {
                // we aren't changing anything...
                return;
            }
            this.corePoolSize = corePoolSize;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public int getMaxPoolSize() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return maxPoolSize;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public void setMaxPoolSize(final int maxPoolSize) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            final int oldLimit = this.maxPoolSize;
            if (maxPoolSize < corePoolSize) {
                // don't let the max thread limit be less than the core thread limit.
                // the called method will signal as needed
                setCorePoolSize(maxPoolSize);
            } else if (oldLimit < maxPoolSize) {
                // we're growing the number of extra threads
                // therefore signal anyone waiting to add tasks; there might be more threads to add
                removeCondition.signalAll();
            } else if (oldLimit > maxPoolSize) {
                // we're shrinking the number of extra threads
                // therefore signal anyone waiting to remove tasks so the pool can shrink properly
                enqueueCondition.signalAll();
            } else {
                // we aren't changing anything...
                return;
            }
            this.maxPoolSize = maxPoolSize;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public long getKeepAliveTime() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return keepAliveTime;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the keep-alive time to the given amount of time.
     *
     * @param keepAliveTime the amount of time
     * @param keepAliveTimeUnit the unit of time
     */
    public void setKeepAliveTime(final long keepAliveTime, final TimeUnit keepAliveTimeUnit) {
        if (keepAliveTimeUnit == null) {
            throw new NullPointerException("keepAliveTimeUnit is null");
        }
        if (keepAliveTime < 0L) {
            throw new IllegalArgumentException("keepAliveTime is less than zero");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.keepAliveTime = keepAliveTimeUnit.convert(keepAliveTime, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public void setKeepAliveTime(final long milliseconds) {
        setKeepAliveTime(milliseconds, TimeUnit.MILLISECONDS);
    }

    /**
     * Determine whether this thread pool executor is set to block when a task cannot be accepted immediately.
     *
     * @return {@code true} if blocking is enabled, {@code false} if the handoff executor is used
     */
    public boolean isBlocking() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return blocking;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set whether this thread pool executor should be set to block when a task cannot be accepted immediately.
     *
     * @param blocking {@code true} if blocking is enabled, {@code false} if the handoff executor is used
     */
    public void setBlocking(boolean blocking) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.blocking = blocking;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the handoff executor which is called when a task cannot be accepted immediately.
     *
     * @return the handoff executor
     */
    public Executor getHandoffExecutor() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the handoff executor which is called when a task cannot be accepted immediately.
     *
     * @param handoffExecutor the handoff executor
     */
    public void setHandoffExecutor(final Executor handoffExecutor) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.handoffExecutor = handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    // call with lock held!
    private void startNewThread(final Runnable task) {
        final Thread thread = threadFactory.newThread(new Worker(task));
        workers.add(thread);
        final int size = workers.size();
        if (size > largestPoolSize) {
            largestPoolSize = size;
        }
        thread.start();
    }

    // call with lock held!
    private Runnable pollTask() {
        final Runnable task = queue.poll();
        if (task != null) {
            removeCondition.signal();
            return task;
        } else {
            if (-- threadCount == 0) {
                threadExitCondition.signalAll();
            }
            return null;
        }
    }

    // call with lock held!
    private Runnable takeTask() {
        final Condition removeCondition = this.removeCondition;
        Runnable task = queue.poll();
        if (task != null) {
            removeCondition.signal();
            return task;
        } else {
            final Condition enqueueCondition = this.enqueueCondition;
            final long start = System.currentTimeMillis();
            boolean intr = Thread.interrupted();
            try {
                long elapsed = 0L;
                for (;;) {
                    // these parameters may change on each iteration
                    final int threadCount = this.threadCount;
                    final int coreThreadLimit = corePoolSize;
                    final boolean allowCoreThreadTimeout = this.allowCoreThreadTimeout;
                    if (stop || threadCount > maxPoolSize) {
                        // too many threads.  Handle a task if there is one, otherwise exit
                        return pollTask();
                    } else if (!allowCoreThreadTimeout && threadCount < coreThreadLimit) {
                        // ignore timeout until we are not a core thread or until core threads are allowed to time out
                        try {
                            enqueueCondition.await();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    } else {
                        final TimeUnit timeUnit = keepAliveTimeUnit;
                        final long time = keepAliveTime;
                        final long remaining = time - timeUnit.convert(elapsed, TimeUnit.MILLISECONDS);
                        if (remaining <= 0L && (allowCoreThreadTimeout || threadCount > coreThreadLimit)) {
                            // the timeout has expired
                            return pollTask();
                        }
                        try {
                            enqueueCondition.await(remaining, timeUnit);
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    }
                    task = queue.poll();
                    if (task != null) {
                        removeCondition.signal();
                        return task;
                    }
                    elapsed = System.currentTimeMillis() - start;
                }
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** {@inheritDoc} */
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    public int getCurrentThreadCount() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return workers.size();
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public int getLargestThreadCount() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return largestPoolSize;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public int getRejectedCount() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return rejectCount;
        } finally {
            lock.unlock();
        }
    }

    private void runTask(Runnable task) {
        if (task != null) try {
            task.run();
        } catch (Throwable t) {
            log.errorf(t, "Task execution failed for task %s", task);
        }
    }

    private class Worker implements Runnable {

        private volatile Runnable first;

        public Worker(final Runnable command) {
            first = command;
        }

        public void run() {
            final Lock lock = QueueExecutor.this.lock;
            try {
                Runnable task = first;
                // Release reference to task
                first = null;
                runTask(task);
                for (;;) {
                    // don't hang on to task while we possibly block waiting for the next one
                    task = null;
                    lock.lock();
                    try {
                        if (stop) {
                            // drain queue
                            if ((task = pollTask()) == null) {
                                return;
                            }
                            Thread.currentThread().interrupt();
                        } else {
                            // get next task
                            if ((task = takeTask()) == null) {
                                return;
                            }
                        }
                    } finally {
                        lock.unlock();
                    }
                    runTask(task);
                    Thread.interrupted();
                }
            } finally {
                lock.lock();
                try {
                    workers.remove(Thread.currentThread());
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
