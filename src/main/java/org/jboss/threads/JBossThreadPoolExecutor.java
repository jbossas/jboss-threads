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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.threads.management.BoundedQueueThreadPoolExecutorMBean;

/**
 * @deprecated Use {@link EnhancedQueueExecutor} instead.
 */
@Deprecated
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
        allowCoreThreadTimeOut(allow);
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
