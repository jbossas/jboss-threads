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
