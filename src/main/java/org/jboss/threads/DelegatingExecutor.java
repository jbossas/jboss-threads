package org.jboss.threads;

import java.util.concurrent.Executor;

/**
 * An executor that simply delegates to another executor.  Use instances of this class to hide extra methods on
 * another executor.
 */
class DelegatingExecutor implements Executor {
    private final Executor delegate;

    DelegatingExecutor(final Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Execute a task by passing it to the delegate executor.
     *
     * @param command the task
     */
    public void execute(final Runnable command) {
        delegate.execute(command);
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
