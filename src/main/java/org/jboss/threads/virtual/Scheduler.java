package org.jboss.threads.virtual;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import io.smallrye.common.annotation.Experimental;
import io.smallrye.common.constraint.Assert;
import jdk.internal.vm.ThreadContainer;
import org.jboss.threads.EnhancedQueueExecutor;

/**
 * A virtual thread scheduler.
 */
@Experimental("Experimental virtual thread support")
public final class Scheduler implements Executor {
    private final EnhancedQueueExecutor blockingPool;
    private final Container container = new Container();
    private final AtomicInteger eventLoopIdx = new AtomicInteger(1);
    private final AtomicLong threadIdx = new AtomicLong(1);
    private final PoolDispatcher poolDispatcher = new PoolDispatcher();

    /**
     * Construct a new instance.
     *
     * @param blockingPool the blocking pool to use (must not be {@code null})
     */
    Scheduler(final EnhancedQueueExecutor blockingPool) {
        this.blockingPool = Assert.checkNotNullParam("blockingPool", blockingPool);
    }

    /**
     * Construct and start a new event loop thread for this scheduler.
     *
     * @param eventLoop the event loop to use (must not be {@code null})
     * @return the new event loop thread (not {@code null})
     * @throws NullPointerException if the factory returned a {@code null} event loop
     */
    public EventLoopThread newEventLoopThread(EventLoop eventLoop) {
        Assert.checkNotNullParam("eventLoop", eventLoop);
        EventLoopThread eventLoopThread = new EventLoopThread(this, eventLoopIdx.getAndIncrement(), eventLoop);
        eventLoopThread.start();
        return eventLoopThread;
    }

    /**
     * Construct a new instance.
     *
     * @param blockingPool the blocking pool to use (must not be {@code null})
     * @return the new scheduler (not {@code null})
     */
    public static Scheduler create(EnhancedQueueExecutor blockingPool) {
        return new Scheduler(Assert.checkNotNullParam("blockingPool", blockingPool));
    }

    /**
     * Execute the given task in a new virtual thread managed by this scheduler, initially scheduled as a worker
     * (CPU-bound) task.
     *
     * @param command the runnable task
     */
    public void execute(final Runnable command) {
        new UserThreadScheduler(this, Assert.checkNotNullParam("command", command), threadIdx.getAndIncrement()).start();
    }

    void executeOnEventLoop(final EventLoopThread eventLoopThread, final Runnable command) {
        new UserThreadScheduler(this, command, threadIdx.getAndIncrement(), eventLoopThread).start();
    }

    /**
     * {@return the current event loop carrier thread, or {@code null} if the current thread is not currently carried by an event loop thread}
     */
    public static EventLoopThread currentEventLoopThread() {
        if (Thread.currentThread().isVirtual() && Access.currentCarrier() instanceof EventLoopThread elt) {
            return elt;
        } else {
            return null;
        }
    }

    /**
     * Indicate that the current thread is going to be performing I/O-intensive operations
     * with relatively little CPU or native usage.
     * After this method returns, the current thread will be carried by the given event loop thread.
     *
     * @param eventLoopThread the event loop thread to resume on (must not be {@code null})
     */
    public static void resumeOn(EventLoopThread eventLoopThread) {
        Assert.checkNotNullParam("eventLoopThread", eventLoopThread);
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts && ts.scheduler() == eventLoopThread.owner()) {
            ts.resumeOn(eventLoopThread.dispatcher());
        } else {
            throw new IllegalArgumentException("Event loop thread " + eventLoopThread + " does not belong to the same scheduler as the current thread");
        }
    }

    /**
     * Indicate that the current thread is going to be performing native or CPU-intensive operations
     * with relatively little I/O usage.
     * After this method returns, the current thread will be carried by a pool thread.
     */
    public static void resumeOnPool() {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts) {
            ts.resumeOn(ts.scheduler().poolDispatcher());
        }
    }

    /**
     * Park and resume on the given event loop thread.
     * After this method returns, the current thread will be carried by the given event loop thread.
     *
     * @param eventLoopThread the event loop thread to resume on (must not be {@code null})
     * @see LockSupport#park()
     */
    public static void parkAndResumeOn(EventLoopThread eventLoopThread) {
        Assert.checkNotNullParam("eventLoopThread", eventLoopThread);
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts && ts.scheduler() == eventLoopThread.owner()) {
            ts.parkAndResumeOn(null, eventLoopThread.dispatcher());
        } else {
            throw new IllegalArgumentException("Event loop thread " + eventLoopThread + " does not belong to the same scheduler as the current thread");
        }
    }

    /**
     * Park and resume on the given event loop thread.
     * After this method returns, the current thread will be carried by the given event loop thread.
     *
     * @param eventLoopThread the event loop thread to resume on (must not be {@code null})
     * @param blocker the blocker object to register
     * @see LockSupport#park(Object)
     */
    public static void parkAndResumeOn(EventLoopThread eventLoopThread, Object blocker) {
        Assert.checkNotNullParam("eventLoopThread", eventLoopThread);
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts && ts.scheduler() == eventLoopThread.owner()) {
            ts.parkAndResumeOn(blocker, eventLoopThread.dispatcher());
        } else {
            throw new IllegalArgumentException("Event loop thread " + eventLoopThread + " does not belong to the same scheduler as the current thread");
        }
    }

    /**
     * Park and resume on the blocking pool.
     * After this method returns, the current thread will be carried by a pool thread.
     * @see LockSupport#park()
     */
    public static void parkAndResumeOnPool() {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts) {
            ts.parkAndResumeOn(null, ts.scheduler().poolDispatcher());
        }
    }

    /**
     * Park and resume on the blocking pool.
     * After this method returns, the current thread will be carried by a pool thread.
     *
     * @param blocker the blocker object to register
     * @see LockSupport#park(Object)
     */
    public static void parkAndResumeOnPool(Object blocker) {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts) {
            ts.parkAndResumeOn(blocker, ts.scheduler().poolDispatcher());
        }
    }

    /**
     * Park and resume on the given event loop thread.
     * After this method returns, the current thread will be carried by the given event loop thread.
     *
     * @param eventLoopThread the event loop thread to resume on (must not be {@code null})
     * @param nanos the number of nanoseconds to park for
     * @see LockSupport#parkNanos(long)
     */
    public static void parkNanosAndResumeOn(EventLoopThread eventLoopThread, long nanos) {
        Assert.checkNotNullParam("eventLoopThread", eventLoopThread);
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts && ts.scheduler() == eventLoopThread.owner()) {
            ts.parkNanosAndResumeOn(null, nanos, eventLoopThread.dispatcher());
        } else {
            throw new IllegalArgumentException("Event loop thread " + eventLoopThread + " does not belong to the same scheduler as the current thread");
        }
    }

    /**
     * Park and resume on the given event loop thread.
     * After this method returns, the current thread will be carried by the given event loop thread.
     *
     * @param eventLoopThread the event loop thread to resume on (must not be {@code null})
     * @param blocker the blocker object to register (see {@link LockSupport#park(Object)})
     * @param nanos the number of nanoseconds to park for
     * @see LockSupport#parkNanos(Object, long)
     */
    public static void parkNanosAndResumeOn(EventLoopThread eventLoopThread, Object blocker, long nanos) {
        Assert.checkNotNullParam("eventLoopThread", eventLoopThread);
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts && ts.scheduler() == eventLoopThread.owner()) {
            ts.parkNanosAndResumeOn(blocker, nanos, eventLoopThread.dispatcher());
        } else {
            throw new IllegalArgumentException("Event loop thread " + eventLoopThread + " does not belong to the same scheduler as the current thread");
        }
    }

    /**
     * Park and resume on the blocking pool.
     * After this method returns, the current thread will be carried by a pool thread.
     *
     * @param nanos the number of nanoseconds to park for
     * @see LockSupport#parkNanos(long)
     */
    public static void parkNanosAndResumeOnPool(long nanos) {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts) {
            ts.parkNanosAndResumeOn(null, nanos, ts.scheduler().poolDispatcher());
        }
    }

    /**
     * Park and resume on the blocking pool.
     * After this method returns, the current thread will be carried by a pool thread.
     *
     * @param blocker the blocker object to register
     * @param nanos the number of nanoseconds to park for
     * @see LockSupport#parkNanos(Object, long)
     */
    public static void parkNanosAndResumeOnPool(Object blocker, long nanos) {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof UserThreadScheduler ts) {
            ts.parkNanosAndResumeOn(blocker, nanos, ts.scheduler().poolDispatcher());
        }
    }

    /**
     * Yield execution to any task is already waiting or will start waiting within the next {@code nanos} nanoseconds.
     * If no tasks remain within the given criteria, the current thread will resume.
     *
     * @param nanos the number of nanoseconds to attempt to yield for
     */
    public static void yieldNanos(long nanos) {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof ThreadScheduler ts) {
            ts.delayBy(Math.max(0, nanos));
        }
        Thread.yield();
    }

    /**
     * {@return the priority of the current thread or virtual thread}
     */
    public static int currentPriority() {
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof ThreadScheduler ts) {
            return ts.priority();
        } else {
            return thread.getPriority();
        }
    }

    /**
     * Change the priority of the current virtual thread, if possible.
     *
     * @param newPriority the new virtual thread priority
     */
    public static void changePriority(int newPriority) {
        newPriority = Math.min(Math.max(newPriority, Thread.MIN_PRIORITY), Thread.MAX_PRIORITY);
        Thread thread = Thread.currentThread();
        if (thread.isVirtual() && Access.schedulerOf(thread) instanceof ThreadScheduler ts) {
            int old = ts.priority();
            if (newPriority != old) {
                // apply new priority
                ts.setPriority(newPriority);
                Thread.yield();
            }
        } else {
            thread.setPriority(newPriority);
        }
    }

    ThreadContainer container() {
        return container;
    }

    Dispatcher poolDispatcher() {
        return poolDispatcher;
    }

    static final class Container extends ThreadContainer {
        static final boolean DEBUG = false;
        final Set<Thread> threads = ConcurrentHashMap.newKeySet();

        private Container() {
            super(true);
        }

        public void onStart(final Thread thread) {
            // todo: track shutdown
            if (DEBUG) threads.add(thread);
        }

        public void onExit(final Thread thread) {
            // todo: track shutdown
            if (DEBUG) threads.remove(thread);
        }

        public String name() {
            return "managed";
        }

        public long threadCount() {
            return threads.size();
        }

        protected boolean tryClose() {
            return super.tryClose();
        }

        public Thread owner() {
            return super.owner();
        }

        public Stream<Thread> threads() {
            return DEBUG ? threads.stream() : Stream.empty();
        }
    }

    private class PoolDispatcher extends Dispatcher {
        void execute(final UserThreadScheduler continuation) {
            blockingPool.execute(continuation);
        }

        ScheduledFuture<?> schedule(final Runnable task, final long nanos) {
            return blockingPool.schedule(task, nanos, TimeUnit.NANOSECONDS);
        }
    }
}
