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

import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public final class ThreadFactoryTestCase extends TestCase {
    private static final NullRunnable NULL_RUNNABLE = new NullRunnable();

    private static class NullRunnable implements Runnable {
        public void run() {
        }
    }

    private static void doTestNamePattern(JBossThreadFactory threadFactory, int expectedPerFactoryId, int expectedGlobalId, int expectedFactoryId) {
        final String name = threadFactory.newThread(NULL_RUNNABLE).getName();
        assertTrue("Wrong thread name (" + name + ") ", name.matches("-([a-z]+:)*one:two:three-%-" + expectedPerFactoryId + "-" + expectedGlobalId + "-" + expectedFactoryId + "-"));
    }

    /**
     * This MUST be the first test, otherwise the sequence numbers will be wrong.
     */
    public void testNamePattern() {
        // TODO - skip test for now since it depends on order.
        if (true) return;
        final JBossThreadFactory threadFactory1 = new JBossThreadFactory(new ThreadGroup(new ThreadGroup(new ThreadGroup("one"), "two"), "three"), null,
                null, "-%p-%%-%t-%g-%f-", null, null);
        doTestNamePattern(threadFactory1, 1, 1, 1);
        doTestNamePattern(threadFactory1, 2, 2, 1);
        doTestNamePattern(threadFactory1, 3, 3, 1);
        final JBossThreadFactory threadFactory2 = new JBossThreadFactory(new ThreadGroup(new ThreadGroup(new ThreadGroup("one"), "two"), "three"), null,
                null, "-%p-%%-%t-%g-%f-", null, null);
        doTestNamePattern(threadFactory2, 1, 4, 2);
        doTestNamePattern(threadFactory2, 2, 5, 2);
        doTestNamePattern(threadFactory2, 3, 6, 2);
        doTestNamePattern(threadFactory2, 4, 7, 2);
        // back to the first factory...
        doTestNamePattern(threadFactory1, 4, 8, 1);
    }

    public void testDaemon() {
        final JBossThreadFactory threadFactory1 = new JBossThreadFactory(null, Boolean.TRUE, null, "%t", null, null);
        assertTrue("Thread is not a daemon thread", threadFactory1.newThread(NULL_RUNNABLE).isDaemon());
        final JBossThreadFactory threadFactory2 = new JBossThreadFactory(null, Boolean.FALSE, null, "%t", null, null);
        assertFalse("Thread should not be a daemon thread", threadFactory2.newThread(NULL_RUNNABLE).isDaemon());
    }

    public void testInterruptHandler() throws InterruptedException {
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final AtomicBoolean called = new AtomicBoolean();
        final JBossThreadFactory threadFactory = new JBossThreadFactory(null, null, null, null, null, null);
        final Thread t = threadFactory.newThread(new Runnable() {
            public void run() {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        wasInterrupted.set(true);
                    }
                }
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertTrue("Was not interrupted", wasInterrupted.get());
        assertTrue("Handler was not called", called.get());
    }

    public void testUncaughtHandler() throws InterruptedException {
        final AtomicBoolean called = new AtomicBoolean();
        final JBossThreadFactory factory = new JBossThreadFactory(null, null, null, null, new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(final Thread t, final Throwable e) {
                called.set(true);
            }
        }, null);
        final Thread t = factory.newThread(new Runnable() {
            public void run() {
                throw new RuntimeException("...");
            }
        });
        t.start();
        t.join();
        assertTrue("Handler was not called", called.get());
    }

    public void testInitialPriority() {
        assertEquals("Wrong initial thread priority", 1, new JBossThreadFactory(null, null, Integer.valueOf(1), null, null, null).newThread(NULL_RUNNABLE).getPriority());
        assertEquals("Wrong initial thread priority", 2, new JBossThreadFactory(null, null, Integer.valueOf(2), null, null, null).newThread(NULL_RUNNABLE).getPriority());
        final ThreadGroup grp = new ThreadGroup("blah");
        grp.setMaxPriority(5);
        assertEquals("Wrong initial thread priority", 5, new JBossThreadFactory(grp, null, Integer.valueOf(10), null, null, null).newThread(NULL_RUNNABLE).getPriority());
        assertEquals("Wrong initial thread priority", 1, new JBossThreadFactory(grp, null, Integer.valueOf(1), null, null, null).newThread(NULL_RUNNABLE).getPriority());
    }
}
