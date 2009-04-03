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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A simple load-balancing executor.  If no delegate executors are defined, then tasks are rejected.  Executors are
 * chosen in a round-robin fashion.
 */
public class BalancingExecutor implements Executor {

    private volatile Executor[] executors = null;
    private final AtomicInteger seq = new AtomicInteger();
    private final Lock writeLock = new ReentrantLock();

    private static final AtomicArray<BalancingExecutor, Executor> executorsUpdater = AtomicArray.create(newUpdater(BalancingExecutor.class, Executor[].class, "executors"), Executor.class);

    /**
     * Construct a new instance.
     */
    public BalancingExecutor() {
        executorsUpdater.clear(this);
    }

    /**
     * Construct a new instance.
     *
     * @param executors the initial list of executors to delegate to
     */
    public BalancingExecutor(Executor... executors) {
        if (executors != null && executors.length > 0) {
            final Executor[] clone = executors.clone();
            for (int i = 0; i < clone.length; i++) {
                if (clone[i] == null) {
                    throw new NullPointerException("executor at index " + i + " is null");
                }
            }
            executorsUpdater.set(this, clone);
        } else {
            executorsUpdater.clear(this);
        }
    }

    /**
     * Execute a task.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException if no executors are available to run the task
     */
    public void execute(final Runnable command) throws RejectedExecutionException {
        final Executor[] executors = this.executors;
        final int len = executors.length;
        if (len == 0) {
            throw new RejectedExecutionException("No executors available to run task");
        }
        executors[seq.getAndIncrement() % len].execute(command);
    }

    /**
     * Clear out all delegate executors at once.  Tasks will be rejected until another delegate executor is added.
     */
    public void clear() {
        executorsUpdater.clear(this);
    }

    /**
     * Add a delegate executor.
     *
     * @param executor the executor to add
     */
    public void addExecutor(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        final Lock lock = writeLock;
        lock.lock();
        try {
            executorsUpdater.add(this, executor);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a delegate executor.
     *
     * @param executor the executor to remove
     */
    public void removeExecutor(final Executor executor) {
        if (executor == null) {
            return;
        }
        final Lock lock = writeLock;
        lock.lock();
        try {
            executorsUpdater.remove(this, executor, true);
        } finally {
            lock.unlock();
        }
    }
}
