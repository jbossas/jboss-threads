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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
class DelegatingBlockingExecutorService extends AbstractExecutorService implements BlockingExecutorService {
    private final BlockingExecutor delegate;

    DelegatingBlockingExecutorService(final BlockingExecutor delegate) {
        this.delegate = delegate;
    }

    public void execute(final Runnable command) {
        delegate.execute(command);
    }

    public boolean isShutdown() {
        // container managed executors are never shut down from the application's perspective
        return false;
    }

    public boolean isTerminated() {
        // container managed executors are never shut down from the application's perspective
        return false;
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return false;
    }

    public void shutdown() {
        throw new SecurityException("shutdown() not allowed on container-managed executor");
    }

    public List<Runnable> shutdownNow() {
        throw new SecurityException("shutdownNow() not allowed on container-managed executor");
    }

    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        delegate.executeBlocking(task);
    }

    public void executeBlocking(final Runnable task, final long timeout, final TimeUnit unit) throws RejectedExecutionException, InterruptedException {
        delegate.executeBlocking(task, timeout, unit);
    }

    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        delegate.executeNonBlocking(task);
    }

    public static ExecutorService directExecutorService() {
        return new DelegatingExecutorService(JBossExecutors.directExecutor());
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
