package org.jboss.threads.virtual;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.PriorityQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.threads.JBossThread;

/**
 * An event loop carrier thread with a built-in executor.
 * To acquire the virtual thread of the event loop which is carried by this thread,
 * use {@link #virtualThread()}.
 */
public final class EventLoopThread extends JBossThread implements Executor {
    private static final VarHandle waitTimeHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "waitTime", VarHandle.class, EventLoopThread.class, long.class);

    // TODO: we could track the currently-mounted thread fairly easily, maybe along with a timestamp

    /**
     * The virtual thread which runs the event handler.
     */
    private final EventLoopThreadScheduler elts;
    /**
     * The event loop implementation.
     */
    private final EventLoop eventLoop;
    /**
     * Comparison nanos.
     * This is not representative of the current time; rather, it's a somewhat-recent (but arbitrary)
     * sample of {@code System.nanoTime()}.
     * Update before each queue operation and this queue can run forever, as long as no task waits for more than ~138 years.
     * Such tasks might unexpectedly have a different priority.
     * But it is not likely to matter at that point.
     */
    private long cmpNanos;
    /**
     * Current nanoseconds.
     * This <em>is</em> a snapshot of the current time, taken immediately before draining the delay queue.
     */
    private long currentNanos;
    /**
     * The wait time for the virtual thread side of the event loop.
     * This value is handed back and forth between the I/O carrier thread and the event loop virtual thread.
     */
    @SuppressWarnings("unused") // waitTimeHandle
    private volatile long waitTime = -1;
    /**
     * The shared/slow task queue, which allows tasks to be enqueued from outside of this thread.
     */
    private final LinkedBlockingQueue<UserThreadScheduler> sq = new LinkedBlockingQueue<>();
    /**
     * The task queue.
     * The queue is ordered by the amount of time that each entry (thread) has been waiting to run.
     */
    private final PriorityQueue<UserThreadScheduler> q = new PriorityQueue<>(this::compare);
    /**
     * The bulk remover for transferring {@code sq} to {@code q}.
     */
    private final Predicate<UserThreadScheduler> bulkRemover = q::add;
    /**
     * The delay queue for timed sleep (patched JDKs only).
     */
    private final DelayQueue<DelayedTask<?>> dq = new DelayQueue<>();
    private final Dispatcher dispatcher = new EventLoopDispatcher();

    EventLoopThread(final Scheduler scheduler, final int idx, final Function<? super EventLoopThread, ? extends EventLoop> eventLoopFactory) {
        super(() -> {}, "Event loop carrier thread " + idx);
        elts = new EventLoopThreadScheduler(scheduler, this, idx);
        this.eventLoop = eventLoopFactory.apply(this);
    }

    /**
     * Create and start a virtual thread which is carried by this I/O thread.
     *
     * @param runnable the body of the virtual thread (must not be {@code null})
     */
    public void execute(Runnable runnable) {
        elts.scheduler().executeOnEventLoop(this, runnable);
    }

    /**
     * {@return the owner of this event loop thread}
     */
    Scheduler owner() {
        return elts.scheduler();
    }

    /**
     * {@return the event loop for this thread}
     */
    public EventLoop eventLoop() {
        return eventLoop;
    }

    /**
     * {@return the virtual thread of the event loop itself}
     */
    public Thread virtualThread() {
        return elts.virtualThread();
    }

    /**
     * {@return the current event loop thread, or {@code null} if the current thread is not mounted on an event loop thread}
     */
    public static EventLoopThread current() {
        return Thread.currentThread().isVirtual() && Access.currentCarrier() instanceof EventLoopThread elt ? elt : null;
    }

    public void interrupt() {
        // interruption of I/O carrier threads is not allowed
    }

    /**
     * Run the carrier side of the event loop.
     */
    public void run() {
        if (Thread.currentThread() != this || elts.virtualThread().isAlive()) {
            throw new IllegalThreadStateException();
        }
        elts.start();
        // initialize the wait-time comparison basis
        cmpNanos = System.nanoTime();
        for (;;) {
            // drain shared queue, hopefully somewhat efficiently
            sq.removeIf(bulkRemover);
            long waitTime = -1L;
            if (!dq.isEmpty()) {
                // process the delay queue
                currentNanos = System.nanoTime();
                DelayedTask<?> dt = dq.poll();
                while (dt != null) {
                    // this will indirectly cause entries to be added to `q`
                    dt.run();
                    dt = dq.poll();
                }
                dt = dq.peek();
                if (dt != null) {
                    // set the max wait time to the amount of time before the next scheduled task
                    waitTime = Math.max(1L, dt.deadline - currentNanos);
                }
            }
            UserThreadScheduler removed = q.poll();
            if (removed == null) {
                waitTimeHandle.setOpaque(this, waitTime);
                // mark event handler ready
                elts.makeReady();
                // call event handler (possibly early)
                elts.run();
            } else {
                // update for next q operation without hitting nanoTime over and over
                cmpNanos = removed.waitingSince();
                removed.run();
                // now, see if we reenter the event loop right away or not
                if (elts.ready()) {
                    waitTimeHandle.setOpaque(this, 0L);
                    // call event handler
                    elts.run();
                }
                // otherwise, continue processing tasks
            }
        }
    }

    void enqueueFromOutside(final UserThreadScheduler command) {
        sq.add(command);
        eventLoop().wakeup();
    }

    void enqueueLocal(final UserThreadScheduler command) {
        assert Thread.currentThread() == this || Access.currentCarrier() == this;
        q.add(command);
    }

    /**
     * Compare the wait times of two tasks.
     * The task that has waited for the longest time is considered earlier than the task that has a shorter wait time.
     *
     * @param o1 the first thread scheduler (must not be {@code null})
     * @param o2 the second thread scheduler (must not be {@code null})
     * @return the comparison result
     */
    private int compare(UserThreadScheduler o1, UserThreadScheduler o2) {
        long cmpNanos = this.cmpNanos;
        return Long.compare(o2.waitTime(cmpNanos), o1.waitTime(cmpNanos));
    }

    ScheduledFuture<?> schedule(final Runnable command, final long nanos) {
        Thread ct = Thread.currentThread();
        if (ct == this || ct.isVirtual() && Access.currentCarrier() == this) {
            DelayedTask<Void> task = new DelayedTask<>(command, System.nanoTime() + nanos);
            dq.add(task);
            return task;
        } else {
            // not expected
            throw new IllegalStateException();
        }
    }

    Dispatcher dispatcher() {
        return dispatcher;
    }

    long waitTime() {
        return (long) waitTimeHandle.getOpaque(this);
    }

    private final class DelayedTask<V> implements ScheduledFuture<V>, Runnable {
        private final Runnable task;
        private final long deadline;

        private DelayedTask(final Runnable task, final long deadline) {
            this.task = task;
            this.deadline = deadline;
        }

        public long getDelay(final TimeUnit unit) {
            long currentNanos = EventLoopThread.this.currentNanos;
            return unit.convert(deadline - currentNanos, TimeUnit.NANOSECONDS);
        }

        public int compareTo(final Delayed o) {
            if (o instanceof DelayedTask<?> dt) {
                long currentNanos = EventLoopThread.this.currentNanos;
                return Long.compare(deadline - currentNanos, dt.deadline - currentNanos);
            } else {
                throw new IllegalStateException();
            }
        }

        public boolean cancel(final boolean mayInterruptIfRunning) {
            // unsupported
            return false;
        }

        public boolean isCancelled() {
            // unsupported
            return false;
        }

        public boolean isDone() {
            // unsupported
            return false;
        }

        public V get() {
            throw new UnsupportedOperationException();
        }

        public V get(final long timeout, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        public void run() {
            task.run();
        }
    }

    private class EventLoopDispatcher extends Dispatcher {
        void execute(final UserThreadScheduler continuation) {
            Thread ct = Thread.currentThread();
            if (ct == EventLoopThread.this || Access.currentCarrier() == EventLoopThread.this) {
                enqueueLocal(continuation);
            } else {
                enqueueFromOutside(continuation);
            }
        }

        ScheduledFuture<?> schedule(final Runnable task, final long nanos) {
            // it is expected that this will only be called locally
            assert Thread.currentThread() == EventLoopThread.this;
            DelayedTask<Object> dt = new DelayedTask<>(task, System.nanoTime() + nanos);
            dq.add(dt);
            return dt;
        }
    }
}

