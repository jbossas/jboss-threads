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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.List;

/**
 * An implementation of {@code ExecutorService} that delegates to the real executor, while disallowing termination.
 */
class DelegatingExecutorService extends AbstractExecutorService implements ExecutorService {
    private final Executor delegate;

    DelegatingExecutorService(final Executor delegate) {
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
        unit.sleep(timeout);
        return false;
    }

    public void shutdown() {
        throw new SecurityException("shutdown() not allowed on container-managed executor");
    }

    public List<Runnable> shutdownNow() {
        throw new SecurityException("shutdownNow() not allowed on container-managed executor");
    }

    public static ExecutorService directExecutorService() {
        return new DelegatingExecutorService(JBossExecutors.directExecutor());
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
