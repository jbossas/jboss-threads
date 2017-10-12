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
