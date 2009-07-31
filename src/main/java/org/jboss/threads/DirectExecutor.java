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

import java.util.concurrent.Executor;

/**
 * A direct executor.  Such an executor is <b>required</b> to run the given task in the current thread rather than
 * delegate to a thread pool.
 *
 * @see JBossExecutors#directExecutor()
 * @see JBossExecutors#rejectingExecutor()
 * @see JBossExecutors#discardingExecutor()
 * @see JBossExecutors#privilegedExecutor(org.jboss.threads.DirectExecutor, java.security.AccessControlContext)
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
