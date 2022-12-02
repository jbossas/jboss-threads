package org.jboss.threads;

final class ThreadLocalResettingRunnable extends DelegatingRunnable {

    ThreadLocalResettingRunnable(final Runnable delegate) {
        super(delegate);
    }

    public void run() {
        try {
            super.run();
        } finally {
            Resetter.run();
        }
    }

    public String toString() {
        return "Thread-local resetting Runnable";
    }

    static final class Resetter {
        private static final long threadLocalMapOffs;
        private static final long inheritableThreadLocalMapOffs;

        static {
            try {
                threadLocalMapOffs = JBossExecutors.unsafe.objectFieldOffset(Thread.class.getDeclaredField("threadLocals"));
                inheritableThreadLocalMapOffs = JBossExecutors.unsafe.objectFieldOffset(Thread.class.getDeclaredField("inheritableThreadLocals"));
            } catch (NoSuchFieldException e) {
                throw new NoSuchFieldError(e.getMessage());
            }
        }

        static void run() {
            final Thread thread = Thread.currentThread();
            JBossExecutors.unsafe.putObject(thread, threadLocalMapOffs, null);
            JBossExecutors.unsafe.putObject(thread, inheritableThreadLocalMapOffs, null);
        }
    }
}
