package org.jboss.threads.virtual;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * The thread scheduler for an event loop thread.
 */
final class EventLoopThreadScheduler extends ThreadScheduler {
    private static final VarHandle readyHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "ready", VarHandle.class, EventLoopThreadScheduler.class, boolean.class);

    private final EventLoopThread eventLoopThread;
    @SuppressWarnings("unused") // readyHandle
    private boolean ready;

    EventLoopThreadScheduler(final Scheduler scheduler, final EventLoopThread eventLoopThread, final long idx) {
        super(scheduler, "Event loop", idx);
        this.eventLoopThread = eventLoopThread;
    }

    boolean ready() {
        return (boolean) readyHandle.getOpaque(this);
    }

    void makeReady() {
        readyHandle.setOpaque(this, true);
        LockSupport.unpark(virtualThread());
    }

    void runThreadBody() {
        // this runs on the *virtual* event loop thread
        EventLoopThread eventLoopThread = this.eventLoopThread;
        final EventLoop eventLoop = eventLoopThread.eventLoop();
        long waitTime;
        for (;;) {
            // clear the unpark permit
            Util.clearUnpark();
            // clear interrupt status
            Thread.interrupted();
            // call the event loop
            waitTime = eventLoopThread.waitTime();
            try {
                eventLoop.unparkAny(waitTime);
            } catch (Throwable ignored) {
            }
            // avoid starvation
            if (! yielded()) {
                Thread.yield();
                // yielding sets the flag to true, so clear it again
                yielded();
            }
        }
    }

    public void run() {
        readyHandle.setOpaque(this, false);
        super.run();
    }

    void start() {
        super.start();
    }

    public void execute(final Runnable command) {
        readyHandle.setOpaque(this, true);
    }

    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        return eventLoopThread.schedule(command, unit.convert(delay, TimeUnit.NANOSECONDS));
    }
}
