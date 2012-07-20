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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.threads.management.BoundedQueueThreadPoolExecutorMBean;

/**
 *
 */
public final class JBossThreadPoolExecutor extends ThreadPoolExecutor implements BlockingExecutorService, BoundedQueueThreadPoolExecutorMBean, ShutdownListenable {

    private final SimpleShutdownListenable shutdownListenable = new SimpleShutdownListenable();
    private final AtomicInteger rejectCount = new AtomicInteger();

    public JBossThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        setRejectedExecutionHandler(super.getRejectedExecutionHandler());
    }

    public JBossThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        setRejectedExecutionHandler(super.getRejectedExecutionHandler());
    }

    public JBossThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        setRejectedExecutionHandler(handler);
    }

    public JBossThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        setRejectedExecutionHandler(handler);
    }

    public void execute(final Runnable task) {
        super.execute(task);
    }

    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        super.execute(task);
    }

    public void executeBlocking(final Runnable task, final long timeout, final TimeUnit unit) throws RejectedExecutionException, InterruptedException {
        super.execute(task);
    }

    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        super.execute(task);
    }

    public int getLargestThreadCount() {
        return super.getLargestPoolSize();
    }

    public boolean isAllowCoreThreadTimeout() {
        return allowsCoreThreadTimeOut();
    }

    public void setAllowCoreThreadTimeout(final boolean allow) {
        setAllowCoreThreadTimeout(allow);
    }

    public int getMaxThreads() {
        return getMaximumPoolSize();
    }

    public void setMaxThreads(final int newSize) {
        setMaximumPoolSize(newSize);
    }

    public int getCoreThreads() {
        return getCorePoolSize();
    }

    public void setCoreThreads(final int newSize) {
        setCorePoolSize(newSize);
    }

    public long getKeepAliveTime() {
        return getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    public void setKeepAliveTime(final long milliseconds) {
        setKeepAliveTime(milliseconds, TimeUnit.MILLISECONDS);
    }

    public long getNumberOfFreeThreads() {
        return getMaximumPoolSize() - getActiveCount();
    }

    public int getCurrentThreadCount() {
        return getPoolSize();
    }

    public int getRejectedCount() {
        return rejectCount.get();
    }

    /** {@inheritDoc} */
    public int getQueueSize() {
        return this.getQueue().size();
    }

    public boolean isBlocking() {
        return false;
    }

    public void setBlocking(final boolean blocking) {
        throw new UnsupportedOperationException();
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
}
