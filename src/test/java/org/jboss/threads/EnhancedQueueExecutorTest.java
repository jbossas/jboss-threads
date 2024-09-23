package org.jboss.threads;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EnhancedQueueExecutorTest {
    private int coreSize = 3;
    private int maxSize = coreSize * 2;
    private long keepaliveTimeMillis = 1000;

    class TestTask implements Runnable {
        private long sleepTime = 0;

        public TestTask withSleepTime(long sleepTime) {
            if (sleepTime > 0) {
                this.sleepTime = sleepTime;
            }
            return this;
        }

        @Override
        public void run() {
            try {
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testMaximumQueueSize() throws InterruptedException {
        var builder = (new EnhancedQueueExecutor.Builder())
                .setMaximumQueueSize(1)
                .setCorePoolSize(1)
                .setMaximumPoolSize(1);
        assertTrue(builder.getQueueLimited());
        var executor = builder.build();
        CountDownLatch executeEnqueuedTask = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        CountDownLatch enqueuedTask = new CountDownLatch(1);
        CountDownLatch executedEnqueuedTask = new CountDownLatch(1);
        executor.execute(() -> {
            count.incrementAndGet();
            try {
                enqueuedTask.countDown();
                executeEnqueuedTask.await();
            } catch (InterruptedException ignored) {
            }
        });
        enqueuedTask.await();
        assertEquals(1, count.get());
        assertEquals(0, executor.getQueueSize());
        // this is going to cause the queue size to be == 1
        executor.execute(() -> {
            count.incrementAndGet();
            executedEnqueuedTask.countDown();
        });
        assertEquals(1, count.get());
        assertEquals(1, executor.getQueueSize());
        try {
            executor.execute(count::incrementAndGet);
            fail("Expected RejectedExecutionException");
        } catch (RejectedExecutionException e) {
            // expected
        }
        assertEquals(1, count.get());
        assertEquals(1, executor.getQueueSize());
        executeEnqueuedTask.countDown();
        executedEnqueuedTask.await();
        assertEquals(2, count.get());
        assertEquals(0, executor.getQueueSize());
        executor.shutdown();
    }

    @Test
    public void testNoQueueLimit() throws InterruptedException {
        var builder = (new EnhancedQueueExecutor.Builder())
                .setQueueLimited(false)
                .setMaximumQueueSize(1)
                .setCorePoolSize(1)
                .setMaximumPoolSize(1);
        assertFalse(builder.getQueueLimited());
        var executor = builder.build();
        assertEquals(Integer.MAX_VALUE, executor.getMaximumQueueSize());
        CountDownLatch executeEnqueuedTasks = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        CountDownLatch enqueuedTask = new CountDownLatch(1);
        CountDownLatch executedEnqueuedTasks = new CountDownLatch(2);
        executor.execute(() -> {
            count.incrementAndGet();
            try {
                enqueuedTask.countDown();
                executeEnqueuedTasks.await();
            } catch (InterruptedException ignored) {
            }
        });
        enqueuedTask.await();
        executor.execute(() -> {
            count.incrementAndGet();
            executedEnqueuedTasks.countDown();
        });
        assertEquals(1, count.get());
        assertEquals(-1, executor.getQueueSize());
        executor.execute(() -> {
            count.incrementAndGet();
            executedEnqueuedTasks.countDown();
        });
        assertEquals(1, count.get());
        assertEquals(-1, executor.getQueueSize());
        executeEnqueuedTasks.countDown();
        executedEnqueuedTasks.await();
        assertEquals(3, count.get());
        assertEquals(-1, executor.getQueueSize());
        executor.shutdown();
    }

    /**
     * Test that unused threads are being reused. Scenario:
     * <ul>
     *     <li>max threads = 2x, core threads = x</li>
     *     <li>schedule x tasks, wait for tasks to finish</li>
     *     <li>schedule x tasks, expect pool size = x immediately after</li>
     * </ul>
     */
    @Test
    @Disabled("https://issues.jboss.org/browse/JBTHR-67")
    public void testThreadReuse() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();

        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask().withSleepTime(100));
        }
        assertEquals(executor.getPoolSize(), coreSize, "expected: == " + coreSize + ", actual: " + executor.getPoolSize());
        waitForActiveCount(executor, 0, 1000);
        assertEquals(executor.getPoolSize(), coreSize, "expected: == " + coreSize + ", actual: " + executor.getPoolSize());
        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask().withSleepTime(1000));
        }
        assertEquals(executor.getPoolSize(), coreSize, "expected: == " + coreSize + ", actual: " + executor.getPoolSize());
        executor.shutdown();
    }

    /**
     * Test that keepalive time is honored and threads above the core count are being removed when no tasks are
     * available.
     *
     * @throws InterruptedException
     * @throws TimeoutException
     */
    @Test
    @Disabled("https://issues.jboss.org/browse/JBTHR-67")
    public void testKeepaliveTime() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();

        assertTrue(executor.getPoolSize() <= coreSize, "expected: <=" + coreSize + ", actual: " + executor.getPoolSize());
        for (int i = 0; i < maxSize; i++) {
            executor.execute(new TestTask().withSleepTime(1000));
        }
        assertEquals(executor.getPoolSize(), maxSize, "expected: ==" + maxSize + ", actual: " + executor.getPoolSize());
        waitForActiveCount(executor, 0, 5000);
        waitForPoolSize(executor, coreSize, keepaliveTimeMillis * 2);
        executor.shutdown();
    }

    /**
     * Test that max size setting is honored. Test that keepalive time is ignored when core threads are the same as max
     * threads and core thread time out is disabled.
     */
    @Test
    @Disabled("https://issues.jboss.org/browse/JBTHR-67")
    public void testKeepaliveTime2() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(coreSize)
                .build();

        for (int i = 0; i < 2*coreSize; i++) {
            executor.execute(new TestTask().withSleepTime(100));
        }
        int currentThreads = executor.getPoolSize();
        assertEquals(currentThreads, coreSize, "expected: == " + coreSize + ", actual: " + currentThreads);
        waitForActiveCount(executor, 0, 5000);
        assertEquals(executor.getPoolSize(), currentThreads, "expected: == " + currentThreads + ", actual: " + executor.getPoolSize());
        executor.shutdown();
    }

    /**
     * Test the keepalive setting with core thread time out enabled.
     */
    @Test
    @Disabled("https://issues.jboss.org/browse/JBTHR-67")
    public void testKeepaliveTime3() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .allowCoreThreadTimeOut(true)
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();

        for (int i = 0; i < maxSize; i++) {
            executor.execute(new TestTask().withSleepTime(0));
        }
        waitForActiveCount(executor, 0, 5000);
        waitForPoolSize(executor, 0, keepaliveTimeMillis * 2);
        executor.shutdown();
    }

    /**
     * Tests that prestarting core threads starts exactly the core threads amount specified.
     */
    @Test
    public void testPrestartCoreThreads() {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();
        int prestarted = executor.prestartAllCoreThreads();
        assertEquals(prestarted, coreSize, "expected: == " + coreSize + ", actual: " + prestarted);
        assertEquals(executor.getPoolSize(), coreSize, "expected: == " + coreSize + ", actual: " + executor.getPoolSize());
        executor.shutdown();
    }

    @Test
    public void testStackDepth() throws InterruptedException {
        // Test exists to acknowledge changes which result in greater stack depth making stack traces
        // created within the executor more difficult to follow. This isn't something that we should
        // necessarily optimize for, rather something we should keep in mind when other options exist.
        final int expectedStackFrames = 6;
        assertStackDepth(new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(1)
                .setMaximumPoolSize(1)
                .build(), expectedStackFrames + 2);
        // Use a standard ThreadPoolExecutor as a baseline for comparison.
        assertStackDepth(Executors.newSingleThreadExecutor(), expectedStackFrames);
    }

    public void assertStackDepth(ExecutorService executor, int expectedStackFrames) throws InterruptedException {
        CountDownLatch initialTaskCompletionBlockingLatch = new CountDownLatch(1);
        AtomicInteger initialTaskStackFrames = new AtomicInteger();
        Runnable initialTask = new Runnable() {
            @Override
            public void run() {
                initialTaskStackFrames.set(new RuntimeException().getStackTrace().length);
                try {
                    initialTaskCompletionBlockingLatch.await();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        };
        CountDownLatch queuedTaskCompletionLatch = new CountDownLatch(1);
        AtomicInteger queuedTaskStackFrames = new AtomicInteger();
        Runnable queuedTask = new Runnable() {
            @Override
            public void run() {
                queuedTaskStackFrames.set(new RuntimeException().getStackTrace().length);
                queuedTaskCompletionLatch.countDown();
            }
        };
        try {
            executor.submit(initialTask);
            executor.submit(queuedTask);
            initialTaskCompletionBlockingLatch.countDown();
            queuedTaskCompletionLatch.await();
            assertEquals(expectedStackFrames, initialTaskStackFrames.get());
            assertEquals(expectedStackFrames, queuedTaskStackFrames.get());
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor failed to terminate");
        }
    }

    private void waitForPoolSize(EnhancedQueueExecutor executor, int expectedPoolSize, long waitMillis) throws TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis;
        long delayMillis = 100;

        do {
            if (executor.getPoolSize() == expectedPoolSize) {
                break;
            }
            Thread.sleep(delayMillis);
        } while (System.currentTimeMillis() + delayMillis < deadline);
        if (executor.getPoolSize() != expectedPoolSize) {
            throw new TimeoutException("Timed out waiting for pool size to become " + expectedPoolSize
                    + ", current pool size is " + executor.getPoolSize());
        }
    }

    private void waitForActiveCount(EnhancedQueueExecutor executor, int expectedActiveCount, long waitMillis) throws TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis;
        long delayMillis = 100;

        do {
            if (executor.getActiveCount() == expectedActiveCount) {
                break;
            }
            Thread.sleep(delayMillis);
        } while (System.currentTimeMillis() + delayMillis < deadline);
        if (executor.getActiveCount() != expectedActiveCount) {
            throw new TimeoutException("Timed out waiting for active count to become " + expectedActiveCount
                    + ", current active count is " + executor.getActiveCount());
        }
    }
}
