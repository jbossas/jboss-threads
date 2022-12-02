package org.jboss.threads;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

class RejectingExecutor implements Executor {
    static final RejectingExecutor INSTANCE = new RejectingExecutor();

    private final String message;

    private RejectingExecutor() {
        message = null;
    }

    RejectingExecutor(final String message) {
        this.message = message;
    }

    public void execute(final Runnable command) {
        throw new RejectedExecutionException(message);
    }

    public String toString() {
        return "Rejecting executor";
    }
}
