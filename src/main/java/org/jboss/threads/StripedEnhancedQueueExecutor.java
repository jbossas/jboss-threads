/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import static org.jboss.threads.EnhancedQueueExecutor.DEFAULT_HANDLER;
import static org.jboss.threads.EnhancedQueueExecutor.REGISTER_MBEAN;
import static org.jboss.threads.EnhancedQueueExecutor.TS_THREAD_CNT_MASK;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.threads.management.ManageableThreadPoolExecutorService;
import org.jboss.threads.management.StandardThreadPoolMXBean;
import org.wildfly.common.Assert;

/**
 * A striped version of {@link EnhancedQueueExecutor}.
 */
public final class StripedEnhancedQueueExecutor extends AbstractExecutorService implements ManageableThreadPoolExecutorService {
    private final List<EnhancedQueueExecutor> executors;
    volatile Runnable terminationTask;

    StripedEnhancedQueueExecutor(Builder builder) {
        int count = builder.getStripes();
        final AtomicInteger cnt = new AtomicInteger(count);
        final Runnable stripeStopTask = new Runnable() {
            public void run() {
                if (cnt.decrementAndGet() == 0) {
                    Runnable task = terminationTask;
                    terminationTask = null;
                    try {
                        task.run();
                    } catch (Throwable ignored) {
                    }
                }
            }
        };
        int coreSize = builder.coreSize;
        coreSize = (coreSize % count == 0 ? 0 : 1) + coreSize / count;
        int maxSize = builder.maxSize;
        maxSize = (maxSize % count == 0 ? 0 : 1) + maxSize / count;
        int maxQueueSize = builder.maxQueueSize;
        if (maxQueueSize != Integer.MAX_VALUE) {
            maxQueueSize = (maxQueueSize % count == 0 ? 0 : 1) + maxQueueSize / count;
        }
        final List<EnhancedQueueExecutor> executors = new ArrayList<>(count);
        for (int i = 0; i < count; i ++) {
            EnhancedQueueExecutor.Builder b = new EnhancedQueueExecutor.Builder();
            b.setTerminationTask(stripeStopTask);
            b.setThreadFactory(builder.threadFactory);
            b.setCorePoolSize(coreSize);
            b.setMaximumPoolSize(maxSize);
            b.setExceptionHandler(builder.exceptionHandler);
            b.setGrowthResistance(builder.growthResistance);
            b.allowCoreThreadTimeOut(builder.allowCoreTimeOut);
            b.setMaximumQueueSize(maxQueueSize);
            b.setHandoffExecutor(builder.handoffExecutor);
            b.setMBeanName(builder.mBeanName + "_" + i);
            b.setKeepAliveTime(builder.keepAliveTime);
            executors.add(b.build());
        }
        this.executors = executors;
    }

    public StandardThreadPoolMXBean getThreadPoolMXBean() {
        return new MXBean();
    }

    public int getStripes() {
        return executors.size();
    }

    public void shutdown() {
        for (EnhancedQueueExecutor executor : executors) {
            executor.shutdown();
        }
    }

    public List<Runnable> shutdownNow() {
        List<Runnable> list = null;
        for (EnhancedQueueExecutor executor : executors) {
            List<Runnable> tasks = executor.shutdownNow();
            if (! tasks.isEmpty()) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.addAll(tasks);
            }
        }
        return list == null ? Collections.emptyList() : list;
    }

    public boolean isShutdown() {
        for (EnhancedQueueExecutor executor : executors) {
            if (executor.isShutdown()) {
                return true;
            }
        }
        return false;
    }

    public boolean isTerminating() {
        for (EnhancedQueueExecutor executor : executors) {
            if (executor.isTerminating()) {
                return true;
            }
        }
        return false;
    }

    public boolean isTerminated() {
        for (EnhancedQueueExecutor executor : executors) {
            if (! executor.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        long start = System.nanoTime();
        long now;
        for (EnhancedQueueExecutor executor : executors) {
            executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            now = System.nanoTime();
            remaining = Math.max(0, remaining - Math.max(0, now - start));

        }
        return false;
    }

    private EnhancedQueueExecutor chooseDelegate() {
        return executors.get(ThreadLocalRandom.current().nextInt(executors.size()));
    }

    public <T> Future<T> submit(final Callable<T> task) {
        return chooseDelegate().submit(task);
    }

    public <T> Future<T> submit(final Runnable task, final T result) {
        return chooseDelegate().submit(task, result);
    }

    public Future<?> submit(final Runnable task) {
        return chooseDelegate().submit(task);
    }

    public void execute(final Runnable command) {
        chooseDelegate().execute(command);
    }

    // =======================================================
    // Builder
    // =======================================================

    /**
     * The builder class for an {@code EnhancedQueueExecutor}.  All the fields are initialized to sensible defaults for
     * a small thread pool.
     */
    public static final class Builder {
        private int stripes = 2;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
        private Runnable terminationTask = NullRunnable.getInstance();
        private Executor handoffExecutor = DEFAULT_HANDLER;
        private Thread.UncaughtExceptionHandler exceptionHandler = JBossExecutors.loggingExceptionHandler();
        private int coreSize = 16;
        private int maxSize = 64;
        private Duration keepAliveTime = Duration.ofSeconds(30);
        private float growthResistance;
        private boolean allowCoreTimeOut;
        private int maxQueueSize = Integer.MAX_VALUE;
        private boolean registerMBean = REGISTER_MBEAN;
        private String mBeanName;

        /**
         * Construct a new instance.
         */
        public Builder() {}

        /**
         * Get the number of stripes.
         *
         * @return the number of stripes
         */
        public int getStripes() {
            return stripes;
        }

        /**
         * Set the number of stripes.
         *
         * @param stripes the number of stripes
         * @return this builder
         */
        public Builder setStripes(final int stripes) {
            Assert.checkMinimumParameter("stripes", 2, stripes);
            this.stripes = stripes;
            return this;
        }

        /**
         * Get the configured thread factory.
         *
         * @return the configured thread factory (not {@code null})
         */
        public ThreadFactory getThreadFactory() {
            return threadFactory;
        }

        /**
         * Set the configured thread factory.
         *
         * @param threadFactory the configured thread factory (must not be {@code null})
         * @return this builder
         */
        public Builder setThreadFactory(final ThreadFactory threadFactory) {
            Assert.checkNotNullParam("threadFactory", threadFactory);
            this.threadFactory = threadFactory;
            return this;
        }

        /**
         * Get the termination task.  By default, an empty {@code Runnable} is used.
         *
         * @return the termination task (not {@code null})
         */
        public Runnable getTerminationTask() {
            return terminationTask;
        }

        /**
         * Set the termination task.
         *
         * @param terminationTask the termination task (must not be {@code null})
         * @return this builder
         */
        public Builder setTerminationTask(final Runnable terminationTask) {
            Assert.checkNotNullParam("terminationTask", terminationTask);
            this.terminationTask = terminationTask;
            return this;
        }

        /**
         * Get the core pool size.  This is the size below which new threads will always be created if no idle threads
         * are available.  If the pool size reaches the core size but has not yet reached the maximum size, a resistance
         * factor will be applied to each task submission which determines whether the task should be queued or a new
         * thread started.
         *
         * @return the core pool size
         * @see EnhancedQueueExecutor#getCorePoolSize()
         */
        public int getCorePoolSize() {
            return coreSize;
        }

        /**
         * Set the core pool size.  If the configured maximum pool size is less than the configured core size, the
         * core size will be reduced to match the maximum size when the thread pool is constructed.
         *
         * @param coreSize the core pool size (must be greater than or equal to 0, and less than 2^20)
         * @return this builder
         * @see EnhancedQueueExecutor#setCorePoolSize(int)
         */
        public Builder setCorePoolSize(final int coreSize) {
            Assert.checkMinimumParameter("coreSize", 0, coreSize);
            Assert.checkMaximumParameter("coreSize", TS_THREAD_CNT_MASK, coreSize);
            this.coreSize = coreSize;
            return this;
        }

        /**
         * Get the maximum pool size.  This is the absolute upper limit to the size of the thread pool.
         *
         * @return the maximum pool size
         * @see EnhancedQueueExecutor#getMaximumPoolSize()
         */
        public int getMaximumPoolSize() {
            return maxSize;
        }

        /**
         * Set the maximum pool size.  If the configured maximum pool size is less than the configured core size, the
         * core size will be reduced to match the maximum size when the thread pool is constructed.
         *
         * @param maxSize the maximum pool size (must be greater than or equal to 0, and less than 2^20)
         * @return this builder
         * @see EnhancedQueueExecutor#setMaximumPoolSize(int)
         */
        public Builder setMaximumPoolSize(final int maxSize) {
            Assert.checkMinimumParameter("maxSize", 0, maxSize);
            Assert.checkMaximumParameter("maxSize", TS_THREAD_CNT_MASK, maxSize);
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Get the thread keep-alive time.  This is the amount of time (in the configured time unit) that idle threads
         * will wait for a task before exiting.
         *
         * @return the thread keep-alive time duration
         */
        public Duration getKeepAliveTime() {
            return keepAliveTime;
        }

        /**
         * Get the thread keep-alive time.  This is the amount of time (in the configured time unit) that idle threads
         * will wait for a task before exiting.
         *
         * @param keepAliveUnits the time keepAliveUnits of the keep-alive time (must not be {@code null})
         * @return the thread keep-alive time
         * @see EnhancedQueueExecutor#getKeepAliveTime(TimeUnit)
         * @deprecated Use {@link #getKeepAliveTime()} instead.
         */
        @Deprecated
        public long getKeepAliveTime(TimeUnit keepAliveUnits) {
            Assert.checkNotNullParam("keepAliveUnits", keepAliveUnits);
            final long secondsPart = keepAliveUnits.convert(keepAliveTime.getSeconds(), TimeUnit.SECONDS);
            final long nanoPart = keepAliveUnits.convert(keepAliveTime.getNano(), TimeUnit.NANOSECONDS);
            final long sum = secondsPart + nanoPart;
            return sum < 0 ? Long.MAX_VALUE : sum;
        }

        /**
         * Set the thread keep-alive time.
         *
         * @param keepAliveTime the thread keep-alive time (must not be {@code null})
         */
        public Builder setKeepAliveTime(final Duration keepAliveTime) {
            Assert.checkNotNullParam("keepAliveTime", keepAliveTime);
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        /**
         * Set the thread keep-alive time.
         *
         * @param keepAliveTime the thread keep-alive time (must be greater than 0)
         * @param keepAliveUnits the time keepAliveUnits of the keep-alive time (must not be {@code null})
         * @return this builder
         * @see EnhancedQueueExecutor#setKeepAliveTime(long, TimeUnit)
         * @deprecated Use {@link #setKeepAliveTime(Duration)} instead.
         */
        @Deprecated
        public Builder setKeepAliveTime(final long keepAliveTime, final TimeUnit keepAliveUnits) {
            Assert.checkMinimumParameter("keepAliveTime", 1L, keepAliveTime);
            Assert.checkNotNullParam("keepAliveUnits", keepAliveUnits);
            this.keepAliveTime = Duration.of(keepAliveTime, JDKSpecific.timeToTemporal(keepAliveUnits));
            return this;
        }

        /**
         * Get the thread pool growth resistance.  This is the average fraction of submitted tasks that will be enqueued (instead
         * of causing a new thread to start) when there are no idle threads and the pool size is equal to or greater than
         * the core size (but still less than the maximum size).  A value of {@code 0.0} indicates that tasks should
         * not be enqueued until the pool is completely full; a value of {@code 1.0} indicates that tasks should always
         * be enqueued until the queue is completely full.
         *
         * @return the thread pool growth resistance
         * @see EnhancedQueueExecutor#getGrowthResistance()
         */
        public float getGrowthResistance() {
            return growthResistance;
        }

        /**
         * Set the thread pool growth resistance.
         *
         * @param growthResistance the thread pool growth resistance (must be in the range {@code 0.0f ≤ n ≤ 1.0f})
         * @return this builder
         * @see #getGrowthResistance()
         * @see EnhancedQueueExecutor#setGrowthResistance(float)
         */
        public Builder setGrowthResistance(final float growthResistance) {
            Assert.checkMinimumParameter("growthResistance", 0.0f, growthResistance);
            Assert.checkMaximumParameter("growthResistance", 1.0f, growthResistance);
            this.growthResistance = growthResistance;
            return this;
        }

        /**
         * Determine whether core threads are allowed to time out.  A "core thread" is defined as any thread in the pool
         * when the pool size is below the pool's {@linkplain #getCorePoolSize() core pool size}.
         *
         * @return {@code true} if core threads are allowed to time out, {@code false} otherwise
         * @see EnhancedQueueExecutor#allowsCoreThreadTimeOut()
         */
        public boolean allowsCoreThreadTimeOut() {
            return allowCoreTimeOut;
        }

        /**
         * Establish whether core threads are allowed to time out.  A "core thread" is defined as any thread in the pool
         * when the pool size is below the pool's {@linkplain #getCorePoolSize() core pool size}.
         *
         * @param allowCoreTimeOut {@code true} if core threads are allowed to time out, {@code false} otherwise
         * @return this builder
         * @see EnhancedQueueExecutor#allowCoreThreadTimeOut(boolean)
         */
        public Builder allowCoreThreadTimeOut(final boolean allowCoreTimeOut) {
            this.allowCoreTimeOut = allowCoreTimeOut;
            return this;
        }

        /**
         * Get the maximum queue size.  If the queue is full and a task cannot be immediately accepted, rejection will result.
         *
         * @return the maximum queue size
         * @see EnhancedQueueExecutor#getMaximumQueueSize()
         */
        public int getMaximumQueueSize() {
            return maxQueueSize;
        }

        /**
         * Set the maximum queue size.
         *
         * @param maxQueueSize the maximum queue size (must be ≥ 0)
         * @return this builder
         * @see EnhancedQueueExecutor#setMaximumQueueSize(int)
         */
        public Builder setMaximumQueueSize(final int maxQueueSize) {
            Assert.checkMinimumParameter("maxQueueSize", 0, maxQueueSize);
            Assert.checkMaximumParameter("maxQueueSize", Integer.MAX_VALUE, maxQueueSize);
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        /**
         * Get the handoff executor.
         *
         * @return the handoff executor (not {@code null})
         */
        public Executor getHandoffExecutor() {
            return handoffExecutor;
        }

        /**
         * Set the handoff executor.
         *
         * @param handoffExecutor the handoff executor (must not be {@code null})
         * @return this builder
         */
        public Builder setHandoffExecutor(final Executor handoffExecutor) {
            Assert.checkNotNullParam("handoffExecutor", handoffExecutor);
            this.handoffExecutor = handoffExecutor;
            return this;
        }

        /**
         * Get the uncaught exception handler.
         *
         * @return the uncaught exception handler (not {@code null})
         */
        public Thread.UncaughtExceptionHandler getExceptionHandler() {
            return exceptionHandler;
        }

        /**
         * Set the uncaught exception handler.
         *
         * @param exceptionHandler the uncaught exception handler (must not be {@code null})
         * @return this builder
         */
        public Builder setExceptionHandler(final Thread.UncaughtExceptionHandler exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        /**
         * Construct the executor from the configured parameters.
         *
         * @return the executor, which will be active and ready to accept tasks (not {@code null})
         */
        public StripedEnhancedQueueExecutor build() {
            return new StripedEnhancedQueueExecutor(this);
        }

        /**
         * Determine whether an MBean should automatically be registered for this pool.
         *
         * @return {@code true} if an MBean is to be auto-registered; {@code false} otherwise
         */
        public boolean isRegisterMBean() {
            return registerMBean;
        }

        /**
         * Establish whether an MBean should automatically be registered for this pool.
         *
         * @param registerMBean {@code true} if an MBean is to be auto-registered; {@code false} otherwise
         * @return this builder
         */
        public Builder setRegisterMBean(final boolean registerMBean) {
            this.registerMBean = registerMBean;
            return this;
        }

        /**
         * Get the overridden MBean name.
         *
         * @return the overridden MBean name, or {@code null} if a default name should be generated
         */
        public String getMBeanName() {
            return mBeanName;
        }

        /**
         * Set the overridden MBean name.
         *
         * @param mBeanName the overridden MBean name, or {@code null} if a default name should be generated
         * @return this builder
         */
        public Builder setMBeanName(final String mBeanName) {
            this.mBeanName = mBeanName;
            return this;
        }
    }

    class MXBean implements StandardThreadPoolMXBean {
        MXBean() {
        }

        public float getGrowthResistance() {
            // they all should be the same
            return chooseDelegate().getGrowthResistance();
        }

        public void setGrowthResistance(final float value) {
            for (EnhancedQueueExecutor executor : executors) {
                executor.setGrowthResistance(value);
            }
        }

        public boolean isGrowthResistanceSupported() {
            return true;
        }

        public int getCorePoolSize() {
            return chooseDelegate().getCorePoolSize() * executors.size();
        }

        public void setCorePoolSize(int corePoolSize) {
            int count = executors.size();
            boolean bump = corePoolSize % count != 0;
            int finalSize = corePoolSize / count + (bump ? 1 : 0);
            for (EnhancedQueueExecutor executor : executors) {
                executor.setCorePoolSize(finalSize);
            }
        }

        public boolean isCorePoolSizeSupported() {
            return true;
        }

        public boolean prestartCoreThread() {
            return chooseDelegate().prestartCoreThread();
        }

        public int prestartAllCoreThreads() {
            int cnt = 0;
            for (EnhancedQueueExecutor executor : executors) {
                cnt += executor.prestartAllCoreThreads();
            }
            return cnt;
        }

        public boolean isCoreThreadPrestartSupported() {
            return true;
        }

        public int getMaximumPoolSize() {
            return chooseDelegate().getMaximumPoolSize() * executors.size();
        }

        public void setMaximumPoolSize(final int maxPoolSize) {
            int count = executors.size();
            boolean bump = maxPoolSize % count != 0;
            int finalSize = maxPoolSize / count + (bump ? 1 : 0);
            for (EnhancedQueueExecutor executor : executors) {
                executor.setMaximumPoolSize(finalSize);
            }
        }

        public int getPoolSize() {
            int cnt = 0;
            for (EnhancedQueueExecutor executor : executors) {
                cnt += executor.getPoolSize();
            }
            return cnt;
        }

        public int getLargestPoolSize() {
            int cnt = 0;
            for (EnhancedQueueExecutor executor : executors) {
                cnt += executor.getLargestPoolSize();
            }
            return cnt;
        }

        public int getActiveCount() {
            int cnt = 0;
            for (EnhancedQueueExecutor executor : executors) {
                cnt += executor.getActiveCount();
            }
            return cnt;
        }

        public boolean isAllowCoreThreadTimeOut() {
            return true;
        }

        public void setAllowCoreThreadTimeOut(final boolean value) {
            for (EnhancedQueueExecutor executor : executors) {
                executor.allowCoreThreadTimeOut(value);
            }
        }

        public long getKeepAliveTimeSeconds() {
            return chooseDelegate().getKeepAliveTime().getSeconds();
        }

        public void setKeepAliveTimeSeconds(final long seconds) {
            Duration duration = Duration.ofSeconds(seconds);
            for (EnhancedQueueExecutor executor : executors) {
                executor.setKeepAliveTime(duration);
            }
        }

        public int getMaximumQueueSize() {
            return chooseDelegate().getMaximumQueueSize() * getStripes();
        }

        public void setMaximumQueueSize(final int size) {
            int count = executors.size();
            int finalSize;
            if (size == Integer.MAX_VALUE) {
                finalSize = size;
            } else {
                boolean bump = size % count != 0;
                finalSize = size / count + (bump ? 1 : 0);
            }
            for (EnhancedQueueExecutor executor : executors) {
                executor.setMaximumQueueSize(finalSize);
            }
        }

        public int getQueueSize() {
            long sum = 0;
            for (EnhancedQueueExecutor executor : executors) {
                sum += executor.getQueueSize();
            }
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }

        public int getLargestQueueSize() {
            int max = 0;
            for (EnhancedQueueExecutor executor : executors) {
                max = Math.max(max, executor.getLargestQueueSize());
            }
            return max;
        }

        public boolean isQueueBounded() {
            return chooseDelegate().getQueueSize() < Integer.MAX_VALUE;
        }

        public boolean isQueueSizeModifiable() {
            return true;
        }

        public boolean isShutdown() {
            return StripedEnhancedQueueExecutor.this.isShutdown();
        }

        public boolean isTerminating() {
            return StripedEnhancedQueueExecutor.this.isTerminating();
        }

        public boolean isTerminated() {
            return StripedEnhancedQueueExecutor.this.isTerminated();
        }

        public long getSubmittedTaskCount() {
            long sum = 0;
            for (EnhancedQueueExecutor executor : executors) {
                sum += executor.getSubmittedTaskCount();
            }
            return sum;
        }

        public long getRejectedTaskCount() {
            long sum = 0;
            for (EnhancedQueueExecutor executor : executors) {
                sum += executor.getRejectedTaskCount();
            }
            return sum;
        }

        public long getCompletedTaskCount() {
            long sum = 0;
            for (EnhancedQueueExecutor executor : executors) {
                sum += executor.getCompletedTaskCount();
            }
            return sum;
        }
    }
}
