package org.jboss.threads;

import java.util.concurrent.Executor;

class SimpleDirectExecutor implements Executor {

    static final SimpleDirectExecutor INSTANCE = new SimpleDirectExecutor();

    private SimpleDirectExecutor() {
    }

    public void execute(final Runnable command) {
        command.run();
    }

    public String toString() {
        return "Direct executor";
    }
}
