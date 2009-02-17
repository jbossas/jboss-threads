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

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

/**
 * An executor that always runs all tasks in queue order, using a delegate executor to run the tasks.
 * <p/>
 * More specifically, if a FIFO queue type is used, any call B to the {@link #execute(Runnable)} method that
 * happens-after another call A to the same method, will result in B's task running after A's.
 */
public final class OrderedExecutor implements Executor {

    private final Executor parent;
    private final Runnable runner = new Runner();
    private final Lock lock = new ReentrantLock();
    private final Condition removeCondition = lock.newCondition();
    // @protectedby lock
    private final Queue<Runnable> queue;
    // @protectedby lock
    private boolean running;
    // @protectedby lock
    private RejectionPolicy policy;
    // @protectedby lock
    private Executor handoffExecutor;

    /**
     * Construct a new instance using an unbounded FIFO queue.  Since the queue is unbounded, tasks are never
     * rejected.
     *
     * @param parent the parent to delegate tasks to
     */
    public OrderedExecutor(final Executor parent) {
        this(parent, RejectionPolicy.BLOCK, null);
    }

    /**
     * Construct a new instance using the given queue and a blocking reject policy.
     *
     * @param parent the parent to delegate tasks to
     * @param queue the queue to use to hold tasks
     */
    public OrderedExecutor(final Executor parent, final Queue<Runnable> queue) {
        this(parent, queue, RejectionPolicy.BLOCK, null);
    }

    /**
     * Construct a new instance using an unbounded FIFO queue.
     *
     * @param parent the parent executor
     * @param policy the task rejection policy
     * @param handoffExecutor the executor to hand tasks to if the queue is full
     */
    public OrderedExecutor(final Executor parent, final RejectionPolicy policy, final Executor handoffExecutor) {
        this(parent, new LinkedList<Runnable>(), policy, handoffExecutor);
    }

    /**
     * Construct a new instance.
     *
     * @param parent the parent executor
     * @param queue the task queue to use
     * @param policy the task rejection policy
     * @param handoffExecutor the executor to hand tasks to if the queue is full
     */
    public OrderedExecutor(final Executor parent, final Queue<Runnable> queue, final RejectionPolicy policy, final Executor handoffExecutor) {
        if (parent == null) {
            throw new NullPointerException("parent is null");
        }
        if (queue == null) {
            throw new NullPointerException("queue is null");
        }
        if (policy == null) {
            throw new NullPointerException("policy is null");
        }
        if (policy == RejectionPolicy.HANDOFF && handoffExecutor == null) {
            throw new NullPointerException("handoffExecutor is null");
        }
        this.queue = queue;
        this.parent = parent;
        this.policy = policy;
        this.handoffExecutor = handoffExecutor;
    }

    /**
     * Run a task.
     *
     * @param command the task to run.
     */
    public void execute(Runnable command) {
        try {
            lock.lockInterruptibly();
            try {
                while (! queue.offer(command)) {
                    switch (policy) {
                        case ABORT:
                            throw new RejectedExecutionException();
                        case BLOCK:
                            removeCondition.await();
                            break;
                        case DISCARD:
                            return;
                        case DISCARD_OLDEST:
                            if (queue.poll() != null) {
                                queue.add(command);
                            }
                            break;
                        case HANDOFF:
                            handoffExecutor.execute(command);
                            return;
                    }
                }
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
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            throw new RejectedExecutionException();
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
