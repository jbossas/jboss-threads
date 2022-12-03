package org.jboss.threads;

class DelegatingRunnable implements Runnable {
    private final Runnable delegate;

    DelegatingRunnable(final Runnable delegate) {
        this.delegate = delegate;
    }

    public void run() {
        delegate.run();
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
