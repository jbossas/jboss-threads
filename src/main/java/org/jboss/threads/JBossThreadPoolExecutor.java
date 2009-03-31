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

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;

/**
 *
 */
public final class JBossThreadPoolExecutor extends ThreadPoolExecutor implements ThreadPoolExecutorMBean {

    private final String name;
    private final AtomicInteger rejectCount = new AtomicInteger();

    public JBossThreadPoolExecutor(final String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        this.name = name;
    }

    public JBossThreadPoolExecutor(final String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.name = name;
    }

    public JBossThreadPoolExecutor(final String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        setRejectedExecutionHandler(handler);
        this.name = name;
    }

    public JBossThreadPoolExecutor(final String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        setRejectedExecutionHandler(handler);
        this.name = name;
    }

    public void stop() {
        shutdown();
        try {
            awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            // todo log if fails?
        } catch (InterruptedException e) {
            // todo log it?
            Thread.currentThread().interrupt();
        }
    }

    public void destroy() {
        // todo is this the right behavior?
        shutdownNow();
        try {
            awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            // todo log if fails?
        } catch (InterruptedException e) {
            // todo log it?
            Thread.currentThread().interrupt();
        }
    }

    private static final Method GET_ALLOW_CORE_THREAD_TIMEOUT;
    private static final Method SET_ALLOW_CORE_THREAD_TIMEOUT;

    static {
        Method method = null;
        try {
            method = ThreadPoolExecutor.class.getMethod("allowsCoreThreadTimeOut");
        } catch (NoSuchMethodException e) {
        }
        GET_ALLOW_CORE_THREAD_TIMEOUT = method;
        try {
            method = ThreadPoolExecutor.class.getMethod("allowCoreThreadTimeOut", boolean.class);
        } catch (NoSuchMethodException e) {
        }
        SET_ALLOW_CORE_THREAD_TIMEOUT = method;
    }

    public String getName() {
        return name;
    }

    public boolean isAllowCoreThreadTimeout() {
        final Method method = GET_ALLOW_CORE_THREAD_TIMEOUT;
        try {
            return method != null ? ((Boolean) method.invoke(this)).booleanValue() : false;
        } catch (Exception e) {
            return false;
        }
    }

    public void setAllowCoreThreadTimeout(final boolean allow) {
        final Method method = SET_ALLOW_CORE_THREAD_TIMEOUT;
        try {
            if (method != null) {
                method.invoke(this, Boolean.valueOf(allow));
                return;
            }
        } catch (Exception e) {
        }
        throw new UnsupportedOperationException();
    }

    public int getMaxPoolSize() {
        return getMaximumPoolSize();
    }

    public void setMaxPoolSize(final int newSize) {
        setMaximumPoolSize(newSize);
    }

    public long getKeepAliveTime() {
        return getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    public void setKeepAliveTime(final long milliseconds) {
        setKeepAliveTime(milliseconds, TimeUnit.MILLISECONDS);
    }

    public int getCurrentPoolSize() {
        return getPoolSize();
    }

    public int getRejectedCount() {
        return rejectCount.get();
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return ((CountingRejectHandler)super.getRejectedExecutionHandler()).getDelegate();
    }

    public void setRejectedExecutionHandler(final RejectedExecutionHandler handler) {
        super.setRejectedExecutionHandler(new CountingRejectHandler(handler));
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
            delegate.rejectedExecution(r, executor);
        }
    }
}
