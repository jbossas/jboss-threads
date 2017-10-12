/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This interface represents the result of an asynchronous future task, which provides all the features
 * of {@link Future} while also adding several additional convenience methods and the ability to add asynchronous
 * callbacks.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface AsyncFuture<T> extends Future<T>, AsyncCancellable {

    /**
     * Wait if necessary for this operation to complete, returning the outcome.  The outcome will be one of
     * {@link Status#COMPLETE}, {@link Status#CANCELLED}, or {@link Status#FAILED}.
     *
     * @return the outcome
     * @throws InterruptedException if execution was interrupted while waiting
     */
    Status await() throws InterruptedException;

    /**
     * Wait if necessary for this operation to complete, returning the outcome, which may include {@link Status#WAITING} if
     * the timeout expires before the operation completes.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the outcome
     * @throws InterruptedException if execution was interrupted while waiting
     */
    Status await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits (uninterruptibly) if necessary for the computation to complete, and then retrieves the result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an exception
     */
    T getUninterruptibly() throws CancellationException, ExecutionException;

    /**
     * Waits (uninterruptibly) if necessary for at most the given time for the computation to complete, and then
     * retrieves the result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an exception
     * @throws TimeoutException if the wait timed out
     */
    T getUninterruptibly(long timeout, TimeUnit unit) throws CancellationException, ExecutionException, TimeoutException;

    /**
     * Wait (uninterruptibly) if necessary for this operation to complete, returning the outcome.  The outcome will be one of
     * {@link Status#COMPLETE}, {@link Status#CANCELLED}, or {@link Status#FAILED}.
     *
     * @return the outcome
     */
    Status awaitUninterruptibly();

    /**
     * Wait if necessary for this operation to complete, returning the outcome, which may include {@link Status#WAITING} if
     * the timeout expires before the operation completes.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the outcome
     */
    Status awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Get (poll) the current status of the asynchronous operation.
     *
     * @return the current status
     */
    Status getStatus();

    /**
     * Add an asynchronous listener to be called when this operation completes.
     *
     * @param listener the listener to add
     * @param attachment the attachment to pass in
     * @param <A> the attachment type
     */
    <A> void addListener(Listener<? super T, A> listener, A attachment);

    /**
     * Synchronously cancel a task, blocking uninterruptibly until it is known whether such cancellation was
     * successful.  Note that the {@link Future#cancel(boolean)} is somewhat unclear about blocking semantics.
     * It is recommended to use {@link #asyncCancel(boolean)} instead.
     *
     * @param interruptionDesired if interruption is desired (if available)
     * @return {@code true} if cancel succeeded, {@code false} otherwise
     */
    boolean cancel(boolean interruptionDesired);

    /** {@inheritDoc} */
    void asyncCancel(boolean interruptionDesired);

    /**
     * The possible statuses of an {@link AsyncFuture}.
     */
    enum Status {

        /**
         * The operation is still in progress.
         */
        WAITING,
        /**
         * The operation has completed successfully.
         */
        COMPLETE,
        /**
         * The operation was cancelled before it completed.
         */
        CANCELLED,
        /**
         * The operation has failed with some exception.
         */
        FAILED,
        ;
    }

    /**
     * A listener for an asynchronous future computation result.  Each listener method is passed the
     * {@link AsyncFuture} which it was added to, as well as the {@code attachment} which was passed in to
     * {@link AsyncFuture#addListener(Listener, Object)}.
     *
     * @param <T> the future type
     * @param <A> the attachment type
     */
    interface Listener<T, A> extends java.util.EventListener {

        /**
         * Handle a successful computation result.
         *
         * @param future the future
         * @param attachment the attachment
         */
        void handleComplete(AsyncFuture<? extends T> future, A attachment);

        /**
         * Handle a failure result.
         *
         * @param future the future
         * @param cause the reason for the failure
         * @param attachment the attachment
         */
        void handleFailed(AsyncFuture<? extends T> future, Throwable cause, A attachment);

        /**
         * Handle a cancellation result.
         *
         * @param future the future
         * @param attachment the attachment
         */
        void handleCancelled(AsyncFuture<? extends T> future, A attachment);
    }

    /**
     * An abstract base class for an implementation of the {@code Listener} interface.  The implementation
     * methods do nothing unless overridden.
     *
     * @param <T> the future type
     * @param <A> the attachment type
     */
    abstract class AbstractListener<T, A> implements Listener<T, A> {

        /** {@inheritDoc} */
        public void handleComplete(final AsyncFuture<? extends T> future, final A attachment) {
        }

        /** {@inheritDoc} */
        public void handleFailed(final AsyncFuture<? extends T> future, final Throwable cause, final A attachment) {
        }

        /** {@inheritDoc} */
        public void handleCancelled(final AsyncFuture<? extends T> future, final A attachment) {
        }
    }
}
