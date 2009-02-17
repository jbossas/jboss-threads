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

/**
 * An executor which uses a regular queue to hold tasks.  The executor may be tuned at runtime in many ways.
 */
public final class SimpleQueueExecutor extends AbstractExecutorService implements ExecutorService {
    private final Lock lock = new ReentrantLock();
    // signal when a task is written to the queue
    private final Condition enqueueCondition = lock.newCondition();
    // signal when the queue is read
    private final Condition removeCondition = lock.newCondition();
    // signalled when threads terminate
    private final Condition threadExitCondition = lock.newCondition();
    private final ThreadFactory threadFactory;

    // all protected by poolLock...
    private int coreThreadLimit;
    private int maxThreadLimit;
    private boolean allowCoreThreadTimeout;
    private long keepAliveTime;
    private TimeUnit keepAliveTimeUnit;
    private RejectionPolicy rejectionPolicy;
    private Executor handoffExecutor;

    private int threadCount;
    private Set<Thread> workers = new HashSet<Thread>();

    private boolean stop;
    private boolean interrupt;

    private Queue<Runnable> queue;

    public SimpleQueueExecutor(final int coreThreadLimit, final int maxThreadLimit, final long keepAliveTime, final TimeUnit keepAliveTimeUnit, final Queue<Runnable> queue, final ThreadFactory threadFactory, final RejectionPolicy rejectionPolicy, final Executor handoffExecutor) {
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory is null");
        }
        if (queue == null) {
            throw new NullPointerException("queue is null");
        }
        if (keepAliveTimeUnit == null) {
            throw new NullPointerException("keepAliveTimeUnit is null");
        }
        if (rejectionPolicy == null) {
            throw new NullPointerException("rejectionPolicy is null");
        }
        if (rejectionPolicy == RejectionPolicy.HANDOFF && handoffExecutor == null) {
            throw new NullPointerException("handoffExecutor is null");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            this.threadFactory = threadFactory;
            // configurable...
            this.keepAliveTime = keepAliveTime;
            this.keepAliveTimeUnit = keepAliveTimeUnit;
            this.coreThreadLimit = coreThreadLimit;
            this.maxThreadLimit = maxThreadLimit;
            this.queue = queue;
            this.rejectionPolicy = rejectionPolicy;
            this.handoffExecutor = handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    public void execute(final Runnable task) throws RejectedExecutionException {
        final Lock lock = this.lock;
        try {
            lock.lockInterruptibly();
            try {
                for (;;) {
                    if (stop) {
                        throw new RejectedExecutionException("Executor is stopped");
                    }
                    // Try core thread first, then queue, then extra thread
                    final int count = threadCount;
                    if (count < coreThreadLimit) {
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
                    if (count < maxThreadLimit) {
                        startNewThread(task);
                        threadCount = count + 1;
                        return;
                    }
                    // how to reject the task...
                    switch (rejectionPolicy) {
                        case ABORT:
                            throw new RejectedExecutionException("Executor is busy");
                        case BLOCK:
                            removeCondition.await();
                            break;
                        case DISCARD:
                            return;
                        case DISCARD_OLDEST:
                            if (queue.poll() != null) {
                                queue.add(task);
                                enqueueCondition.signal();
                            }
                            return;
                        case HANDOFF:
                            handoffExecutor.execute(task);
                            return;
                    }
                }
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Thread interrupted");
        }
    }

    public void shutdown() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (! stop) {
                stop = true;
                // wake up the whole town
                removeCondition.signalAll();
                enqueueCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Runnable> shutdownNow() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            stop = true;
            interrupt = true;
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
            return stop && threadCount == 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        final Lock lock = this.lock;
        lock.lockInterruptibly();
        try {
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

    public boolean isAllowCoreThreadTimeout() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return allowCoreThreadTimeout;
        } finally {
            lock.unlock();
        }
    }

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

    public int getCoreThreadLimit() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return coreThreadLimit;
        } finally {
            lock.unlock();
        }
    }

    public void setCoreThreadLimit(final int coreThreadLimit) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            final int oldLimit = this.coreThreadLimit;
            if (maxThreadLimit < coreThreadLimit) {
                // don't let the max thread limit be less than the core thread limit.
                // the called method will signal as needed
                setMaxThreadLimit(coreThreadLimit);
            } else if (oldLimit < coreThreadLimit) {
                // we're growing the number of core threads
                // therefore signal anyone waiting to add tasks; there might be more threads to add
                removeCondition.signalAll();
            } else if (oldLimit > coreThreadLimit) {
                // we're shrinking the number of core threads
                // therefore signal anyone waiting to remove tasks so the pool can shrink properly
                enqueueCondition.signalAll();
            } else {
                // we aren't changing anything...
                return;
            }
            this.coreThreadLimit = coreThreadLimit;
        } finally {
            lock.unlock();
        }
    }

    public int getMaxThreadLimit() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return maxThreadLimit;
        } finally {
            lock.unlock();
        }
    }

    public void setMaxThreadLimit(final int maxThreadLimit) {
        final Lock lock = this.lock;
        lock.lock();
        try {
            final int oldLimit = this.maxThreadLimit;
            if (maxThreadLimit < coreThreadLimit) {
                // don't let the max thread limit be less than the core thread limit.
                // the called method will signal as needed
                setCoreThreadLimit(maxThreadLimit);
            } else if (oldLimit < maxThreadLimit) {
                // we're growing the number of extra threads
                // therefore signal anyone waiting to add tasks; there might be more threads to add
                removeCondition.signalAll();
            } else if (oldLimit > maxThreadLimit) {
                // we're shrinking the number of extra threads
                // therefore signal anyone waiting to remove tasks so the pool can shrink properly
                enqueueCondition.signalAll();
            } else {
                // we aren't changing anything...
                return;
            }
            this.maxThreadLimit = maxThreadLimit;
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

    public TimeUnit getKeepAliveTimeUnit() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return keepAliveTimeUnit;
        } finally {
            lock.unlock();
        }
    }

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
            this.keepAliveTime = keepAliveTime;
            this.keepAliveTimeUnit = keepAliveTimeUnit;
        } finally {
            lock.unlock();
        }
    }

    public RejectionPolicy getRejectionPolicy() {
        final Lock lock = this.lock;
        lock.lock();
        try {
            return rejectionPolicy;
        } finally {
            lock.unlock();
        }
    }

    public void setRejectionPolicy(final RejectionPolicy newPolicy, final Executor handoffExecutor) {
        if (newPolicy == null) {
            throw new NullPointerException("rejectionPolicy is null");
        }
        if (newPolicy == RejectionPolicy.HANDOFF && handoffExecutor == null) {
            throw new NullPointerException("handoffExecutor is null");
        }
        final Lock lock = this.lock;
        lock.lock();
        try {
            if (rejectionPolicy == RejectionPolicy.BLOCK && newPolicy != RejectionPolicy.BLOCK) {
                // there could be blocking .execute() calls out there; give them a nudge
                removeCondition.signalAll();
            }
            rejectionPolicy = newPolicy;
            this.handoffExecutor = handoffExecutor;
        } finally {
            lock.unlock();
        }
    }

    // container lifecycle methods
    public void stop() {
        shutdown();
        try {
            awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            // todo log if fails?
        } catch (InterruptedException e) {
            // todo log it?
            Thread.currentThread().interrupt();
        }
    }

    public void destroy() {
        // todo is this the right behavior?
        shutdownNow();
        try {
            awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            // todo log if fails?
        } catch (InterruptedException e) {
            // todo log it?
            Thread.currentThread().interrupt();
        }
    }

    // call with lock held!
    private void startNewThread(final Runnable task) {
        final Thread thread = threadFactory.newThread(new Worker(task));
        workers.add(thread);
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
                    final int coreThreadLimit = this.coreThreadLimit;
                    final boolean allowCoreThreadTimeout = this.allowCoreThreadTimeout;
                    if (stop || threadCount > maxThreadLimit) {
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

    private class Worker implements Runnable {

        private Runnable first;

        public Worker(final Runnable command) {
            first = command;
        }

        public void run() {
            final Lock lock = SimpleQueueExecutor.this.lock;
            Runnable task = first;
            // Release reference to task
            first = null;
            lock.lock();
            try {
                for (;;) {
                    if (task != null) {
                        try {
                            if (interrupt) {
                                Thread.currentThread().interrupt();
                            }
                            lock.unlock();
                            try {
                                task.run();
                            } finally {
                                // this is OK because it's in the finally block after lock.unlock()
                                //noinspection LockAcquiredButNotSafelyReleased
                                lock.lock();
                            }
                        } catch (Throwable t) {
                            // todo - log the exception perhaps
                        }
                        // don't hang on to task while we possibly block down below
                        task = null;
                    }
                    if (stop) {
                        // drain queue
                        if ((task = pollTask()) == null) {
                            return;
                        }
                    } else {
                        // get next task
                        if ((task = takeTask()) == null) {
                            return;
                        }
                    }
                }
            } finally {
                workers.remove(Thread.currentThread());
                lock.unlock();
            }
        }
    }
}
