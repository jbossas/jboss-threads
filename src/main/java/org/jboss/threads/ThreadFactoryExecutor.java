/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.threads.management.BoundedThreadPoolExecutorMBean;

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
        if (maxThreads < 0) {
            throw new IllegalArgumentException("Max threads must not be negative");
        }
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
        if (task == null) {
            throw new NullPointerException("task is null");
        }
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
        if (task == null) {
            throw new NullPointerException("task is null");
        }
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
        if (task == null) {
            throw new NullPointerException("task is null");
        }
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
        if (task == null) {
            throw new NullPointerException("task is null");
        }
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

    @Override
    public long getNumberOfFreeThreads() {
        return getMaxThreads() - getCurrentThreadCount();
    }

    public String toString() {
        return String.format("%s (%s)", super.toString(), factory);
    }
}
