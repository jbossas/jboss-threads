package org.jboss.threads;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

class HandoffRejectedExecutionHandler implements RejectedExecutionHandler {

    private final Executor target;

    HandoffRejectedExecutionHandler(final Executor target) {
        this.target = target;
    }

    public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
        target.execute(r);
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), target);
    }
}
