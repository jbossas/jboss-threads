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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.threads.management.ManageableThreadPoolExecutorService;
import org.jboss.threads.management.StandardThreadPoolMXBean;
import org.wildfly.common.Assert;

/**
 * A version of {@link ThreadPoolExecutor} which implements {@link ManageableThreadPoolExecutorService} in order to allow
 * opting out of using {@link EnhancedQueueExecutor}.
 */
public final class ManagedThreadPoolExecutor extends ThreadPoolExecutor implements ManageableThreadPoolExecutorService {
    private final Runnable terminationTask;
    private final StandardThreadPoolMXBean mxBean = new MXBeanImpl();
    private volatile Executor handoffExecutor = JBossExecutors.rejectingExecutor();
    private static final RejectedExecutionHandler HANDLER = new RejectedExecutionHandler() {
        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
            ((ManagedThreadPoolExecutor) executor).reject(r);
        }
    };

    public ManagedThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final Runnable terminationTask) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, HANDLER);
        this.terminationTask = terminationTask;
    }

    public ManagedThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final Runnable terminationTask) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, HANDLER);
        this.terminationTask = terminationTask;
    }

    public ManagedThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final Executor handoffExecutor, final Runnable terminationTask) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, HANDLER);
        this.terminationTask = terminationTask;
        this.handoffExecutor = handoffExecutor;
    }

    public ManagedThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final Executor handoffExecutor, final Runnable terminationTask) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, HANDLER);
        this.terminationTask = terminationTask;
        this.handoffExecutor = handoffExecutor;
    }

    public StandardThreadPoolMXBean getThreadPoolMXBean() {
        return mxBean;
    }

    public Executor getHandoffExecutor() {
        return handoffExecutor;
    }

    public void setHandoffExecutor(final Executor handoffExecutor) {
        Assert.checkNotNullParam("handoffExecutor", handoffExecutor);
        this.handoffExecutor = handoffExecutor;
        super.setRejectedExecutionHandler(HANDLER);
    }

    void reject(Runnable r) {
        handoffExecutor.execute(r);
    }

    protected void terminated() {
        terminationTask.run();
    }

    class MXBeanImpl implements StandardThreadPoolMXBean {
        public float getGrowthResistance() {
            return 1.0f;
        }

        public void setGrowthResistance(final float value) {
            // ignored
        }

        public boolean isGrowthResistanceSupported() {
            return false;
        }

        public int getCorePoolSize() {
            return ManagedThreadPoolExecutor.this.getCorePoolSize();
        }

        public void setCorePoolSize(final int corePoolSize) {
            ManagedThreadPoolExecutor.this.setCorePoolSize(corePoolSize);
        }

        public boolean isCorePoolSizeSupported() {
            return true;
        }

        public boolean prestartCoreThread() {
            return ManagedThreadPoolExecutor.this.prestartCoreThread();
        }

        public int prestartAllCoreThreads() {
            return ManagedThreadPoolExecutor.this.prestartAllCoreThreads();
        }

        public boolean isCoreThreadPrestartSupported() {
            return true;
        }

        public int getMaximumPoolSize() {
            return ManagedThreadPoolExecutor.this.getMaximumPoolSize();
        }

        public void setMaximumPoolSize(final int maxPoolSize) {
            ManagedThreadPoolExecutor.this.setMaximumPoolSize(maxPoolSize);
        }

        public int getPoolSize() {
            return ManagedThreadPoolExecutor.this.getPoolSize();
        }

        public int getLargestPoolSize() {
            return ManagedThreadPoolExecutor.this.getLargestPoolSize();
        }

        public int getActiveCount() {
            return ManagedThreadPoolExecutor.this.getActiveCount();
        }

        public boolean isAllowCoreThreadTimeOut() {
            return ManagedThreadPoolExecutor.this.allowsCoreThreadTimeOut();
        }

        public void setAllowCoreThreadTimeOut(final boolean value) {
            ManagedThreadPoolExecutor.this.allowCoreThreadTimeOut(value);
        }

        public long getKeepAliveTimeSeconds() {
            return ManagedThreadPoolExecutor.this.getKeepAliveTime(TimeUnit.SECONDS);
        }

        public void setKeepAliveTimeSeconds(final long seconds) {
            ManagedThreadPoolExecutor.this.setKeepAliveTime(seconds, TimeUnit.SECONDS);
        }

        public int getMaximumQueueSize() {
            return 0;
        }

        public void setMaximumQueueSize(final int size) {
        }

        public int getQueueSize() {
            return ManagedThreadPoolExecutor.this.getQueue().size();
        }

        public int getLargestQueueSize() {
            return 0;
        }

        public boolean isQueueBounded() {
            return false;
        }

        public boolean isQueueSizeModifiable() {
            return false;
        }

        public boolean isShutdown() {
            return ManagedThreadPoolExecutor.this.isShutdown();
        }

        public boolean isTerminating() {
            return ManagedThreadPoolExecutor.this.isTerminating();
        }

        public boolean isTerminated() {
            return ManagedThreadPoolExecutor.this.isTerminated();
        }

        public long getSubmittedTaskCount() {
            return ManagedThreadPoolExecutor.this.getTaskCount();
        }

        public long getRejectedTaskCount() {
            return 0;
        }

        public long getCompletedTaskCount() {
            return ManagedThreadPoolExecutor.this.getCompletedTaskCount();
        }
    }
}
