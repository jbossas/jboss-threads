package org.jboss.threads;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class ScheduledEnhancedQueueExecutorTest {

    @Test
    public void testCancel() throws Exception {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        try {
            ScheduledFuture<?> future = eqe.schedule(() -> fail("Should never run"), 1000, TimeUnit.DAYS);
            Thread.sleep(400); // a few ms to let things percolate
            assertFalse(future.isCancelled());
            // this should succeed since the task isn't submitted yet
            assertTrue(future.cancel(false));
            assertTrue(future.isCancelled());
            eqe.shutdown();
            assertTrue(eqe.awaitTermination(5, TimeUnit.SECONDS), "Timely shutdown");
        } finally {
            eqe.shutdownNow();
        }
    }

    @Test
    public void testCancelWhileRunning() throws Exception {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            ScheduledFuture<Boolean> future = eqe.schedule(() -> { latch.countDown(); Thread.sleep(1_000_000_000L); return Boolean.TRUE; }, 1, TimeUnit.NANOSECONDS);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timely task execution");
            assertFalse(future.isCancelled());
            // task is running
            assertTrue(future.cancel(false));
            assertFalse(future.isCancelled());
            assertFalse(future.isDone());
            // now try to interrupt it
            assertTrue(future.cancel(true));
            assertFalse(future.isCancelled());
            // now get it
            Throwable cause = assertThrows(ExecutionException.class, () -> future.get(100L, TimeUnit.MILLISECONDS)).getCause();
            assertInstanceOf(InterruptedException.class, cause);
            assertTrue(future.isDone());
            eqe.shutdown();
            assertTrue(eqe.awaitTermination(5, TimeUnit.SECONDS), "Timely shutdown");
        } finally {
            eqe.shutdownNow();
        }
    }

    @Test
    public void testReasonableExecutionDelay() throws Exception {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        try {
            Callable<Boolean> task = () -> Boolean.TRUE;
            long start = System.nanoTime();
            ScheduledFuture<Boolean> future = eqe.schedule(task, 1, TimeUnit.MILLISECONDS);
            Boolean result = future.get();
            long execTime = System.nanoTime() - start;
            long expected = 1_000_000L;
            assertTrue(execTime >= expected, "Execution too short (expected at least " + expected + ", got " + execTime + ")");
            assertNotNull(result);
            assertTrue(result.booleanValue());
            start = System.nanoTime();
            future = eqe.schedule(task, 500, TimeUnit.MILLISECONDS);
            result = future.get();
            execTime = System.nanoTime() - start;
            expected = 500_000_000L;
            assertTrue(execTime >= expected, "Execution too short (expected at least " + expected + ", got " + execTime + ")");
            assertNotNull(result);
            assertTrue(result.booleanValue());
            eqe.shutdown();
            assertTrue(eqe.awaitTermination(5, TimeUnit.SECONDS), "Timely shutdown");
        } finally {
            eqe.shutdownNow();
        }
    }

    @Test
    public void testFixedRateExecution() throws Exception {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        try {
            AtomicInteger ai = new AtomicInteger();
            CountDownLatch completeLatch = new CountDownLatch(1);
            ScheduledFuture<?> future = eqe.scheduleAtFixedRate(() -> {
                if (ai.incrementAndGet() == 5) {
                    completeLatch.countDown();
                }
            }, 20, 50, TimeUnit.MILLISECONDS);
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "Completion of enough iterations");
            assertFalse(future.isDone()); // they're never done
            // don't assert, because there's a small chance it would happen to be running
            future.cancel(false);
            try {
                future.get(5, TimeUnit.SECONDS);
                fail("Expected cancellation exception");
            } catch (CancellationException e) {
                // expected
            }
            eqe.shutdown();
            assertTrue(eqe.awaitTermination(5, TimeUnit.SECONDS), "Timely shutdown");
        } finally {
            eqe.shutdownNow();
        }
    }

    @Test
    public void testFixedDelayExecution() throws Exception {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        try {
            AtomicInteger ai = new AtomicInteger();
            CountDownLatch completeLatch = new CountDownLatch(1);
            ScheduledFuture<?> future = eqe.scheduleWithFixedDelay(() -> {
                if (ai.incrementAndGet() == 5) {
                    completeLatch.countDown();
                }
            }, 20, 50, TimeUnit.MILLISECONDS);
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "Completion of enough iterations");
            assertFalse(future.isDone()); // they're never done
            // don't assert, because there's a small chance it would happen to be running
            future.cancel(false);
            try {
                future.get(5, TimeUnit.SECONDS);
                fail("Expected cancellation exception");
            } catch (CancellationException e) {
                // expected
            }
            eqe.shutdown();
            assertTrue(eqe.awaitTermination(5, TimeUnit.SECONDS), "Timely shutdown");
        } finally {
            eqe.shutdownNow();
        }
    }

    @Test
    public void testThatFixedDelayTerminatesTask() {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        final ArrayList<LocalDateTime> times = new ArrayList<>();
        var r = new Runnable() {
            final CountDownLatch latch = new CountDownLatch(1);
            volatile ScheduledFuture<?> future;

            public void run() {
                times.add(LocalDateTime.now());
                if (times.size() >= 5) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    future.cancel(false);
                }
            }

            public void schedule() {
                future = eqe.scheduleWithFixedDelay(this, 0, 100, TimeUnit.MILLISECONDS);
            }
        };
        r.schedule();
        r.latch.countDown();
        assertThrows(CancellationException.class, () -> r.future.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void testCancelOnShutdown() throws Exception {
        EnhancedQueueExecutor eqe = new EnhancedQueueExecutor.Builder().build();
        try {
            ScheduledFuture<?> future = eqe.schedule(() -> fail("Should never run"), 1, TimeUnit.DAYS);
            eqe.shutdown();
            assertTrue(eqe.awaitTermination(5, TimeUnit.SECONDS), "Timely shutdown");
            try {
                future.get(5, TimeUnit.SECONDS);
                fail("Expected cancellation exception");
            } catch (CancellationException e) {
                // expected
            }
            assertTrue(future.isCancelled(), "Was cancelled on shutdown");
        } finally {
            eqe.shutdownNow();
        }
    }
}
