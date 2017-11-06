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

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

import org.wildfly.common.Assert;

/**
 * An executor that always runs all tasks in queue order, using a delegate executor to run the tasks.
 * <p/>
 * More specifically, if a FIFO queue type is used, any call B to the {@link #execute(Runnable)} method that
 * happens-after another call A to the same method, will result in B's task running after A's.
 */
public final class OrderedExecutor extends AbstractExecutorService implements BlockingExecutorService {

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
        Assert.checkNotNullParam("parent", parent);
        Assert.checkNotNullParam("queue", queue);
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
        Assert.checkNotNullParam("task", task);
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
                            throw Messages.msg.executionInterrupted();
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

    @Deprecated
    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        Assert.checkNotNullParam("task", task);
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

    @Deprecated
    public void executeBlocking(final Runnable task, final long timeout, final TimeUnit unit) throws RejectedExecutionException, InterruptedException {
        Assert.checkNotNullParam("task", task);
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
                        throw Messages.msg.executionTimedOut();
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

    @Deprecated
    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        Assert.checkNotNullParam("task", task);
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

    public boolean isShutdown() {
        // container managed executors are never shut down from the application's perspective
        return false;
    }

    public boolean isTerminated() {
        // container managed executors are never shut down from the application's perspective
        return false;
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return false;
    }

    public void shutdown() {
        throw Messages.msg.notAllowedContainerManaged("shutdown");
    }

    public List<Runnable> shutdownNow() {
        throw Messages.msg.notAllowedContainerManaged("shutdownNow");
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
