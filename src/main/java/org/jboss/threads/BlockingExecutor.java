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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.wildfly.common.Assert;

/**
 * An executor which can optionally block or not block on task submission.
 *
 * @deprecated Executors in this package will always accept tasks immediately.
 */
@Deprecated
public interface BlockingExecutor extends Executor {

    /**
     * Executes the given command at some time in the future.  The command may execute in a new thread, in a pooled thread,
     * or in the calling thread, at the discretion of the <tt>Executor</tt> implementation.  The call may block
     * or not block, depending on the configuration of the executor.
     *
     * @param task the task to submit
     *
     * @throws ExecutionInterruptedException if the executor is configured to block, and the thread was interrupted while waiting
     *              for the task to be accepted
     * @throws StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws ThreadCreationException if a thread could not be created for some reason
     * @throws RejectedExecutionException if execution is rejected for some other reason
     * @throws NullPointerException if command is {@code null}
     */
    void execute(Runnable task);

    /**
     * Execute a task, blocking until it can be accepted, or until the calling thread is interrupted.
     *
     * @param task the task to submit
     * @throws StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws ThreadCreationException if a thread could not be created for some reason
     * @throws RejectedExecutionException if execution is rejected for some other reason
     * @throws InterruptedException if the current thread was interrupted before the task could be accepted
     * @throws NullPointerException if command is {@code null}
     */
    void executeBlocking(Runnable task) throws RejectedExecutionException, InterruptedException;

    /**
     * Execute a task, blocking until it can be accepted, a timeout elapses, or the calling thread is interrupted.
     *
     * @param task the task to submit
     * @param timeout the amount of time to wait
     * @param unit the unit of time
     * @throws ExecutionTimedOutException if the timeout elapsed before a task could be accepted
     * @throws StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws ThreadCreationException if a thread could not be created for some reason
     * @throws RejectedExecutionException if execution is rejected for some other reason
     * @throws InterruptedException if the current thread was interrupted before the task could be accepted
     * @throws NullPointerException if command is {@code null}
     */
    void executeBlocking(Runnable task, long timeout, TimeUnit unit) throws RejectedExecutionException, InterruptedException;

    /**
     * Execute a task, without blocking.
     *
     * @param task the task to submit
     * @throws StoppedExecutorException if the executor was shut down before the task was accepted
     * @throws ThreadCreationException if a thread could not be created for some reason
     * @throws RejectedExecutionException if execution is rejected for some other reason
     * @throws NullPointerException if command is {@code null}
     */
    void executeNonBlocking(Runnable task) throws RejectedExecutionException;

    /**
     * Convert an executor to a "blocking" executor which never actually blocks.
     *
     * @param executor the executor (must not be {@code null})
     * @return the blocking executor (not {@code null})
     */
    static BlockingExecutor of(Executor executor) {
        Assert.checkNotNullParam("executor", executor);
        return executor instanceof BlockingExecutor ? (BlockingExecutor) executor : new BlockingExecutor() {
            public void execute(final Runnable task) {
                executor.execute(task);
            }

            public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
                executor.execute(task);
            }

            public void executeBlocking(final Runnable task, final long timeout, final TimeUnit unit) throws RejectedExecutionException, InterruptedException {
                executor.execute(task);
            }

            public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
                executor.execute(task);
            }
        };
    }
}
