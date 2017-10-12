/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A base class for implementing asynchronous tasks.  This class implements
 * {@link java.util.concurrent.Future Future} as well as {@link AsyncFuture}, and
 * is approximately equivalent to {@link java.util.concurrent.FutureTask}, however it
 * does not implement {@link Runnable} and is somewhat more flexible.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AsyncFutureTask<T> implements AsyncFuture<T> {
    private final Executor executor;
    private AsyncFuture.Status status;
    private Object result;
    private List<Reg<?>> listeners;

    private final class Reg<A> implements Runnable {
        private final AsyncFuture.Listener<? super T, A> listener;
        private final A attachment;

        private Reg(final AsyncFuture.Listener<? super T, A> listener, final A attachment) {
            this.listener = listener;
            this.attachment = attachment;
        }

        public void run() {
            switch (getStatus()) {
                case CANCELLED: listener.handleCancelled(AsyncFutureTask.this, attachment); break;
                case COMPLETE: listener.handleComplete(AsyncFutureTask.this, attachment); break;
                case FAILED: listener.handleFailed(AsyncFutureTask.this, (Throwable) result, attachment);
            }
        }
    }

    private static TimeoutException operationTimedOut() {
        return new TimeoutException("Operation timed out");
    }

    private static CancellationException operationCancelled() {
        return new CancellationException("Operation was cancelled");
    }

    private static ExecutionException operationFailed(final Throwable cause) {
        return new ExecutionException("Operation failed", cause);
    }

    private static IllegalStateException invalidState() {
        return new IllegalStateException("Invalid state entered");
    }

    /**
     * Construct a new instance.
     *
     * @param executor the executor to use for asynchronous notifications
     */
    protected AsyncFutureTask(final Executor executor) {
        this.executor = executor;
        status = AsyncFuture.Status.WAITING;
    }

    /**
     * Set the successful result of this operation.  Once a result is set, calls to this
     * or the other {@code set*()} methods are ignored.
     *
     * @param result the result
     * @return {@code true} if the result was successfully set, or {@code false} if a result was already set
     */
    protected final boolean setResult(final T result) {
        List<Reg<?>> list;
        synchronized (this) {
            if (status == AsyncFuture.Status.WAITING) {
                this.result = result;
                status = AsyncFuture.Status.COMPLETE;
                notifyAll();
                list = listeners;
                listeners = null;
            } else {
                return false;
            }
        }
        if (list != null) for (Reg<?> reg : list) {
            safeExecute(reg);
        }
        return true;
    }

    /**
     * Set the cancelled result of this operation.  Once a result is set, calls to this
     * or the other {@code set*()} methods are ignored.
     *
     * @return {@code true} if the result was successfully set, or {@code false} if a result was already set
     */
    protected final boolean setCancelled() {
        List<Reg<?>> list;
        synchronized (this) {
            if (status == AsyncFuture.Status.WAITING) {
                status = AsyncFuture.Status.CANCELLED;
                notifyAll();
                list = listeners;
                listeners = null;
            } else {
                return false;
            }
        }
        if (list != null) for (Reg<?> reg : list) {
            safeExecute(reg);
        }
        return true;
    }

    /**
     * Set the failure result of this operation.  Once a result is set, calls to this
     * or the other {@code set*()} methods are ignored.
     *
     * @param cause the cause of failure
     * @return {@code true} if the result was successfully set, or {@code false} if a result was already set
     */
    protected final boolean setFailed(final Throwable cause) {
        List<Reg<?>> list;
        synchronized (this) {
            if (status == AsyncFuture.Status.WAITING) {
                status = AsyncFuture.Status.FAILED;
                result = cause;
                notifyAll();
                list = listeners;
                listeners = null;
            } else {
                return false;
            }
        }
        if (list != null) for (Reg<?> reg : list) {
            safeExecute(reg);
        }
        return true;
    }

    private <A> void safeExecute(final Reg<A> reg) {
        try {
            executor.execute(reg);
        } catch (Throwable t) {
            // todo log it
        }
    }

    /**
     * Cancel this task.  The default implementation of this method does nothing; if the
     * task support asynchronous cancel, this method may be overridden to implement it.  The
     * implementation may choose to support or disregard the {@code interruptionDesired} flag.
     * Implementations are allowed to interrupt threads associated with tasks even if the flag is
     * {@code false}; likewise, implementations may choose not to interrupt threads even if the
     * flag is {@code true}.
     *
     * @param interruptionDesired {@code true} if interruption of threads is desired
     */
    public void asyncCancel(final boolean interruptionDesired) {
    }

    /** {@inheritDoc} */
    public final Status await() throws InterruptedException {
        synchronized (this) {
            while (status == Status.WAITING) {
                wait();
            }
            return status;
        }
    }

    /** {@inheritDoc} */
    public final Status await(final long timeout, final TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        long now = System.nanoTime();
        Status status;
        synchronized (this) {
            for (;;) {
                status = this.status;
                if (remaining <= 0L || status != Status.WAITING) {
                    return status;
                }
                wait(remaining / 1_000_000L, (int) (remaining % 1_000_000));
                remaining -= -now + (now = System.nanoTime());
            }
        }
    }

    /** {@inheritDoc} */
    public final Status awaitUninterruptibly() {
        synchronized (this) {
            boolean intr = Thread.interrupted();
            try {
                while (status == Status.WAITING) try {
                    wait();
                } catch (InterruptedException e) {
                    intr = true;
                }
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
            return status;
        }
    }

    /** {@inheritDoc} */
    public final Status awaitUninterruptibly(final long timeout, final TimeUnit unit) {
        long remaining = unit.toNanos(timeout);
        long now = System.nanoTime();
        Status status;
        boolean intr = Thread.interrupted();
        try {
            synchronized (this) {
                for (;;) {
                    status = this.status;
                    if (remaining <= 0L || status != Status.WAITING) {
                        return status;
                    }
                    try {
                        wait(remaining / 1_000_000L, (int) (remaining % 1_000_000));
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                    remaining -= -now + (now = System.nanoTime());
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked" })
    public final T get() throws InterruptedException, ExecutionException {
        synchronized (AsyncFutureTask.this) {
            switch (await()) {
                case CANCELLED: throw operationCancelled();
                case FAILED: throw operationFailed((Throwable) result);
                case COMPLETE: return (T) result;
                default: throw invalidState();
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked" })
    public final T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (AsyncFutureTask.this) {
            switch (await(timeout, unit)) {
                case CANCELLED: throw operationCancelled();
                case FAILED: throw operationFailed((Throwable) result);
                case COMPLETE: return (T) result;
                case WAITING: throw operationTimedOut();
                default: throw invalidState();
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked" })
    public final T getUninterruptibly() throws CancellationException, ExecutionException {
        synchronized (AsyncFutureTask.this) {
            switch (awaitUninterruptibly()) {
                case CANCELLED: throw operationCancelled();
                case FAILED: throw operationFailed((Throwable) result);
                case COMPLETE: return (T) result;
                default: throw invalidState();
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked" })
    public final T getUninterruptibly(final long timeout, final TimeUnit unit) throws CancellationException, ExecutionException, TimeoutException {
        synchronized (AsyncFutureTask.this) {
            switch (awaitUninterruptibly(timeout, unit)) {
                case CANCELLED: throw operationCancelled();
                case FAILED: throw operationFailed((Throwable) result);
                case COMPLETE: return (T) result;
                case WAITING: throw operationTimedOut();
                default: throw invalidState();
            }
        }
    }

    /** {@inheritDoc} */
    public final Status getStatus() {
        synchronized (AsyncFutureTask.this) {
            return status;
        }
    }

    /** {@inheritDoc} */
    public final <A> void addListener(final Listener<? super T, A> listener, final A attachment) {
        synchronized (AsyncFutureTask.this) {
            final Reg<A> reg = new Reg<A>(listener, attachment);
            if (status == Status.WAITING) {
                if (listeners == null) {
                    listeners = new ArrayList<Reg<?>>();
                }
                listeners.add(reg);
            } else {
                safeExecute(reg);
            }
        }
    }

    /** {@inheritDoc} */
    public final boolean cancel(final boolean interruptionDesired) {
        asyncCancel(interruptionDesired);
        return awaitUninterruptibly() == Status.CANCELLED;
    }

    /** {@inheritDoc} */
    public final boolean isCancelled() {
        return getStatus() == Status.CANCELLED;
    }

    /** {@inheritDoc} */
    public final boolean isDone() {
        return getStatus() != Status.WAITING;
    }
}
