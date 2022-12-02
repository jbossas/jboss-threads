package org.jboss.threads;

final class NullRunnable implements Runnable {

    private static final NullRunnable INSTANCE = new NullRunnable();

    static NullRunnable getInstance() {
        return INSTANCE;
    }

    NullRunnable() {
    }

    public void run() {
        // do nothing
    }
}
