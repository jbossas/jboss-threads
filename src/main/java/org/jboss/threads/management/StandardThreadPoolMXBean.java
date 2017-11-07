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

package org.jboss.threads.management;

/**
 * An MXBean which contains the attributes and operations found on all standard thread pools.
 */
public interface StandardThreadPoolMXBean {
    /**
     * Get the pool size growth resistance factor.  If the thread pool does not support growth resistance,
     * then {@code 0.0} (no resistance) is returned.
     *
     * @return the growth resistance factor ({@code 0.0 ≤ n ≤ 1.0})
     */
    float getGrowthResistance();

    /**
     * Set the pool size growth resistance factor, if supported.
     *
     * @param value the growth resistance factor ({@code 0.0 ≤ n ≤ 1.0})
     */
    void setGrowthResistance(float value);

    /**
     * Determine whether the thread pool supports a growth resistance factor.
     *
     * @return {@code true} if the growth resistance factor is supported, {@code false} otherwise
     */
    boolean isGrowthResistanceSupported();

    /**
     * Get the core pool size.  This is the size below which new threads will always be created if no idle threads
     * are available.  If the thread pool does not support a separate core pool size, this size will match the
     * maximum size.
     *
     * @return the core pool size
     */
    int getCorePoolSize();

    /**
     * Set the core pool size.  If the configured maximum pool size is less than the configured core size, the
     * core size will be reduced to match the maximum size when the thread pool is constructed.  If the thread pool
     * does not support a separate core pool size, this setting will be ignored.
     *
     * @param corePoolSize the core pool size (must be greater than or equal to 0)
     */
    void setCorePoolSize(int corePoolSize);

    /**
     * Determine whether this implementation supports a separate core pool size.
     *
     * @return {@code true} if a separate core size is supported; {@code false} otherwise
     */
    boolean isCorePoolSizeSupported();

    /**
     * Attempt to start a core thread without submitting work to it.
     *
     * @return {@code true} if the thread was started, or {@code false} if the pool is filled to the core size, the
     * thread could not be created, or prestart of core threads is not supported.
     */
    boolean prestartCoreThread();

    /**
     * Attempt to start all core threads.  This is usually equivalent to calling {@link #prestartCoreThread()} in a loop
     * until it returns {@code false} and counting the {@code true} results.
     *
     * @return the number of started core threads (may be 0)
     */
    int prestartAllCoreThreads();

    /**
     * Determine whether this thread pool allows manual pre-start of core threads.
     *
     * @return {@code true} if pre-starting core threads is supported, {@code false} otherwise
     */
    boolean isCoreThreadPrestartSupported();

    /**
     * Get the maximum pool size.  This is the absolute upper limit to the size of the thread pool.
     *
     * @return the maximum pool size
     */
    int getMaximumPoolSize();

    /**
     * Set the maximum pool size.  If the configured maximum pool size is less than the configured core size, the
     * core size will be reduced to match the maximum size when the thread pool is constructed.
     *
     * @param maxPoolSize the maximum pool size (must be greater than or equal to 0)
     */
    void setMaximumPoolSize(final int maxPoolSize);

    /**
     * Get an estimate of the current number of active threads in the pool.
     *
     * @return an estimate of the current number of active threads in the pool
     */
    int getPoolSize();

    /**
     * Get an estimate of the peak number of threads that the pool has ever held.
     *
     * @return an estimate of the peak number of threads that the pool has ever held
     */
    int getLargestPoolSize();

    /**
     * Get an estimate of the current number of active (busy) threads.
     *
     * @return an estimate of the active count
     */
    int getActiveCount();

    /**
     * Determine whether core threads are allowed to time out.  A "core thread" is defined as any thread in the pool
     * when the pool size is below the pool's {@linkplain #getCorePoolSize() core pool size}.  If the thread pool
     * does not support a separate core pool size, this method should return {@code false}.
     * <p>
     * This method is named differently from the typical {@code allowsCoreThreadTimeOut()} in order to accommodate
     * the requirements of MXBean attribute methods.
     *
     * @return {@code true} if core threads are allowed to time out, {@code false} otherwise
     */
    boolean isAllowCoreThreadTimeOut();

    /**
     * Establish whether core threads are allowed to time out.  A "core thread" is defined as any thread in the pool
     * when the pool size is below the pool's {@linkplain #getCorePoolSize() core pool size}.  If the thread pool
     * does not support a separate core pool size, the value is ignored.
     * <p>
     * This method is named differently from the typical {@code allowCoreThreadTimeOut(boolean)} in order to accommodate
     * the requirements of MXBean attribute methods.
     *
     * @param value {@code true} if core threads are allowed to time out, {@code false} otherwise
     */
    void setAllowCoreThreadTimeOut(boolean value);

    /**
     * Get the thread keep-alive time, in seconds.
     * <p>
     * This method differs from the typical {@code getKeepAliveTime(TimeUnit)} due to the inability to send in a
     * time units parameter on an MXBean attribute.  As such, the unit is hard-coded to seconds.
     *
     * @return the thread keep-alive time, in seconds
     */
    long getKeepAliveTimeSeconds();

    /**
     * Set the thread keep-alive time, in seconds.
     * <p>
     * This method differs from the typical {@code getKeepAliveTime(TimeUnit)} due to the inability to send in a
     * time units parameter on an MXBean attribute.  As such, the unit is hard-coded to seconds.
     *
     * @param seconds the thread keep-alive time, in seconds (must be greater than or equal to 0)
     */
    void setKeepAliveTimeSeconds(long seconds);

    /**
     * Get the maximum queue size for this thread pool.  If there is no queue or it is not bounded, {@link Integer#MAX_VALUE} is
     * returned.
     *
     * @return the maximum queue size
     */
    int getMaximumQueueSize();

    /**
     * Set the maximum queue size for this thread pool.  If the new maximum queue size is smaller than the current queue
     * size, there is no effect other than preventing tasks from being enqueued until the size decreases below the
     * maximum again.  If changing the maximum queue size is not supported, or there is no bounded backing queue,
     * then the value is ignored.
     *
     * @param size the maximum queue size for this thread pool
     */
    void setMaximumQueueSize(int size);

    /**
     * Get an estimate of the current queue size, if any.  If no backing queue exists, or its size cannot be determined,
     * this method will return 0.
     *
     * @return an estimate of the current queue size
     */
    int getQueueSize();

    /**
     * Get an estimate of the peak size of the queue, if any.  If no backing queue exists, or its size cannot be determined,
     * this method will return 0.
     *
     * @return an estimate of the peak size of the queue
     */
    int getLargestQueueSize();

    /**
     * Determine whether there is a bounded queue backing this thread pool.
     *
     * @return {@code true} if there is a bounded backing queue, {@code false} otherwise
     */
    boolean isQueueBounded();

    /**
     * Determine whether the maximum queue size is modifiable.
     *
     * @return {@code true} if the queue size is modifiable, false otherwise
     */
    boolean isQueueSizeModifiable();

    /**
     * Determine whether shutdown was requested.
     *
     * @return {@code true} if shutdown was requested, {@code false} otherwise
     */
    boolean isShutdown();

    /**
     * Determine whether shutdown is in progress.
     *
     * @return {@code true} if shutdown is in progress, {@code false} otherwise
     */
    boolean isTerminating();

    /**
     * Determine whether shutdown is complete.
     *
     * @return {@code true} if shutdown is complete, {@code false} otherwise
     */
    boolean isTerminated();

    /**
     * Get an estimate of the total number of tasks ever submitted to this thread pool.  This number may be zero
     * if the underlying thread pool does not support this metric.
     *
     * @return an estimate of the total number of tasks ever submitted to this thread pool
     */
    long getSubmittedTaskCount();

    /**
     * Get an estimate of the total number of tasks ever rejected by this thread pool for any reason.  This number may be zero
     * if the underlying thread pool does not support this metric.
     *
     * @return an estimate of the total number of tasks ever rejected by this thread pool
     */
    long getRejectedTaskCount();

    /**
     * Get an estimate of the number of tasks completed by this thread pool.  This number may be zero
     * if the underlying thread pool does not support this metric.
     *
     * @return an estimate of the number of tasks completed by this thread pool
     */
    long getCompletedTaskCount();

    /**
     * Get the number of spin misses that have occurred.  Spin misses indicate that contention is not being properly
     * handled by the thread pool.
     *
     * @return an estimate of the number of spin misses
     */
    default long getSpinMissCount() {
        return 0;
    }
}
