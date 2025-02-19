package org.jboss.threads.virtual;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The base class for an individual thread's scheduler.
 */
abstract class ThreadScheduler implements ScheduledExecutorService, Runnable {
    private static final VarHandle yieldedHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "yielded", VarHandle.class, ThreadScheduler.class, boolean.class);
    /**
     * Indicate if this thread parked or yielded, to detect a starvation scenario.
     */
    @SuppressWarnings("unused") // yieldedHandle
    volatile boolean yielded;

    /**
     * The owning scheduler.
     */
    private final Scheduler scheduler;
    /**
     * The virtual thread associated with this task.
     */
    private final Thread virtualThread;

    ThreadScheduler(final Scheduler scheduler, final String name, final long idx) {
        this.scheduler = scheduler;
        this.virtualThread = Access.threadBuilder(this).inheritInheritableThreadLocals(false).name(name, idx).unstarted(this::runThreadBody);
    }

    void start() {
        Access.startThread(virtualThread, scheduler.container());
    }

    abstract void runThreadBody();

    /**
     * Run the continuation for the current thread.
     */
    public void run() {
        assert ! Thread.currentThread().isVirtual();
        try {
            Access.continuationOf(virtualThread).run();
        } finally {
            yieldedHandle.setOpaque(this, true);
        }
    }

    /**
     * {@return {@code true} if this scheduler has yielded since the last invocation of this method, or {@code false} otherwise}
     */
    boolean yielded() {
        boolean yielded = (boolean) yieldedHandle.getOpaque(this);
        if (yielded) {
            yieldedHandle.setOpaque(this, false);
        }
        return yielded;
    }

    /**
     * {@return the overall scheduler for this thread scheduler}
     */
    Scheduler scheduler() {
        return scheduler;
    }

    /**
     * {@return the virtual thread associated with this thread scheduler}
     */
    Thread virtualThread() {
        return virtualThread;
    }

    /**
     * Schedule the given command, which may be the continuation for this thread.
     *
     * @param command the command (must not be {@code null})
     */
    public abstract void execute(Runnable command);

    /**
     * Schedule the given command, which is generally an unpark request for some other sleeping thread.
     *
     * @param command the command (must not be {@code null})
     */
    public abstract ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    // unimplemented methods

    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    public boolean isShutdown() {
        throw new UnsupportedOperationException();
    }

    public boolean isTerminated() {
        throw new UnsupportedOperationException();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException();
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }
}
