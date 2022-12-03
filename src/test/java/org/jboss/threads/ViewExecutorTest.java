package org.jboss.threads;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
        ve.shutdown();
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

    // TaskWrapper instances are relatively small and take a long time to knock over a JVM,
    // however when they aren't collected properly they will cause a GC spiral for much longer
    // than ten seconds. Unfortunately this test is impacted by hardware changes and may flake
    // (or erroneously pass on fast enough hardware).
    @Test
    @Timeout(10_000)
    public void testViewExecutorMemoryOverhead() {
        Executor directExecutor = new Executor() {
            @Override
            public void execute(Runnable command) {
                try {
                    command.run();
                } catch (Throwable t) {
                    Thread currentThread = Thread.currentThread();
                    currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, t);
                }
            }
        };
        ExecutorService executorService = ViewExecutor.builder(directExecutor).build();
        for (long i = 0; i < 20_000_000L; i++) {
            executorService.execute(JBossExecutors.nullRunnable());
        }
        executorService.shutdown();
        assertTrue(executorService.isTerminated());
    }

    @Test
    public void testSameThreadDelegateDoesNotDeadlock() throws InterruptedException {
        Executor direct = Runnable::run;
        AtomicInteger completed = new AtomicInteger();
        ExecutorService view = ViewExecutor.builder(direct).setQueueLimit(1).setMaxSize(1).build();
        ExecutorService submittingExecutor = Executors.newCachedThreadPool();
        try {
            submittingExecutor.execute(() -> view.execute(() -> view.execute(completed::incrementAndGet)));
            Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> assertEquals(1, completed.get()));
            view.shutdown();
            assertTrue(view.awaitTermination(100, TimeUnit.SECONDS));
        } finally {
            submittingExecutor.shutdown();
            assertTrue(submittingExecutor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testDelegateQueueProcessingRejection() throws InterruptedException {
        // When the active task pulls from the queue, submitting the task to the delegate will fail because it's
        // already consuming the only available thread. The task should be handled on the same thread.
        CountDownLatch taskLatch = new CountDownLatch(1);
        // One permit, throws RejectedExecutionException if a second task is provided
        ExecutorService delegate = new ThreadPoolExecutor(0, 1,
                5, TimeUnit.SECONDS, new SynchronousQueue<>());
        try {
            ExecutorService view = ViewExecutor.builder(delegate).setQueueLimit(1).setMaxSize(1).build();
            List<Throwable> throwables = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 2; i++) {
                view.execute(() -> {
                    try {
                        // latch used to ensure the second task is queued, otherwise the first task may complete
                        // before the second is submitted.
                        taskLatch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    throwables.add(new RuntimeException("task trace"));
                });
            }
            taskLatch.countDown();
            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        assertEquals(2, throwables.size());
                        // The stack size mustn't grow with each queued task, otherwise processing will eventually
                        // fail running out of stack space.
                        assertEquals(throwables.get(0).getStackTrace().length, throwables.get(1).getStackTrace().length);
                    });
        } finally {
            delegate.shutdown();
            assertTrue(delegate.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(5_000)
    public void testDelegateQueueProcessingRejectionTaskIsInterrupted() throws InterruptedException {
        // Subsequent queued tasks run by the same wrapper should support interruption
        CountDownLatch firstTaskLatch = new CountDownLatch(1);
        // One permit, throws RejectedExecutionException if a second task is provided
        ExecutorService delegate = new ThreadPoolExecutor(0, 1,
                5, TimeUnit.SECONDS, new SynchronousQueue<>());
        try {
            ExecutorService view = ViewExecutor.builder(delegate).setQueueLimit(1).setMaxSize(1).build();
            view.submit(() -> {
                // latch used to ensure the second task is queued, otherwise the first task may complete
                // before the second is submitted.
                firstTaskLatch.await();
                return null;
            });
            AtomicBoolean interrupted = new AtomicBoolean();
            CountDownLatch secondTaskStartedLatch = new CountDownLatch(1);
            view.execute(() -> {
                secondTaskStartedLatch.countDown();
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            });
            firstTaskLatch.countDown();
            secondTaskStartedLatch.await();
            view.shutdownNow();
            assertTrue(view.awaitTermination(200, TimeUnit.MILLISECONDS));
            assertTrue(interrupted.get());
        } finally {
            delegate.shutdown();
            assertTrue(delegate.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testTestSlowExecuteInParallelWithEnqueue() throws InterruptedException {
        // When a thread (threadA) submits a task to a delegate, a parallel task should not be able to
        // successfully enqueue work. If an enqueue succeeded but delegate.execute did not, the queue
        // would become detached from the executor, and never flush.
        CountDownLatch taskLatch = new CountDownLatch(1);
        // One permit, throws RejectedExecutionException if a second task is provided
        Executor delegate = new Executor() {
            private final AtomicBoolean firstCall = new AtomicBoolean(true);
            @Override
            public void execute(Runnable command) {
                if (firstCall.getAndSet(false)) {
                    try {
                        taskLatch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                throw new RejectedExecutionException();
            }
        };

        ExecutorService testRunner = Executors.newCachedThreadPool();
        ExecutorService view = ViewExecutor.builder(delegate).setQueueLimit(1).setMaxSize(1).build();
        List<Throwable> throwables = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 2; i++) {
            testRunner.execute(() -> {
                try {
                    view.execute(NullRunnable.getInstance());
                    throw new AssertionError("should not be reached");
                } catch (Throwable t) {
                    throwables.add(t);
                }
            });
        }
        assertEquals(0, throwables.size());
        taskLatch.countDown();
        testRunner.shutdown();
        assertTrue(testRunner.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals(2, throwables.size());
        for (Throwable throwable : throwables) {
            assertTrue(throwable instanceof RejectedExecutionException);
        }
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
