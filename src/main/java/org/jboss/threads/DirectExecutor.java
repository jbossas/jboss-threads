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

/**
 * A direct executor.  Such an executor is <b>required</b> to run the given task in the current thread rather than
 * delegate to a thread pool; furthermore, the task is guaranteed to be terminated when the call to the
 * {@link #execute(Runnable)} method returns.
 *
 * @see JBossExecutors#directExecutor()
 * @see JBossExecutors#rejectingExecutor()
 * @see JBossExecutors#discardingExecutor()
 * @see JBossExecutors#privilegedExecutor(org.jboss.threads.DirectExecutor)
 * @see JBossExecutors#contextClassLoaderExecutor(org.jboss.threads.DirectExecutor, java.lang.ClassLoader)
 * @see JBossExecutors#threadNameExecutor(org.jboss.threads.DirectExecutor, java.lang.String)
 * @see JBossExecutors#threadNameNotateExecutor(org.jboss.threads.DirectExecutor, java.lang.String)
 * @see JBossExecutors#exceptionLoggingExecutor(DirectExecutor, org.jboss.logging.Logger)
 * @see JBossExecutors#exceptionLoggingExecutor(DirectExecutor)
 * @see JBossExecutors#resettingExecutor(org.jboss.threads.DirectExecutor)
 */
public interface DirectExecutor extends Executor {

    /**
     * Executes the given command in the calling thread.
     *
     * @param command the runnable task
     *
     * @throws java.util.concurrent.RejectedExecutionException if this task cannot be accepted for execution
     * @throws NullPointerException if command is null
     */
    void execute(Runnable command);
}
