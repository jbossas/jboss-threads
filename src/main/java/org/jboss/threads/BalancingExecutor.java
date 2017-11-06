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

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import org.wildfly.common.Assert;

/**
 * A simple load-balancing executor.  If no delegate executors are defined, then tasks are rejected.  Executors are
 * chosen in a random fashion.
 */
public class BalancingExecutor implements Executor {

    private static final Executor[] NO_EXECUTORS = new Executor[0];
    private volatile Executor[] executors = NO_EXECUTORS;

    private static final AtomicArray<BalancingExecutor, Executor> executorsUpdater = AtomicArray.create(newUpdater(BalancingExecutor.class, Executor[].class, "executors"), NO_EXECUTORS);

    /**
     * Construct a new instance.
     */
    public BalancingExecutor() {
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
                Assert.checkNotNullArrayParam("executors", i, clone[i]);
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
            throw Messages.msg.noExecutorsAvailable();
        }
        executors[ThreadLocalRandom.current().nextInt(len)].execute(command);
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
        Assert.checkNotNullParam("executor", executor);
        synchronized (this) {
            executorsUpdater.add(this, executor);
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
        synchronized (this) {
            executorsUpdater.remove(this, executor, true);
        }
    }
}
