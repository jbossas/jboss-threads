package org.jboss.threads;

class ContextClassLoaderSavingRunnable implements Runnable {

    private final ClassLoader loader;
    private final Runnable delegate;

    ContextClassLoaderSavingRunnable(final ClassLoader loader, final Runnable delegate) {
        this.loader = loader;
        this.delegate = delegate;
    }

    public void run() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader newCl = loader;
        final ClassLoader old = JBossExecutors.getAndSetContextClassLoader(currentThread, loader);
        try {
            delegate.run();
        } finally {
            JBossExecutors.setContextClassLoader(currentThread, old);
        }
    }

    public String toString() {
        return "Context class loader saving " + delegate.toString();
    }
}
