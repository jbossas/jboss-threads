/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * An executor for long-running tasks which limits the total concurrency over a delegate thread pool.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class LimitedExecutor implements Executor {
    private final Executor executor;
    private final Thread.UncaughtExceptionHandler handler;
    private final int concurrencyLimit;
    private final Runner runner;

    public LimitedExecutor(final Executor executor, final Thread.UncaughtExceptionHandler handler, final int concurrencyLimit) {
        this.executor = executor;
        this.handler = handler;
        this.concurrencyLimit = concurrencyLimit;
        this.runner = new Runner();
    }

    public LimitedExecutor(final Executor executor, final int concurrencyLimit) {
        this(executor, null, concurrencyLimit);
    }

    public void execute(final Runnable command) throws RejectedExecutionException {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }
        runner.add(command);
    }

    @SuppressWarnings("serial")
    class Runner extends ArrayDeque<Runnable> implements Runnable {
        private int runCount;

        public synchronized boolean add(final Runnable runnable) {
            int runCount = this.runCount;
            if (runCount < concurrencyLimit) {
                executor.execute(this);
                this.runCount = runCount + 1;
            }
            return super.add(runnable);
        }

        public void run() {
            Runnable task;
            for (;;) {
                synchronized (this) {
                    task = poll();
                    if (task == null) {
                        runCount--;
                        return;
                    }
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    final Thread.UncaughtExceptionHandler handler = LimitedExecutor.this.handler;
                    if (handler != null) try {
                        handler.uncaughtException(Thread.currentThread(), t);
                    } catch (Throwable ignored) {}
                }
            }
        }

        public String toString() {
            return String.format("Task runner for %s", LimitedExecutor.this);
        }
    }

    public String toString() {
        return String.format("Limited executor (%d tasks) over %s", Integer.valueOf(concurrencyLimit), executor);
    }
}
