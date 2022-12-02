package org.jboss.threads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.List;

/**
 * An implementation of {@code ExecutorService} that delegates to the real executor, while disallowing termination.
 */
class DelegatingExecutorService extends AbstractExecutorService implements ExecutorService {
    private final Executor delegate;

    DelegatingExecutorService(final Executor delegate) {
        this.delegate = delegate;
    }

    public void execute(final Runnable command) {
        delegate.execute(command);
    }

    public boolean isShutdown() {
        // container managed executors are never shut down from the application's perspective
        return false;
    }

    public boolean isTerminated() {
        // container managed executors are never shut down from the application's perspective
        return false;
    }

    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return false;
    }

    public void shutdown() {
        throw Messages.msg.notAllowedContainerManaged("shutdown");
    }

    public List<Runnable> shutdownNow() {
        throw Messages.msg.notAllowedContainerManaged("shutdownNow");
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
