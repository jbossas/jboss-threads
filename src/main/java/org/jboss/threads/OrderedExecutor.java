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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

/**
 * An executor that always runs all tasks in queue order, using a delegate executor to run the tasks.
 * <p/>
 * More specifically, if a FIFO queue type is used, any call B to the {@link #execute(Runnable)} method that
 * happens-after another call A to the same method, will result in B's task running after A's.
 */
public final class OrderedExecutor implements BlockingExecutor {

    private final Executor parent;
    private final Runnable runner = new Runner();
    private final Lock lock = new ReentrantLock();
    private final Condition removeCondition = lock.newCondition();
    // @protectedby lock
    private final Queue<Runnable> queue;
    // @protectedby lock
    private boolean running;
    // @protectedby lock
    private boolean blocking;
    // @protectedby lock
    private Executor handoffExecutor;

    /**
     * Construct a new instance using an unbounded FIFO queue.  Since the queue is unbounded, tasks are never
     * rejected.
     *
     * @param parent the parent to delegate tasks to
     */
    public OrderedExecutor(final Executor parent) {
        this(parent, new ArrayDeque<Runnable>());
    }

    /**
     * Construct a new instance using the given queue and a blocking reject policy.
     *
     * @param parent the parent to delegate tasks to
     * @param queue the queue to use to hold tasks
     */
    public OrderedExecutor(final Executor parent, final Queue<Runnable> queue) {
        this(parent, queue, true, null);
    }

    /**
     * Construct a new instance using a bounded FIFO queue of the given size and a blocking reject policy.
     *
     * @param parent the parent to delegate tasks to
     * @param queueLength the fixed length of the queue to use to hold tasks
     */
    public OrderedExecutor(final Executor parent, final int queueLength) {
        this(parent, new ArrayQueue<Runnable>(queueLength), true, null);
    }

    /**
     * Construct a new instance using a bounded FIFO queue of the given size and a handoff reject policy.
     *
     * @param parent the parent executor
     * @param queueLength the fixed length of the queue to use to hold tasks
     * @param handoffExecutor the executor to hand tasks to if the queue is full
     */
    public OrderedExecutor(final Executor parent, final int queueLength, final Executor handoffExecutor) {
        this(parent, new ArrayQueue<Runnable>(queueLength), false, handoffExecutor);
    }

    /**
     * Construct a new instance.
     *
     * @param parent the parent executor
     * @param queue the task queue to use
     * @param blocking {@code true} if rejected tasks should block, {@code false} if rejected tasks should be handed off
     * @param handoffExecutor the executor to hand tasks to if the queue is full
     */
    public OrderedExecutor(final Executor parent, final Queue<Runnable> queue, final boolean blocking, final Executor handoffExecutor) {
        if (parent == null) {
            throw new NullPointerException("parent is null");
        }
        if (queue == null) {
            throw new NullPointerException("queue is null");
        }
        this.queue = queue;
        this.parent = parent;
        this.blocking = blocking;
        this.handoffExecutor = handoffExecutor;
    }

    /**
     * Construct a new instance using a bounded FIFO queue of the given size and a handoff reject policy.
     *
     * @param parent the parent executor
     * @param queueLength the fixed length of the queue to use to hold tasks
     * @param blocking {@code true} if rejected tasks should block, {@code false} if rejected tasks should be handed off
     * @param handoffExecutor the executor to hand tasks to if the queue is full
     */
    public OrderedExecutor(final Executor parent, final int queueLength, final boolean blocking, final Executor handoffExecutor) {
        this(parent, new ArrayQueue<Runnable>(queueLength), blocking, handoffExecutor);
    }

    /**
     * Run a task.
     *
     * @param task the task to run.
     */
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        Executor executor;
        OUT: for (;;) {
            lock.lock();
            try {
                if (! running) {
                    running = true;
                    boolean ok = false;
                    try {
                        parent.execute(runner);
                        ok = true;
                    } finally {
                        if (! ok) {
                            running = false;
                        }
                    }
                }
                if (! queue.offer(task)) {
                    if (blocking) {
                        try {
                            removeCondition.await();
                            continue;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ExecutionInterruptedException();
                        }
                    } else {
                        executor = handoffExecutor;
                        break;
                    }
                }
                return;
            } finally {
                lock.unlock();
            }
        }
        if (executor != null) {
            executor.execute(task);
        }
    }

    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        OUT: for (;;) {
            lock.lock();
            try {
                if (! running) {
                    running = true;
                    boolean ok = false;
                    try {
                        parent.execute(runner);
                        ok = true;
                    } finally {
                        if (! ok) {
                            running = false;
                        }
                    }
                }
                if (! queue.offer(task)) {
                    removeCondition.await();
                    continue;
                }
                return;
            } finally {
                lock.unlock();
            }
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
        OUT: for (;;) {
            lock.lock();
            try {
                if (! running) {
                    running = true;
                    boolean ok = false;
                    try {
                        parent.execute(runner);
                        ok = true;
                    } finally {
                        if (! ok) {
                            running = false;
                        }
                    }
                }
                if (! queue.offer(task)) {
                    final long remaining = deadline - now;
                    if (remaining <= 0L) {
                        throw new ExecutionTimedOutException();
                    }
                    removeCondition.await(remaining, TimeUnit.MILLISECONDS);
                    now = System.currentTimeMillis();
                    continue;
                }
                return;
            } finally {
                lock.unlock();
            }
        }

    }

    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        Executor executor;
        OUT: for (;;) {
            lock.lock();
            try {
                if (! running) {
                    running = true;
                    boolean ok = false;
                    try {
                        parent.execute(runner);
                        ok = true;
                    } finally {
                        if (! ok) {
                            running = false;
                        }
                    }
                }
                if (! queue.offer(task)) {
                    executor = handoffExecutor;
                    break;
                }
                return;
            } finally {
                lock.unlock();
            }
        }
        if (executor != null) {
            executor.execute(task);
        }
    }

    private class Runner implements Runnable {
        public void run() {
            for (;;) {
                final Runnable task;
                lock.lock();
                try {
                    task = queue.poll();
                    removeCondition.signal();
                    if (task == null) {
                        running = false;
                        return;
                    }
                } finally {
                    lock.unlock();
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    // todo log?
                }
            }
        }
    }
}
