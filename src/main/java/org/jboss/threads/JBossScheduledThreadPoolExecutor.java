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

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.threads.management.ThreadPoolExecutorMBean;

public final class JBossScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor implements ThreadPoolExecutorMBean, ShutdownListenable {

    private final SimpleShutdownListenable shutdownListenable = new SimpleShutdownListenable();
    private final AtomicInteger rejectCount = new AtomicInteger();

    public JBossScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize);
        setRejectedExecutionHandler(super.getRejectedExecutionHandler());
    }

    public JBossScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
        setRejectedExecutionHandler(super.getRejectedExecutionHandler());
    }

    public JBossScheduledThreadPoolExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        super(corePoolSize);
        setRejectedExecutionHandler(handler);
    }

    public JBossScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory);
        setRejectedExecutionHandler(handler);
    }

    public long getKeepAliveTime() {
        return getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    public void setKeepAliveTime(final long milliseconds) {
        super.setKeepAliveTime(milliseconds, TimeUnit.MILLISECONDS);
        super.allowCoreThreadTimeOut(milliseconds < Long.MAX_VALUE);
    }

    public void setKeepAliveTime(final long time, final TimeUnit unit) {
        super.setKeepAliveTime(time, unit);
        super.allowCoreThreadTimeOut(time < Long.MAX_VALUE);
    }

    public int getRejectedCount() {
        return rejectCount.get();
    }

    public int getCurrentThreadCount() {
        return getActiveCount();
    }

    public int getLargestThreadCount() {
        return getLargestPoolSize();
    }

    public int getMaxThreads() {
        return getCorePoolSize();
    }

    public void setMaxThreads(final int newSize) {
        setCorePoolSize(newSize);
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return ((CountingRejectHandler)super.getRejectedExecutionHandler()).getDelegate();
    }

    public void setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
        super.setRejectedExecutionHandler(new CountingRejectHandler(handler));
    }

    /** {@inheritDoc} */
    public <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        shutdownListenable.addShutdownListener(shutdownListener, attachment);
    }

    protected void terminated() {
        shutdownListenable.shutdown();
    }

    private final class CountingRejectHandler implements RejectedExecutionHandler {
        private final RejectedExecutionHandler delegate;

        public CountingRejectHandler(final RejectedExecutionHandler delegate) {
            this.delegate = delegate;
        }

        public RejectedExecutionHandler getDelegate() {
            return delegate;
        }

        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
            rejectCount.incrementAndGet();
            if (isShutdown()) {
                throw new StoppedExecutorException();
            }
            delegate.rejectedExecution(r, executor);
        }
    }

    @Override
    public int getQueueSize() {
        return getQueue().size();
    }
}
