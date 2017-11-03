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

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.threads.management.BoundedThreadPoolExecutorMBean;
import org.wildfly.common.Assert;

@Deprecated
class ThreadFactoryExecutor implements BlockingExecutor, BoundedThreadPoolExecutorMBean {

    private final ThreadFactory factory;
    private final Semaphore limitSemaphore;
    private final DirectExecutor taskExecutor;

    private final Object lock = new Object();
    private int maxThreads;
    private int largestThreadCount;
    private int currentThreadCount;
    private final AtomicInteger rejected = new AtomicInteger();
    private volatile boolean blocking;

    ThreadFactoryExecutor(final ThreadFactory factory, int maxThreads, boolean blocking, final DirectExecutor taskExecutor) {
        this.factory = factory;
        this.maxThreads = maxThreads;
        this.blocking = blocking;
        this.taskExecutor = taskExecutor;
        limitSemaphore = new Semaphore(maxThreads);
    }

    public int getMaxThreads() {
        synchronized (lock) {
            return maxThreads;
        }
    }

    public void setMaxThreads(final int maxThreads) {
        Assert.checkMinimumParameter("maxThreads", 0, maxThreads);
        synchronized (lock) {
            final int old = this.maxThreads;
            final int diff = old - maxThreads;
            if (diff < 0) {
                limitSemaphore.release(-diff);
            } else if (diff > 0) {
                if (! limitSemaphore.tryAcquire(diff)) {
                    throw new IllegalArgumentException("Cannot reduce maximum threads below current number of running threads");
                }
            }
            this.maxThreads = maxThreads;
        }
    }

    public void execute(final Runnable task) {
        Assert.checkNotNullParam("task", task);
        try {
            final Semaphore semaphore = limitSemaphore;
            if (blocking) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ExecutionInterruptedException();
                }
            } else {
                if (! semaphore.tryAcquire()) {
                    throw new RejectedExecutionException("Task limit reached");
                }
            }
            boolean ok = false;
            try {
                final Thread thread = factory.newThread(new Runnable() {
                    public void run() {
                        try {
                            synchronized (lock) {
                                int t = ++currentThreadCount;
                                if (t > largestThreadCount) {
                                    largestThreadCount = t;
                                }
                            }
                            taskExecutor.execute(task);
                            synchronized (lock) {
                                currentThreadCount--;
                            }
                        } finally {
                            limitSemaphore.release();
                        }
                    }
                });
                if (thread == null) {
                    throw new ThreadCreationException("No threads can be created");
                }
                thread.start();
                ok = true;
            } finally {
                if (! ok) semaphore.release();
            }
        } catch (RejectedExecutionException e) {
            rejected.getAndIncrement();
            throw e;
        }
    }

    public void executeBlocking(final Runnable task) throws RejectedExecutionException, InterruptedException {
        Assert.checkNotNullParam("task", task);
        try {
            final Semaphore semaphore = limitSemaphore;
            semaphore.acquire();
            boolean ok = false;
            try {
                final Thread thread = factory.newThread(new Runnable() {
                    public void run() {
                        try {
                            synchronized (lock) {
                                int t = ++currentThreadCount;
                                if (t > largestThreadCount) {
                                    largestThreadCount = t;
                                }
                            }
                            taskExecutor.execute(task);
                            synchronized (lock) {
                                currentThreadCount--;
                            }
                        } finally {
                            limitSemaphore.release();
                        }
                    }
                });
                if (thread == null) {
                    throw new ThreadCreationException("No threads can be created");
                }
                thread.start();
                ok = true;
            } finally {
                if (! ok) semaphore.release();
            }
        } catch (RejectedExecutionException e) {
            rejected.getAndIncrement();
            throw e;
        }
    }

    public void executeBlocking(final Runnable task, final long timeout, final TimeUnit unit) throws RejectedExecutionException, InterruptedException {
        Assert.checkNotNullParam("task", task);
        try {
            final Semaphore semaphore = limitSemaphore;
            if (! semaphore.tryAcquire(timeout, unit)) {
                throw new ExecutionTimedOutException();
            }
            boolean ok = false;
            try {
                final Thread thread = factory.newThread(new Runnable() {
                    public void run() {
                        try {
                            synchronized (lock) {
                                int t = ++currentThreadCount;
                                if (t > largestThreadCount) {
                                    largestThreadCount = t;
                                }
                            }
                            taskExecutor.execute(task);
                            synchronized (lock) {
                                currentThreadCount--;
                            }
                        } finally {
                            limitSemaphore.release();
                        }
                    }
                });
                if (thread == null) {
                    throw new ThreadCreationException("No threads can be created");
                }
                thread.start();
                ok = true;
            } finally {
                if (! ok) semaphore.release();
            }
        } catch (RejectedExecutionException e) {
            rejected.getAndIncrement();
            throw e;
        }
    }

    public void executeNonBlocking(final Runnable task) throws RejectedExecutionException {
        Assert.checkNotNullParam("task", task);
        try {
            final Semaphore semaphore = limitSemaphore;
            if (! semaphore.tryAcquire()) {
                throw new RejectedExecutionException("Task limit reached");
            }
            boolean ok = false;
            try {
                final Thread thread = factory.newThread(new Runnable() {
                    public void run() {
                        try {
                            synchronized (lock) {
                                int t = ++currentThreadCount;
                                if (t > largestThreadCount) {
                                    largestThreadCount = t;
                                }
                            }
                            taskExecutor.execute(task);
                            synchronized (lock) {
                                currentThreadCount--;
                            }
                        } finally {
                            limitSemaphore.release();
                        }
                    }
                });
                if (thread == null) {
                    throw new ThreadCreationException("No threads can be created");
                }
                thread.start();
                ok = true;
            } finally {
                if (! ok) semaphore.release();
            }
        } catch (RejectedExecutionException e) {
            rejected.getAndIncrement();
            throw e;
        }
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(final boolean blocking) {
        this.blocking = blocking;
    }

    public int getLargestThreadCount() {
        synchronized (lock) {
            return largestThreadCount;
        }
    }

    public int getCurrentThreadCount() {
        synchronized (lock) {
            return currentThreadCount;
        }
    }

    /** {@inheritDoc} */
    public int getQueueSize() {
        return 0;
    }

    public int getRejectedCount() {
        return rejected.get();
    }

    public long getKeepAliveTime() {
        return 0L;
    }

    public void setKeepAliveTime(final long milliseconds) {
        if (milliseconds != 0L) {
            throw new IllegalArgumentException("Keep-alive may only be set to 0ms");
        }
    }

    public String toString() {
        return String.format("%s (%s)", super.toString(), factory);
    }
}
