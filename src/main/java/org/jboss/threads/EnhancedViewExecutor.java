/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.threads;

import org.jboss.logging.Logger;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.Nullable;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.common.lock.Locks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.jboss.threads.JBossExecutors.unsafe;

/**
 * A View Executor implementation which avoids lock contention in the common path. This allows us to
 * provide references to the same underlying pool of threads to different consumers and utilize distinct
 * instrumentation without duplicating resources. This implementation is optimized to avoid locking.
 *
 * @author <a href="mailto:ckozak@ckozak.net">Carter Kozak</a>
 */
final class EnhancedViewExecutor extends ViewExecutor {
    private static final Logger log = Logger.getLogger("org.jboss.threads.view-executor");
    private static final long stateOffset;
    static {
        try {
            stateOffset = unsafe.objectFieldOffset(EnhancedViewExecutor.class.getDeclaredField("state"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    private static final int QUEUE_FAILURE_LOG_INTERVAL =
            readIntPropertyPrefixed("queue.failure.log.interval", 1_000_000);
    private static final int MAX_QUEUE_SPINS =
            readIntPropertyPrefixed("queue.poll.spins", ProcessorInfo.availableProcessors() == 1 ? 0 : 128);

    private static final long SHUTDOWN_MASK = 1L << 63;
    private static final long ACTIVE_COUNT_MASK = (1L << 31) - 1;
    private static final int QUEUED_SIZE_OFFSET = 31;
    private static final long QUEUED_SIZE_MASK = ((1L << 31) - 1) << QUEUED_SIZE_OFFSET;

    private final Executor delegate;
    private final int maxCount;
    private final int queueLimit;

    /**
     * The execute lock is only needed necessary to guard from submitting the first queued task before the last
     * non-queued (active == maxCount) task can be submitted to the executor. Locks are not used when the queue
     * is disabled.
     * This lock is required to prevent tasks from beginning to queue while tasks that have acquired permits
     * have not been successfully submitted to the delegate executor. In this case the queue may be left in an
     * unrecoverable state if the delegate executor rejects input.
     */
    @Nullable
    private final Lock executeLock;
    private final Object shutdownLock = new Object();
    private final Set<EnhancedViewExecutorRunnable> activeRunnables = ConcurrentHashMap.newKeySet();

    /**
     * Queue handling:
     *
     * The queue must only be modified after a successful CAS {@link #state} update.
     * After the queue length component of {@link #state} has been decremented a receiver
     * <i>may</i> need to wait for the producer to successfully add an item to the queue.
     */
    private final Queue<EnhancedViewExecutorRunnable> queue = new ConcurrentLinkedQueue<>();

    /**
     * State structure.
     *
     * <ul>
     *   <li>Bit 00..30: Number of active tasks (unsigned)
     *   <li>Bit 31..61: Number of queued tasks (unsigned)
     *   <li>Bit 62: unused; always zero
     *   <li>Bit 63: executor shutdown state; 0 = shutdown has not been requested
     * </ul>
     */
    @SuppressWarnings("unused")
    private volatile long state;
    private volatile boolean interrupted = false;

    EnhancedViewExecutor(
            final Executor delegate,
            final int maxCount,
            final int queueLimit,
            final Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.delegate = Assert.checkNotNullParam("delegate", delegate);
        this.maxCount = maxCount;
        this.queueLimit = queueLimit;
        // The lock is only necessary when queueing is possible
        this.executeLock = queueLimit == 0
                ? null
                // Lock must be reentrant to handle same-thread executors or CallerRunsPolicy
                : Locks.reentrantLock();
        this.setExceptionHandler(uncaughtExceptionHandler);
    }

    @Override
    public void shutdown(boolean interrupt) {
        for (;;) {
            long stateSnapshot = state;
            // Avoid unnecessary work if shutdown is already set.
            if (isShutdown(stateSnapshot)) {
                break; // nothing to do
            }
            long newState = stateSnapshot | SHUTDOWN_MASK;
            if (compareAndSwapState(stateSnapshot, newState)) {
                // state change sh1:
                //   state(snapshot) ← state(snapshot) | shutdown
                // succeeds: -
                // preconditions: -
                // post-actions (succeed):
                //   If the resulting state is terminal, notify waiters and run the termination task
                // post-actions (fail):
                //   repeat state change until success or break
                notifyWaitersIfTerminated(newState);
                break;
            }
        }
        if (interrupt) {
            interrupted = true;
            activeRunnables.forEach(EnhancedViewExecutorRunnable::interrupt);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        int queuedElementsToRemove;
        for (;;) {
            long stateSnapshot = state;
            // If shutdown is already set, the queue is still expected to be drained when shutdownNow is invoked.
            queuedElementsToRemove = getQueueSize(stateSnapshot);
            if (isShutdown(stateSnapshot) && queuedElementsToRemove == 0) {
                break; // nothing to do
            }
            // state change sh2:
            //   state(snapshot).shutdown ← true
            //   state(snapshot).queueSize ← zero
            // succeeds: -
            // preconditions: -
            // post-actions (succeed):
            //   If the resulting state is terminal, notify waiters and run the termination task
            //   Interrupt active threads
            //   Drain the queue by the value of state(snapshot).queueSize
            // post-actions (fail):
            //   repeat state change until success or break
            long newState = (stateSnapshot | SHUTDOWN_MASK) & ~QUEUED_SIZE_MASK;
            if (compareAndSwapState(stateSnapshot, newState)) {
                notifyWaitersIfTerminated(newState);
                break;
            }
        }
        interrupted = true;
        activeRunnables.forEach(EnhancedViewExecutorRunnable::interrupt);
        if (queuedElementsToRemove > 0) {
            ArrayList<Runnable> neverCommencedExecution = new ArrayList<>(queuedElementsToRemove);
            for (int i = 0; i < queuedElementsToRemove; i++) {
                neverCommencedExecution.add(blockingTake().delegate);
            }
            return neverCommencedExecution;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return isShutdown(state);
    }

    private static boolean isShutdown(long state) {
        return (state & SHUTDOWN_MASK) != 0;
    }

    @Override
    public boolean isTerminated() {
        return isTerminated(state);
    }

    private static boolean isTerminated(long state) {
        // SHUTDOWN_MASK is set with neither queued nor active tasks.
        return state == SHUTDOWN_MASK;
    }

    private void notifyWaitersIfTerminated(long stateSnapshot) {
        if (isTerminated(stateSnapshot)) {
            synchronized (shutdownLock) {
                shutdownLock.notifyAll();
            }
            // The queue must be empty when the executor is terminated.
            // If this fails, something has not set state properly.
            assert queue.isEmpty();
            runTermination();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long remainingNanos = unit.toNanos(timeout);
        // Use the system precise clock to avoid issues resulting from time changes.
        long now = System.nanoTime();
        synchronized (shutdownLock) {
            while (!isTerminated()) {
                remainingNanos -= Math.max(-now + (now = System.nanoTime()), 0L);
                long remainingMillis = TimeUnit.MILLISECONDS.convert(remainingNanos, TimeUnit.NANOSECONDS);
                if (remainingMillis <= 0) {
                    return false;
                }
                shutdownLock.wait(remainingMillis);
            }
            return true;
        }
    }

    @Override
    public void execute(Runnable task) {
        Assert.checkNotNullParam("task", task);
        final EnhancedViewExecutorRunnable decoratedTask = new EnhancedViewExecutorRunnable(
                task, JBossExecutors.getContextClassLoader(Thread.currentThread()));
        final int maxCount = this.maxCount;
        final int queueLimit = this.queueLimit;
        Lock executeLock = null;
        try {
            for (;;) {
                long stateSnapshot = state;
                if (isShutdown(stateSnapshot)) {
                    throw new RejectedExecutionException("Executor has been shut down");
                }

                int activeCount = getActiveCount(stateSnapshot);
                int currentQueueSize = getQueueSize(stateSnapshot);

                if (queueLimit != 0 && executeLock == null && currentQueueSize == 0 && activeCount >= (maxCount - 1)) {
                    executeLock = this.executeLock;
                    executeLock.lock();
                    // Try again after the lock has been acquired
                    continue;
                }

                if (activeCount < maxCount) {
                    assert getQueueSize(stateSnapshot) == 0;
                    long updatedActiveCount = activeCount + 1;
                    // state change ex1:
                    //   state(snapshot).activeCount ← state(snapshot).activeCount + 1
                    // succeeds: -
                    // preconditions:
                    //   ! state.shutdown
                    //   state(snapshot).activeCount < maxCount
                    //   state(snapshot).queueSize == 0
                    // post-actions (succeed):
                    //   task is executed on the delegate executor
                    // post-actions (fail):
                    //   retry with new state(snapshot)
                    if (compareAndSwapState(stateSnapshot, updatedActiveCount | (stateSnapshot & ~ACTIVE_COUNT_MASK))) {
                        try {
                            delegate.execute(decoratedTask);
                            return;
                        } catch (Throwable t) {
                            // The active count must be reduced when execute fails
                            taskComplete(false);
                            throw t;
                        }
                    }
                    continue;
                }
                if (currentQueueSize < queueLimit) {
                    assert activeCount == maxCount;
                    long updatedQueueSize = currentQueueSize + 1;
                    // state change ex2:
                    //   state(snapshot).queueSize ← state(snapshot).queueSize + 1
                    // succeeds: -
                    // preconditions:
                    //   ! state.shutdown
                    //   state(snapshot).queueSize < queueLimit
                    //   state(snapshot).activeCount == maxCount
                    // post-actions (succeed):
                    //   task is enqueued
                    // post-actions (fail):
                    //   retry with new state(snapshot)
                    if (compareAndSwapState(stateSnapshot, (updatedQueueSize << QUEUED_SIZE_OFFSET) | (stateSnapshot & ~QUEUED_SIZE_MASK))) {
                        enqueue(decoratedTask);
                        // Work is complete.
                        return;
                    } else {
                        continue;
                    }
                }
                throw new RejectedExecutionException("No executor queue space remaining");
            }
        } finally {
            if (executeLock != null) executeLock.unlock();
        }
        // Assert.unreachableCode();
    }

    /**
     * Only called after the queue size component of {@link #state} has been updated.
     * At this point there may already be a thread waiting for the queued task after decrementing
     * the queued count, so it's vital that this operation succeeds.
     */
    private void enqueue(EnhancedViewExecutorRunnable task) {
        int failures = 0;
        for (;;) {
            try {
                if (queue.offer(task)) return;
                throw new RejectedExecutionException("Task was rejected by the queue. This should never happen.");
            } catch (Throwable t) {
                // Enqueue can safely fail without leaving an executor thread spinning waiting for the queued element
                // if the queue size can still be decremented.
                if (decrementQueueSize()) throw t;
                if (failures == 0) log.error("Failed to submit a task to the queue. This should never happen.", t);
            }
            if (++failures >= QUEUE_FAILURE_LOG_INTERVAL) {
                failures = 0;
            }
            // try again, hope for the best
            Thread.yield();
        }
    }

    private boolean decrementQueueSize() {
        for (;;) {
            long snapshot = state;
            int queueSize = getQueueSize(snapshot);
            if (queueSize == 0) {
                return false;
            }
            long newQueueSize = queueSize - 1;
            long newState = (snapshot & ~QUEUED_SIZE_MASK) | (newQueueSize << QUEUED_SIZE_OFFSET);
            if (compareAndSwapState(snapshot, newState)) {
                notifyWaitersIfTerminated(newState);
                return true;
            }
        }
    }

    /** Tasks are enqueued after a successful state update, they may not be immediately available at this point. */
    private EnhancedViewExecutorRunnable blockingTake() {
        int spins = 0;
        int attempts = 0;
        for (;;) {
            try {
                EnhancedViewExecutorRunnable result = queue.poll();
                if (result != null) {
                    return result;
                }
                // try again
                if (spins < MAX_QUEUE_SPINS) {
                    spins++;
                    JDKSpecific.onSpinWait();
                } else {
                    Thread.yield();
                }

            } catch (Throwable t) {
                if (attempts == 0) {
                    log.error("Failed to read from the queue. This should never happen.", t);
                }
                if (++attempts >= QUEUE_FAILURE_LOG_INTERVAL) {
                    attempts = 0;
                }
                // try again, hope for the best
                Thread.yield();
            }
        }
    }

    // Returns a EnhancedViewExecutorRunnable which must be executed if it couldn't be submitted to the executor
    // if allowQueuePolling is false, only null can be returned.
    private EnhancedViewExecutorRunnable taskComplete(boolean allowQueuePolling) {
        for (;;) {
            long stateSnapshot = state;
            int queueSize = getQueueSize(stateSnapshot);
            if (queueSize > 0 && allowQueuePolling) {
                // state change tc1:
                //   state(snapshot).queueSize ← state(snapshot).queueSize - 1
                // succeeds: ex2
                // preconditions:
                //   allowQueuePolling is true
                //   state(snapshot).queueSize > 0
                // post-actions (succeed):
                //   the queued task is executed
                // post-actions (fail):
                //   retry with new state(snapshot)
                long updatedQueueSize = queueSize - 1;
                if (compareAndSwapState(stateSnapshot, (updatedQueueSize << QUEUED_SIZE_OFFSET) | (stateSnapshot & ~QUEUED_SIZE_MASK))) {
                    // no need to check if waiters must be notified, the enqueued task is considered active.
                    EnhancedViewExecutorRunnable task = blockingTake();
                    try {
                        delegate.execute(task);
                        return null;
                    } catch (Throwable t) {
                        return task;
                    }
                }
            } else {
                // state change tc2:
                //   state(snapshot).activeCount ← state(snapshot).activeCount - 1
                // succeeds: ex1
                // preconditions:
                //   state(snapshot).activeCount > 0
                // post-actions (succeed):
                //   Waiters must be notified if the resulting state is terminal
                // post-actions (fail):
                //   retry with new state(snapshot)
                long newState = (getActiveCount(stateSnapshot) - 1) | (stateSnapshot & ~ACTIVE_COUNT_MASK);
                if (compareAndSwapState(stateSnapshot, newState)) {
                    notifyWaitersIfTerminated(newState);
                    return null;
                }
            }
        }
    }

    private static int getActiveCount(long state) {
        return (int) (state & ACTIVE_COUNT_MASK);
    }

    private static int getQueueSize(long state) {
        return (int) ((state & QUEUED_SIZE_MASK) >> QUEUED_SIZE_OFFSET);
    }

    private boolean compareAndSwapState(long expected, long update) {
        return unsafe.compareAndSwapLong(this, stateOffset, expected, update);
    }

    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler handler = getExceptionHandler();
        if (handler != null) {
            return handler;
        }
        // If not uncaught exception handler is set, use the current threads existing handler if present.
        // Otherwise use the default JBoss logging exception handler.
        Thread.UncaughtExceptionHandler threadHandler =
                Thread.currentThread().getUncaughtExceptionHandler();
        return threadHandler != null ? threadHandler : JBossExecutors.loggingExceptionHandler();
    }

    @Override
    public String toString() {
        long snapshot = state;
        int activeTasks = getActiveCount(snapshot);
        int queueSize = getQueueSize(snapshot);
        boolean shutdown = isShutdown(snapshot);
        boolean terminated = isTerminated(snapshot);
        return "EnhancedViewExecutor{delegate=" + delegate
                + ", active=" + activeTasks
                + ", queued=" + queueSize
                + ", shutdown=" + shutdown
                + ", terminated=" + terminated
                + '}';
    }

    private final class EnhancedViewExecutorRunnable implements Runnable {

        private Runnable delegate;
        private ClassLoader contextClassLoader;

        @Nullable
        private volatile Thread thread;

        EnhancedViewExecutorRunnable(Runnable delegate, ClassLoader contextClassLoader) {
            this.delegate = delegate;
            this.contextClassLoader = contextClassLoader;
        }

        @Override
        public void run() {
            // Task is almost always 'this'. If the delegate executor has rejects attempts to submit work,
            // existing active threads handle it, potentially violating FIFO order on the delegate queue.
            EnhancedViewExecutorRunnable task = this;
            // Loop is only used when the executor rejects tasks
            while (task != null) {
                Thread currentThread = Thread.currentThread();
                Set<EnhancedViewExecutorRunnable> runnables = activeRunnables;
                task.thread = currentThread;
                try {
                    runnables.add(task);
                    if (interrupted) {
                        // shutdownNow may have been invoked after this task was submitted
                        // but prior to activeRunnables.add(this).
                        currentThread.interrupt();
                    }
                    Runnable runnable = task.delegate;
                    ClassLoader loader = task.contextClassLoader;
                    // Avoid gc pressure from large runnables
                    task.delegate = null;
                    task.contextClassLoader = null;
                    // Matches ContextClassLoaderSavingRunnable without the allocation overhead or
                    // additional stack frames.
                    ClassLoader old = JBossExecutors.getAndSetContextClassLoader(currentThread, loader);
                    try {
                        runnable.run();
                    } finally {
                        JBossExecutors.setContextClassLoader(currentThread, old);
                    }
                } catch (Throwable t) {
                    // The uncaught exception handler should be called on the current thread in order to log
                    // using the updated thread name based on nameFunction.
                    uncaughtExceptionHandler().uncaughtException(task.thread, t);
                } finally {
                    runnables.remove(task);
                    // Synchronization is important to avoid racily reading the current thread and interrupting
                    // it after this task completes and a task from another view has begun execution.
                    synchronized (task) {
                        task.thread = null;
                    }
                    task = taskComplete(true);
                }
            }
        }

        synchronized void interrupt() {
            Thread taskThread = this.thread;
            if (taskThread != null) {
                taskThread.interrupt();
            }
        }

        @Override
        public String toString() {
            return "EnhancedViewExecutorRunnable{" + delegate + '}';
        }
    }
}
