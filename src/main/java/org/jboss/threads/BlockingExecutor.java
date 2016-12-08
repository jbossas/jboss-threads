/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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
import java.util.concurrent.TimeUnit;

/**
 * An executor which can optionally block or not block on task submission.
 */
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

    long getNumberOfFreeThreads();
}
