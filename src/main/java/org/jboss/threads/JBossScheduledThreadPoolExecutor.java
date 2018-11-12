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

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class JBossScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private final AtomicInteger rejectCount = new AtomicInteger();
    private final Runnable terminationTask;

    public JBossScheduledThreadPoolExecutor(int corePoolSize, final Runnable terminationTask) {
        super(corePoolSize);
        this.terminationTask = terminationTask;
        setRejectedExecutionHandler(super.getRejectedExecutionHandler());
    }

    public JBossScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, final Runnable terminationTask) {
        super(corePoolSize, threadFactory);
        this.terminationTask = terminationTask;
        setRejectedExecutionHandler(super.getRejectedExecutionHandler());
    }

    public JBossScheduledThreadPoolExecutor(int corePoolSize, RejectedExecutionHandler handler, final Runnable terminationTask) {
        super(corePoolSize);
        this.terminationTask = terminationTask;
        setRejectedExecutionHandler(handler);
    }

    public JBossScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler, final Runnable terminationTask) {
        super(corePoolSize, threadFactory);
        this.terminationTask = terminationTask;
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
    public int getQueueSize() {
        return this.getQueue().size();
    }

    protected void terminated() {
        terminationTask.run();
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
                throw Messages.msg.shutDownInitiated();
            }
            delegate.rejectedExecution(r, executor);
        }
    }
}
