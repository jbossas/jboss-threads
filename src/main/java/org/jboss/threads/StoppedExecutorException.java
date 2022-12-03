package org.jboss.threads;

import java.util.concurrent.RejectedExecutionException;

/**
 * Thrown when a task is submitted to an executor which is in the process of, or has completed shutting down.
 */
public class StoppedExecutorException extends RejectedExecutionException {

    private static final long serialVersionUID = 4815103522815471074L;

    /**
     * Constructs a {@code StoppedExecutorException} with no detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public StoppedExecutorException() {
    }

    /**
     * Constructs a {@code StoppedExecutorException} with the specified detail message. The cause is not initialized, and
     * may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public StoppedExecutorException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code StoppedExecutorException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public StoppedExecutorException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code StoppedExecutorException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public StoppedExecutorException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
