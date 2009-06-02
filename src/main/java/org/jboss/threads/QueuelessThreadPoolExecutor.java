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
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Create a queueless thread pool executor.
 */
public final class QueuelessThreadPoolExecutor extends ThreadPoolExecutor implements ConfigurableExecutor {

    /**
     * Construct a new instance.
     *
     * @param keepAliveTime the thread keepalive time
     * @param unit the time unit
     */
    public QueuelessThreadPoolExecutor(final long keepAliveTime, final TimeUnit unit) {
        super(0, Integer.MAX_VALUE, keepAliveTime, unit, new SynchronousQueue<Runnable>());
    }

    /**
     * Execute a task.  Tasks on a queueless executor are never rejected (unless the pool is shut down).
     *
     * @param task the task to execute
     * @param taskExecutor the direct executor to use, or {@code null} to execute the task as-is
     * @param policy the rejection policy (ignored)
     * @param handoffExecutor the handoff executor (ignored)
     */
    public void execute(final Runnable task, final DirectExecutor taskExecutor, final RejectionPolicy policy, final Executor handoffExecutor) {
        execute(taskExecutor == null || taskExecutor == JBossExecutors.directExecutor() ? task : JBossExecutors.executorTask(taskExecutor, task));
    }
}
