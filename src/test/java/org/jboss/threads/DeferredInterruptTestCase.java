package org.jboss.threads;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class DeferredInterruptTestCase {

    @Test
    public void testDeferral() throws Exception {
        final AtomicBoolean delivered0 = new AtomicBoolean();
        final AtomicBoolean deferred = new AtomicBoolean();
        final AtomicBoolean delivered = new AtomicBoolean();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final JBossThread thread = new JBossThread(new Runnable() {
            public void run() {
                Thread.interrupted();
                latch1.countDown();
                // now wait
                LockSupport.parkNanos(3000000000L);
                if (! Thread.currentThread().isInterrupted()) {
                    // spurious unpark, perhaps
                    LockSupport.parkNanos(3000000000L);
                }
                delivered0.set(Thread.interrupted());
                JBossThread.executeWithInterruptDeferred(new Runnable() {
                    public void run() {
                        latch2.countDown();
                        LockSupport.parkNanos(500000000L);
                        deferred.set(! Thread.interrupted());
                    }
                });
                delivered.set(Thread.interrupted());
            }
        });
        thread.start();
        latch1.await();
        thread.interrupt();
        latch2.await();
        thread.interrupt();
        thread.join();
        assertTrue(delivered0.get());
        assertTrue(deferred.get());
        assertTrue(delivered.get());
    }
}
