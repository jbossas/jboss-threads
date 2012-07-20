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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An executor that simply delegates to another executor.  Use instances of this class to hide extra methods on
 * another executor.
 */
class DelegatingBlockingExecutor implements BlockingExecutor {
    private final BlockingExecutor delegate;

    DelegatingBlockingExecutor(final BlockingExecutor delegate) {
        this.delegate = delegate;
    }

    protected BlockingExecutor getDelegate() {
        return delegate;
    }

    public void execute(final Runnable command) {
        delegate.execute(command);
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

    @Override
    public long getNumberOfFreeThreads() {
        return delegate.getNumberOfFreeThreads();
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
