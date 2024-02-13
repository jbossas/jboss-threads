package org.jboss.threads;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for checking EnhancedTreadPoolExecutor
 * <p>
 */
public class EnhancedThreadQueueExecutorTestCase {

    private int coreSize = 4;
    private int maxSize = 7;
    private long keepaliveTimeMillis = 1000;
    private long defaultWaitTimeout = 5000;

    class TestTask implements Runnable {
        CountDownLatch exitLatch;
        CountDownLatch allThreadsRunningLatch;

        private TestTask(CountDownLatch exitLatch, CountDownLatch allThreadsRunningLatch) {
            this.exitLatch = exitLatch;
            this.allThreadsRunningLatch = allThreadsRunningLatch;
        }

        @Override
        public void run() {
            try {
                allThreadsRunningLatch.countDown();
                exitLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Test invalid values:
     * * Negative keepAlive, coreSize, maxSize
     * * maxSize > coreSize
     */
    @Test
    public void testInvalidValuesKeepAliveZero() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> new EnhancedQueueExecutor.Builder()
                .setKeepAliveTime(Duration.ZERO)
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build());
    }

    @Test
    public void testInvalidValuesKeepAliveNegative() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new EnhancedQueueExecutor.Builder()
                .setKeepAliveTime(Duration.ofMillis(-3456))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build());
    }

    @Test
    public void testInvalidValuesCoreSizeNegative() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new EnhancedQueueExecutor.Builder()
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(-5)
                .setMaximumPoolSize(maxSize)
                .build());
    }

    @Test
    public void testInvalidValuesMaxSizeNegative() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new EnhancedQueueExecutor.Builder()
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(-3)
                .build());
    }

    @Test
    public void testCoreSizeBiggerThanMaxSize() {
        int expectedCorePoolSize = 5;
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(2 * expectedCorePoolSize)
                .setMaximumPoolSize(expectedCorePoolSize)
                .build();
        assertEquals(expectedCorePoolSize, executor.getCorePoolSize(), "Core size should be automatically adjusted to be equal to max size in case it's bigger.");
    }

    /**
     * Test that unused threads are being reused. Scenario:
     * <ul>
     * <li>max threads = 2x, core threads = x</li>
     * <li>schedule x tasks, wait for tasks to finish</li>
     * <li>schedule x tasks, expect pool size = x immediately after</li>
     * </ul>
     */
    @Test
    public void testThreadReuse() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();

        CountDownLatch exitLatch = new CountDownLatch(1);
        CountDownLatch allThreadsRunningLatch = new CountDownLatch(coreSize);

        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask(exitLatch, allThreadsRunningLatch));
        }
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);
        exitLatch.countDown();
        waitForActiveCount(executor, 0, defaultWaitTimeout);
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);
        exitLatch = new CountDownLatch(1);
        allThreadsRunningLatch = new CountDownLatch(coreSize);
        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask(exitLatch, allThreadsRunningLatch));
        }
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        exitLatch.countDown();
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);
        executor.shutdown();
    }

    /**
     * Test thread reuse above core size
     * Scenario:
     * <ul>
     * <li>setKeepAlive=60 sec</li>
     * <li>max threads = 2x, core threads = x</li>
     * <li>schedule x tasks and wait to occupy all core threads </li>
     * <li>schedule one more task and let it finish</li>
     * <li>schedule one task and check that pool size is still x+1</li>
     * </ul>
     */
    @Test
    @Disabled("This test consistently fails, see JBTHR-67")
    public void testThreadReuseAboveCoreSize() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofSeconds(60))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();

        // submit 3 tasks to fill core size
        CountDownLatch exitLatch = new CountDownLatch(1);
        CountDownLatch allThreadsRunningLatch = new CountDownLatch(coreSize);
        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask(exitLatch, allThreadsRunningLatch));
        }
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);

        // submit one more task and allow it to finish
        CountDownLatch singleExitLatch = new CountDownLatch(1);
        CountDownLatch threadRunningLatch = new CountDownLatch(1);
        executor.execute(new TestTask(singleExitLatch, threadRunningLatch));
        threadRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS);
        waitForPoolSize(executor, coreSize + 1, defaultWaitTimeout);
        singleExitLatch.countDown();
        waitForActiveCount(executor, coreSize, defaultWaitTimeout);

        // now there are just core threads and one free thread, submit another task and check it's reused
        singleExitLatch = new CountDownLatch(1);
        threadRunningLatch = new CountDownLatch(1);
        executor.execute(new TestTask(singleExitLatch, threadRunningLatch));
        threadRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS);
        waitForPoolSize(executor, coreSize + 1, defaultWaitTimeout);
        singleExitLatch.countDown();

        // finish all
        exitLatch.countDown();
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
    @Disabled("This test consistently fails, see JBTHR-67")
    public void testKeepaliveTime() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .allowCoreThreadTimeOut(false)
                .build();

        CountDownLatch exitLatch = new CountDownLatch(1);
        CountDownLatch allThreadsRunningLatch = new CountDownLatch(coreSize);
        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask(exitLatch, allThreadsRunningLatch));
        }
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        CountDownLatch exitLatch2 = new CountDownLatch(1);
        CountDownLatch allThreadsRunningLatch2 = new CountDownLatch(maxSize - coreSize);
        for (int i = 0; i < (maxSize - coreSize); i++) {
            executor.execute(new TestTask(exitLatch2, allThreadsRunningLatch2));
        }
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        waitForPoolSize(executor, maxSize, defaultWaitTimeout);

        // finish core tasks and let timeout "core" threads
        exitLatch.countDown();
        waitForActiveCount(executor, maxSize - coreSize, defaultWaitTimeout);
        waitForPoolSize(executor, Math.max(coreSize, (maxSize - coreSize)), defaultWaitTimeout);
        exitLatch2.countDown();
        waitForActiveCount(executor, 0, defaultWaitTimeout);
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);
        executor.shutdown();
    }

    /**
     * Test that keepalive time is ignored when core threads are the same as max
     * threads and core thread time out is disabled.
     */
    @Test
    public void testKeepaliveTime2() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(coreSize)
                .build();

        CountDownLatch exitLatch = new CountDownLatch(1);
        CountDownLatch allThreadsRunningLatch = new CountDownLatch(coreSize);

        for (int i = 0; i < coreSize; i++) {
            executor.execute(new TestTask(exitLatch, allThreadsRunningLatch));
        }
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);
        exitLatch.countDown();
        waitForActiveCount(executor, 0, defaultWaitTimeout);
        waitForPoolSize(executor, coreSize, defaultWaitTimeout);
        executor.shutdown();
    }

    /**
     * Test the keepalive setting with core thread time out enabled.
     */
    @Test
    public void testKeepaliveTimeWithCoreThreadTimeoutEnabled() throws TimeoutException, InterruptedException {
        EnhancedQueueExecutor executor = (new EnhancedQueueExecutor.Builder())
                .setKeepAliveTime(Duration.ofMillis(keepaliveTimeMillis))
                .allowCoreThreadTimeOut(true)
                .setCorePoolSize(coreSize)
                .setMaximumPoolSize(maxSize)
                .build();

        CountDownLatch exitLatch = new CountDownLatch(1);
        CountDownLatch allThreadsRunningLatch = new CountDownLatch(maxSize);

        for (int i = 0; i < maxSize; i++) {
            executor.execute(new TestTask(exitLatch, allThreadsRunningLatch));
        }
        // this will make sure that all thread are running at the same time
        assertTrue(allThreadsRunningLatch.await(defaultWaitTimeout, TimeUnit.MILLISECONDS),
                "Not all threads were running. They were most likely not scheduled for execution.");
        exitLatch.countDown();
        waitForActiveCount(executor, 0, defaultWaitTimeout);
        waitForPoolSize(executor, 0, defaultWaitTimeout);
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
        assertEquals(coreSize, prestarted, "expected: == " + coreSize + ", actual: " + prestarted);
        assertEquals(coreSize, executor.getPoolSize(), "expected: == " + coreSize + ", actual: " + executor.getPoolSize());
        executor.shutdown();
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

    @Test
    public void testEnhancedExecutorShutdownNoTasks() throws Exception {
        final CountDownLatch terminateLatch = new CountDownLatch(1);
        EnhancedQueueExecutor executor = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(10)
                .setKeepAliveTime(Duration.ofNanos(1))
                .setTerminationTask(new Runnable() {
                    @Override
                    public void run() {
                        terminateLatch.countDown();
                    }
                })
                .build();

        executor.shutdown();
        assertTrue(terminateLatch.await(10, TimeUnit.SECONDS));
    }

    @Test //JBTHR-50
    public void testEnhancedExecutorShutdown() throws Exception {
        final CountDownLatch terminateLatch = new CountDownLatch(1);
        EnhancedQueueExecutor executor = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(10)
                .setKeepAliveTime(Duration.ofNanos(1))
                .setTerminationTask(new Runnable() {
                    @Override
                    public void run() {
                        terminateLatch.countDown();
                    }
                })
                .build();

        for (int i = 0; i < 10000; ++i) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            });
        }
        executor.shutdown();
        assertTrue(terminateLatch.await(10, TimeUnit.SECONDS));
    }
}
