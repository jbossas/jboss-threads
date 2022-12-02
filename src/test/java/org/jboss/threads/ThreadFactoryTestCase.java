package org.jboss.threads;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public final class ThreadFactoryTestCase {
    private static final NullRunnable NULL_RUNNABLE = new NullRunnable();

    private static class NullRunnable implements Runnable {
        public void run() {
        }
    }

    private static void doTestNamePattern(JBossThreadFactory threadFactory, int expectedPerFactoryId, int expectedGlobalId, int expectedFactoryId) {
        final String name = threadFactory.newThread(NULL_RUNNABLE).getName();
        assertTrue(name.matches("-([a-z]+:)*one:two:three-%-" + expectedPerFactoryId + "-" + expectedGlobalId + "-" + expectedFactoryId + "-"), "Wrong thread name (" + name + ") ");
    }

    /**
     * This MUST be the first test, otherwise the sequence numbers will be wrong.
     */
    @Test
    @Disabled("skip test for now since it depends on order")
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

    @Test
    public void testDaemon() {
        final JBossThreadFactory threadFactory1 = new JBossThreadFactory(null, Boolean.TRUE, null, "%t", null, null);
        assertTrue(threadFactory1.newThread(NULL_RUNNABLE).isDaemon(), "Thread is not a daemon thread");
        final JBossThreadFactory threadFactory2 = new JBossThreadFactory(null, Boolean.FALSE, null, "%t", null, null);
        assertFalse(threadFactory2.newThread(NULL_RUNNABLE).isDaemon(),"Thread should not be a daemon thread");
    }

    @Test
    public void testInterruptHandler() throws InterruptedException {
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final AtomicBoolean called = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);
        final JBossThreadFactory threadFactory = new JBossThreadFactory(null, null, null, null, null, null);
        final Thread t = threadFactory.newThread(new Runnable() {
            public void run() {
                synchronized (this) {
                    final InterruptHandler old = JBossThread.getAndSetInterruptHandler(new InterruptHandler() {
                        public void handleInterrupt(final Thread thread) {
                            called.set(true);
                        }
                    });
                    Thread.interrupted();
                    latch.countDown();
                    try {
                        for (;;) wait();
                    } catch (InterruptedException e) {
                        wasInterrupted.set(true);
                    }
                }
            }
        });
        t.start();
        latch.await();
        t.interrupt();
        t.join();
        assertTrue(wasInterrupted.get(), "Was not interrupted");
        assertTrue(called.get(), "Handler was not called");
    }

    @Test
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
        assertTrue(called.get(), "Handler was not called");
    }

    @Test
    public void testInitialPriority() {
        assertEquals(1, new JBossThreadFactory(null, null, Integer.valueOf(1), null, null, null).newThread(NULL_RUNNABLE).getPriority(), "Wrong initial thread priority");
        assertEquals(2, new JBossThreadFactory(null, null, Integer.valueOf(2), null, null, null).newThread(NULL_RUNNABLE).getPriority(), "Wrong initial thread priority");
        final ThreadGroup grp = new ThreadGroup("blah");
        grp.setMaxPriority(5);
        assertEquals(5, new JBossThreadFactory(grp, null, Integer.valueOf(10), null, null, null).newThread(NULL_RUNNABLE).getPriority(), "Wrong initial thread priority");
        assertEquals(1, new JBossThreadFactory(grp, null, Integer.valueOf(1), null, null, null).newThread(NULL_RUNNABLE).getPriority(), "Wrong initial thread priority");
    }
}
