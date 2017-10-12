/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.threads;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
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
import org.jboss.threads.management.BoundedQueueThreadPoolExecutorMBean;

/**
 * An executor which uses a regular queue to hold tasks.  The executor may be tuned at runtime in many ways.
 *
 * @deprecated Use {@link EnhancedQueueExecutor} instead.
 */
@Deprecated
public final class QueueExecutor extends AbstractExecutorService implements BlockingExecutorService, BoundedQueueThreadPoolExecutorMBean, ShutdownListenable {
    private static final Logger log = Logger.getLogger("org.jboss.threads.executor");
    private final SimpleShutdownListenable shutdownListenable = new SimpleShutdownListenable();

    private final Lock lock = new ReentrantLock();
    // signal when a task is written to the queue
    private final Condition enqueueCondition = lock.newCondition();
    // signal when the queue is read
    private final Condition removeCondition = lock.newCondition();
    // signalled when threads terminate
    private final Condition threadExitCondition = lock.newCondition();
    private final ThreadFactory threadFactory;
    private final DirectExecutor taskExecutor;

    // all protected by poolLock...
    private int coreThreads;
    private int maxThreads;
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

    private Queue<Runnable> queue;

    /**
     * Create a new instance.
     *
     * @param coreThreads the number of threads to create before enqueueing tasks
     * @param maxThreads the maximum number of threads to create
     * @param keepAliveTime the amount of time that an idle thread should remain active
     * @param keepAliveTimeUnit the unit of time for {@code keepAliveTime}
     * @param queue the queue to use for tasks
     * @param threadFactory the thread factory to use for new threads
     * @param blocking {@code true} if the executor should block when the queue is full and no threads are available, {@code false} to use the handoff executor
     * @param handoffExecutor the executor which is called when blocking is disabled and a task cannot be accepted, or {@code null} to reject the task
     * @param taskExecutor the executor to use to execute tasks
     */
    public QueueExecutor(final int coreThreads, final int maxThreads, final long keepAliveTime, final TimeUnit keepAliveTimeUnit, final Queue<Runnable> queue, final ThreadFactory threadFactory, final boolean blocking, final Executor handoffExecutor, final DirectExecutor taskExecutor) {
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
            this.coreThreads = coreThreads;
            this.maxThreads = maxThreads > coreThreads ? maxThreads : coreThreads;
            this.queue = queue;
            this.blocking = blocking;
            this.handoffExecutor = handoffExecutor;
            this.taskExecutor = taskExecutor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Create a new instance.
     *
     * @param coreThreads the number of threads to create before enqueueing tasks
     * @param maxThreads the maximum number of threads to create
     * @param keepAliveTime the amount of time that an idle thread should remain active
     * @param keepAliveTimeUnit the unit of time for {@code keepAliveTime}
     * @param queue the queue to use for tasks
     * @param threadFactory the thread factory to use for new threads
     * @param blocking {@code true} if the executor should block when the queue is full and no threads are available, {@code false} to use the handoff executor
     * @param handoffExecutor the executor which is called when blocking is disabled and a task cannot be accepted, or {@code null} to reject the task
     */
    public QueueExecutor(final int coreThreads, final int maxThreads, final long keepAliveTime, final TimeUnit keepAliveTimeUnit, final Queue<Runnable> queue, final ThreadFactory threadFactory, final boolean blocking, final Executor handoffExecutor) {
        this(coreThreads, maxThreads, keepAliveTime, keepAliveTimeUnit, queue, threadFactory, blocking, handoffExecutor, JBossExecutors.directExecutor());
    }

    /**
     * Create a new instance.
     *
     * @param coreThreads the number of threads to create before enqueueing tasks
     * @param maxThreads the maximum number of threads to create
     * @param keepAliveTime the amount of time that an idle thread should remain active
     * @param keepAliveTimeUnit the unit of time for {@code keepAliveTime}
     * @param queueLength the fixed queue length to use for tasks
     * @param threadFactory the thread factory to use for new threads
     * @param blocking {@code true} if the executor should block when the queue is full and no threads are available, {@code false} to use the handoff executor
     * @param handoffExecutor the executor which is called when blocking is disabled and a task cannot be accepted, or {@code null} to reject the task
     */
    public QueueExecutor(final int coreThreads, final int maxThreads, final long keepAliveTime, final TimeUnit keepAliveTimeUnit, final int queueLength, final ThreadFactory threadFactory, final boolean blocking, final Executor handoffExecutor) {
        this(coreThreads, maxThreads, keepAliveTime, keepAliveTimeUnit, new ArrayQueue<Runnable>(queueLength), threadFactory, blocking, handoffExecutor);
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
        if (task == null) {
            throw new NullPointerException("task is null");
        }
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
                if (count < coreThreads) {
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
                if (count < maxThreads) {
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
        } else {
            throw new RejectedExecutionException();
        }
        return;
    }

    /**
     * Execute a task, blocking until it can be accepted, or until the calling thread is interrupted.
     *
     * @param task the task to submit
     *
     * @throws org.jboss.threads.StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws org.jboss.threads.ThreadCreationException if a thread could not be created for some reason
     * @throws java.util.concurrent.RejectedExecutionException if execution is rejected for some other reason
     * @throws InterruptedException if the current thread was interrupted before the task could be accepted
     * @throws NullPointerException if command is {@code null}
     */
    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor is stopped");
                }
                // Try core thread first, then queue, then extra thread
                final int count = threadCount;
                if (count < coreThreads) {
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
                if (count < maxThreads) {
                    startNewThread(task);
                    threadCount = count + 1;
                    return;
                }
                removeCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute a task, blocking until it can be accepted, a timeout elapses, or the calling thread is interrupted.
     *
     * @param task the task to submit
     * @param timeout the amount of time to wait
     * @param unit the unit of time
     *
     * @throws org.jboss.threads.ExecutionTimedOutException if the timeout elapsed before a task could be accepted
     * @throws org.jboss.threads.StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws org.jboss.threads.ThreadCreationException if a thread could not be created for some reason
     * @throws java.util.concurrent.RejectedExecutionException if execution is rejected for some other reason
     * @throws InterruptedException if the current thread was interrupted before the task could be accepted
     * @throws NullPointerException if command is {@code null}
     */
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
        final Lock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                if (stop) {
                    throw new StoppedExecutorException("Executor is stopped");
                }
                // Try core thread first, then queue, then extra thread
                final int count = threadCount;
                if (count < coreThreads) {
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
                if (count < maxThreads) {
                    startNewThread(task);
                    threadCount = count + 1;
                    return;
                }
                final long remaining = deadline - now;
                if (remaining <= 0L) {
                    throw new ExecutionTimedOutException();
                }
                removeCondition.await(remaining, TimeUnit.MILLISECONDS);
                now = System.currentTimeMillis();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute a task, without blocking.
     *
     * @param task the task to submit
     *
     * @throws org.jboss.threads.StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws org.jboss.threads.ThreadCreationException if a thread could not be created for some reason
     * @throws java.util.concurrent.RejectedExecutionException if execution is rejected for some other reason
     * @throws NullPointerException if command is {@code null}
     */
    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        final Executor executor;
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (stop) {
                throw new StoppedExecutorException("Executor is stopped");
            }
            // Try core thread first, then queue, then extra thread
            final int count = threadCount;
            if (count < coreThreads) {
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
            if (count < maxThreads) {
                startNewThread(task);
                threadCount = count + 1;
                return;
            }
            // delegate the task outside of the lock.
            rejectCount++;
            executor = handoffExecutor;
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
        boolean callShutdownListener = false;
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (! stop) {
                stop = true;
                // wake up the whole town
                removeCondition.signalAll();
                enqueueCondition.signalAll();
                if (workers.isEmpty()) {
                    callShutdownListener = true;
                } else {
                    for (Thread worker : workers) {
                        worker.interrupt();
                    }
                }
            }
        } finally {
            lock.unlock();
            if (callShutdownListener)
                shutdownListenable.shutdown();
        }
    }

    /** {@inheritDoc} */
    public List<Runnable> shutdownNow() {
        boolean callShutdownListener = false;
        final Lock lock = this.lock;
        lock.lock();
        try {
            stop = true;
            removeCondition.signalAll();
            enqueueCondition.signalAll();
            if (workers.isEmpty()) {
                callShutdownListener = true;
            } else {
                for (Thread worker : workers) {
                    worker.interrupt();
                }
            }
            final Queue<Runnable> queue = this.queue;
            final ArrayList<Runnable> list = new ArrayList<Runnable>(queue);
            queue.clear();
            return list;
        } finally {
            lock.unlock();
            if (callShutdownListener)
                shutdownListenable.shutdown();
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
    public int getCoreThreads() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return coreThreads;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public void setCoreThreads(final int coreThreads) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            final int oldLimit = this.coreThreads;
            if (maxThreads < coreThreads) {
                // don't let the max thread limit be less than the core thread limit.
                // the called method will signal as needed
                setMaxThreads(coreThreads);
            } else if (oldLimit < coreThreads) {
                // we're growing the number of core threads
                // therefore signal anyone waiting to add tasks; there might be more threads to add
                removeCondition.signalAll();
            } else if (oldLimit > coreThreads) {
                // we're shrinking the number of core threads
                // therefore signal anyone waiting to remove tasks so the pool can shrink properly
                enqueueCondition.signalAll();
            } else {
                // we aren't changing anything...
                return;
            }
            this.coreThreads = coreThreads;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public int getMaxThreads() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return maxThreads;
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    public void setMaxThreads(final int maxThreads) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            final int oldLimit = this.maxThreads;
            if (maxThreads < coreThreads) {
                // don't let the max thread limit be less than the core thread limit.
                // the called method will signal as needed
                setCoreThreads(maxThreads);
            } else if (oldLimit < maxThreads) {
                // we're growing the number of extra threads
                // therefore signal anyone waiting to add tasks; there might be more threads to add
                removeCondition.signalAll();
            } else if (oldLimit > maxThreads) {
                // we're shrinking the number of extra threads
                // therefore signal anyone waiting to remove tasks so the pool can shrink properly
                enqueueCondition.signalAll();
            } else {
                // we aren't changing anything...
                return;
            }
            this.maxThreads = maxThreads;
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

    /** {@inheritDoc} */
    public <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        shutdownListenable.addShutdownListener(shutdownListener, attachment);
    }

    // call with lock held!
    private void startNewThread(final Runnable task) {
        final Thread thread = threadFactory.newThread(new Worker(task));
        if (thread == null) {
            throw new ThreadCreationException();
        }
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
                    final int coreThreadLimit = coreThreads;
                    final boolean allowCoreThreadTimeout = this.allowCoreThreadTimeout;
                    if (stop || threadCount > maxThreads) {
                        // too many threads.  Handle a task if there is one, otherwise exit
                        return pollTask();
                    } else if (allowCoreThreadTimeout || threadCount > coreThreadLimit) {
                        final TimeUnit timeUnit = keepAliveTimeUnit;
                        final long time = keepAliveTime;
                        final long remaining = time - timeUnit.convert(elapsed, TimeUnit.MILLISECONDS);
                        if (remaining > 0L) {
                            try {
                                enqueueCondition.await(remaining, timeUnit);
                            } catch (InterruptedException e) {
                                intr = true;
                            }
                        } else {
                            // the timeout has expired
                            return pollTask();
                        }
                    } else {
                        // ignore timeout until we are not a core thread or until core threads are allowed to time out
                        try {
                            enqueueCondition.await();
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

    /** {@inheritDoc} */
    public int getQueueSize() {
        return this.queue.size();
    }

    private void runTask(Runnable task) {
        if (task != null) try {
            taskExecutor.execute(task);
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
                boolean last = false;
                lock.lock();
                try {
                    workers.remove(Thread.currentThread());
                    last = stop && workers.isEmpty();
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
