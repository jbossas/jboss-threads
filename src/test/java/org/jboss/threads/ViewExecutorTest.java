/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public final class ViewExecutorTest {

    @Test
    public void testExecution() throws InterruptedException {
        final ViewExecutor ve = ViewExecutor.builder(JBossExecutors.directExecutor()).build();
        assertFalse(ve.isShutdown());
        assertFalse(ve.isTerminated());
        AtomicBoolean ran = new AtomicBoolean();
        ve.execute(new Runnable() {
            public void run() {
                ran.set(true);
            }
        });
        assertTrue(ran.get());
        ve.shutdown();
        assertTrue(ve.isShutdown());
        assertTrue(ve.awaitTermination(10L, TimeUnit.SECONDS));
        assertTrue(ve.isTerminated());
    }

    @Test
    public void testQueuedExecution() {
        final ArrayDeque<Runnable> executedTasks = new ArrayDeque<>();
        Executor testExecutor = new QueuedExecutor(executedTasks);
        final ViewExecutor ve = ViewExecutor.builder(testExecutor).setMaxSize(1).build();
        AtomicBoolean ran1 = new AtomicBoolean();
        ve.execute(new Runnable() {
            public void run() {
                ran1.set(true);
            }
        });
        assertEquals(1, executedTasks.size());
        AtomicBoolean ran2 = new AtomicBoolean();
        ve.execute(new Runnable() {
            public void run() {
                ran2.set(true);
            }
        });
        assertEquals(1, executedTasks.size());
        executedTasks.poll().run();
        assertEquals(1, executedTasks.size());
        assertTrue(ran1.get());
        executedTasks.poll().run();
        assertTrue(ran2.get());
        assertEquals(0, executedTasks.size());
    }

    @Test
    public void testInterruptedShutdown() throws InterruptedException {
        ExecutorService testExecutor = Executors.newSingleThreadExecutor();
        final ViewExecutor ve = ViewExecutor.builder(testExecutor).build();
        AtomicBoolean intr = new AtomicBoolean();
        CountDownLatch runGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(1);
        ve.execute(new Runnable() {
            public void run() {
                runGate.countDown();
                try {
                    Thread.sleep(60_000L);
                } catch (InterruptedException e) {
                    intr.set(true);
                } finally {
                    finishGate.countDown();
                }
            }
        });
        runGate.await();
        assertFalse(intr.get());
        ve.shutdown(true);
        finishGate.await();
        assertTrue(intr.get());
        testExecutor.shutdown();
        assertTrue(testExecutor.awaitTermination(5L, TimeUnit.SECONDS));
    }

    private static class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> executedTasks;

        public QueuedExecutor(final ArrayDeque<Runnable> executedTasks) {
            this.executedTasks = executedTasks;
        }

        public void execute(final Runnable command) {
            executedTasks.add(command);
        }
    }
}
