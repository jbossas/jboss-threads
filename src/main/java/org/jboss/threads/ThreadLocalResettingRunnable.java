package org.jboss.threads;

final class ThreadLocalResettingRunnable extends DelegatingRunnable {

    ThreadLocalResettingRunnable(final Runnable delegate) {
        super(delegate);
    }

    public void run() {
        try {
            super.run();
        } finally {
            JDKSpecific.ThreadAccess.clearThreadLocals();
        }
    }

    public String toString() {
        return "Thread-local resetting Runnable";
    }
}
