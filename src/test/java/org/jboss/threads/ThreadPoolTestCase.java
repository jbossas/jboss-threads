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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;

/**
 *
 */
public final class ThreadPoolTestCase extends TestCase {

    private final JBossThreadFactory threadFactory = new JBossThreadFactory(null, null, null, "test thread %p %t", null, null);

    private static final class SimpleTask implements Runnable {

        private final CountDownLatch taskUnfreezer;
        private final CountDownLatch taskFinishLine;

        private SimpleTask(final CountDownLatch taskUnfreezer, final CountDownLatch taskFinishLine) {
            this.taskUnfreezer = taskUnfreezer;
            this.taskFinishLine = taskFinishLine;
        }

        public void run() {
            try {
                assertTrue(taskUnfreezer.await(800L, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                fail("interrupted");
            }
            taskFinishLine.countDown();
        }
    }

    public void testBasic() throws InterruptedException {
        // Start some tasks, let them run, then shut down the executor
        final int cnt = 100;
        final CountDownLatch taskUnfreezer = new CountDownLatch(1);
        final CountDownLatch taskFinishLine = new CountDownLatch(cnt);
        final ExecutorService simpleQueueExecutor = new QueueExecutor(5, 5, 500L, TimeUnit.MILLISECONDS, 1000, threadFactory, true, null);
        for (int i = 0; i < cnt; i ++) {
            simpleQueueExecutor.execute(new SimpleTask(taskUnfreezer, taskFinishLine));
        }
        taskUnfreezer.countDown();
        final boolean finished = taskFinishLine.await(800L, TimeUnit.MILLISECONDS);
        assertTrue(finished);
        simpleQueueExecutor.shutdown();
        try {
            simpleQueueExecutor.execute(new Runnable() {
                public void run() {
                }
            });
            fail("Task not rejected after shutdown");
        } catch (Throwable t) {
            assertTrue(t instanceof RejectedExecutionException);
        }
        assertTrue(simpleQueueExecutor.awaitTermination(800L, TimeUnit.MILLISECONDS));
    }

    public void testShutdownNow() throws InterruptedException {
        final AtomicBoolean interrupted = new AtomicBoolean();
        final AtomicBoolean ran = new AtomicBoolean();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final ExecutorService simpleQueueExecutor = new QueueExecutor(5, 5, 500L, TimeUnit.MILLISECONDS, 1000, threadFactory, true, null);
        simpleQueueExecutor.execute(new Runnable() {
            public void run() {
                try {
                    ran.set(true);
                    startLatch.countDown();
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            }
        });
        assertTrue("Task not started", startLatch.await(300L, TimeUnit.MILLISECONDS));
        assertTrue("Task returned", simpleQueueExecutor.shutdownNow().isEmpty());
        try {
            simpleQueueExecutor.execute(new Runnable() {
                public void run() {
                }
            });
            fail("Task not rejected after shutdown");
        } catch (RejectedExecutionException t) {
        }
        assertTrue("Executor not shut down in 800ms", simpleQueueExecutor.awaitTermination(800L, TimeUnit.MILLISECONDS));
        assertTrue("Task wasn't run", ran.get());
        assertTrue("Worker wasn't interrupted", interrupted.get());
    }

    private static class Holder<T> {
        private T instance;
        public Holder(T instance) {
            this.instance = instance;
        }
        public T get() { return instance; }
        public void set(T instance) {this.instance = instance;}
    }

    public void testBlocking() throws InterruptedException {
        final int queueSize = 20;
        final int coreThreads = 5;
        final int extraThreads = 5;
        final int cnt = queueSize + coreThreads + extraThreads;
        final CountDownLatch taskUnfreezer = new CountDownLatch(1);
        final CountDownLatch taskFinishLine = new CountDownLatch(cnt);
        final ExecutorService simpleQueueExecutor = new QueueExecutor(coreThreads, coreThreads + extraThreads, 500L, TimeUnit.MILLISECONDS, new ArrayQueue<Runnable>(queueSize), threadFactory, true, null);
        for (int i = 0; i < cnt; i ++) {
            simpleQueueExecutor.execute(new SimpleTask(taskUnfreezer, taskFinishLine));
        }
        Thread.currentThread().interrupt();
        try {
            simpleQueueExecutor.execute(new Runnable() {
                public void run() {
                }
            });
            fail("Task was accepted");
        } catch (RejectedExecutionException t) {
        }
        Thread.interrupted();
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread otherThread = threadFactory.newThread(new Runnable() {
            public void run() {
                simpleQueueExecutor.execute(new Runnable() {
                    public void run() {
                        latch.countDown();
                    }
                });
            }
        });
        otherThread.start();
        assertFalse("Task executed without wait", latch.await(100L, TimeUnit.MILLISECONDS));
        // safe to say the other thread is blocking...?
        taskUnfreezer.countDown();
        assertTrue("Task never ran", latch.await(800L, TimeUnit.MILLISECONDS));
        otherThread.join(500L);
        assertTrue("Simple Tasks never ran", taskFinishLine.await(800L, TimeUnit.MILLISECONDS));
        simpleQueueExecutor.shutdown();
        final Holder<Boolean> callback = new Holder<Boolean>(false);
        ((QueueExecutor)simpleQueueExecutor).addShutdownListener(new EventListener<Object>() {
            @Override
            public void handleEvent(Object attachment) {
                callback.set(true);
            } } , null);
        assertTrue("Calback not called", callback.get());
        assertTrue("Executor not shut down in 800ms", simpleQueueExecutor.awaitTermination(800L, TimeUnit.MILLISECONDS));
    }

    public void testBlockingEmpty() throws InterruptedException {
        final int queueSize = 20;
        final int coreThreads = 5;
        final int extraThreads = 5;
        final int cnt = queueSize + coreThreads + extraThreads;
        final ExecutorService simpleQueueExecutor = new QueueExecutor(coreThreads, coreThreads + extraThreads, 500L, TimeUnit.MILLISECONDS, new ArrayQueue<Runnable>(queueSize), threadFactory, true, null);
        simpleQueueExecutor.shutdown();
        final Holder<Boolean> callback = new Holder<Boolean>(false);
        ((QueueExecutor)simpleQueueExecutor).addShutdownListener(new EventListener<Object>() {
            @Override
            public void handleEvent(Object attachment) {
                callback.set(true);
            } } , null);
        assertTrue("Calback not called", callback.get());
        assertTrue("Executor not shut down in 800ms", simpleQueueExecutor.awaitTermination(800L, TimeUnit.MILLISECONDS));
        Thread.interrupted();
    }

    public void testQueuelessEmpty() throws InterruptedException {
        final int queueSize = 20;
        final int coreThreads = 5;
        final int extraThreads = 5;
        final int cnt = queueSize + coreThreads + extraThreads;
        final ExecutorService simpleQueueExecutor = new QueuelessExecutor(threadFactory, SimpleDirectExecutor.INSTANCE, null, 500L);
        simpleQueueExecutor.shutdown();
        final Holder<Boolean> callback = new Holder<Boolean>(false);
        ((QueuelessExecutor)simpleQueueExecutor).addShutdownListener(new EventListener<Object>() {
            @Override
            public void handleEvent(Object attachment) {
                callback.set(true);
            } } , null);
        assertTrue("Calback not called", callback.get());
        assertTrue("Executor not shut down in 800ms", simpleQueueExecutor.awaitTermination(800L, TimeUnit.MILLISECONDS));
        Thread.interrupted();
    }
}
