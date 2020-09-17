package org.jboss.threads;

import org.wildfly.common.Assert;

import java.security.PrivilegedAction;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;

import static java.security.AccessController.doPrivileged;

/**
 * An executor service that is actually a "view" over another executor service.
 */
public abstract class ViewExecutor extends AbstractExecutorService {

    private static final boolean USE_V2 = readBooleanPropertyPrefixed("v2", true);

    private volatile Thread.UncaughtExceptionHandler handler;
    private volatile Runnable terminationTask;

    // Intentionally package private to effectively seal the type.
    ViewExecutor() {}

    @Override
    public final void shutdown() {
        shutdown(false);
    }

    public abstract void shutdown(boolean interrupt);

    public final Thread.UncaughtExceptionHandler getExceptionHandler() {
        return handler;
    }

    public final void setExceptionHandler(final Thread.UncaughtExceptionHandler handler) {
        Assert.checkNotNullParam("handler", handler);
        this.handler = handler;
    }

    public final Runnable getTerminationTask() {
        return terminationTask;
    }

    public final void setTerminationTask(final Runnable terminationTask) {
        this.terminationTask = terminationTask;
    }

    public static Builder builder(Executor delegate) {
        Assert.checkNotNullParam("delegate", delegate);
        return new Builder(delegate);
    }

    public static final class Builder {
        private final Executor delegate;
        private short maxSize = 1;
        private int queueLimit = Integer.MAX_VALUE;
        private int queueInitialSize = 256;
        private Thread.UncaughtExceptionHandler handler = JBossExecutors.loggingExceptionHandler();

        Builder(final Executor delegate) {
            this.delegate = delegate;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public Builder setMaxSize(final int maxSize) {
            Assert.checkMinimumParameter("maxSize", 1, maxSize);
            Assert.checkMaximumParameter("maxSize", Short.MAX_VALUE, maxSize);
            this.maxSize = (short) maxSize;
            return this;
        }

        public int getQueueLimit() {
            return queueLimit;
        }

        public Builder setQueueLimit(final int queueLimit) {
            Assert.checkMinimumParameter("queueLimit", 0, queueLimit);
            this.queueLimit = queueLimit;
            return this;
        }

        public Executor getDelegate() {
            return delegate;
        }

        public Thread.UncaughtExceptionHandler getUncaughtHandler() {
            return handler;
        }

        public Builder setUncaughtHandler(final Thread.UncaughtExceptionHandler handler) {
            this.handler = handler;
            return this;
        }

        public int getQueueInitialSize() {
            return queueInitialSize;
        }

        public Builder setQueueInitialSize(final int queueInitialSize) {
            this.queueInitialSize = queueInitialSize;
            return this;
        }

        public ViewExecutor build() {
            if (USE_V2 || queueLimit == 0) {
                // queueInitialSize is not relevant using ConcurrentLinkedQueue
                return new EnhancedViewExecutor(
                        Assert.checkNotNullParam("delegate", delegate),
                        maxSize,
                        queueLimit,
                        handler);
            }
            return new QueuedViewExecutor(
                    Assert.checkNotNullParam("delegate", delegate),
                    maxSize,
                    queueLimit,
                    queueInitialSize,
                    handler);
        }
    }

    protected void runTermination() {
        final Runnable task = ViewExecutor.this.terminationTask;
        ViewExecutor.this.terminationTask = null;
        if (task != null) try {
            task.run();
        } catch (Throwable t) {
            Thread.UncaughtExceptionHandler configuredHandler = handler;
            if (configuredHandler != null) {
                try {
                    handler.uncaughtException(Thread.currentThread(), t);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static boolean readBooleanPropertyPrefixed(String name, boolean defVal) {
        return Boolean.parseBoolean(readPropertyPrefixed(name, Boolean.toString(defVal)));
    }

    static int readIntPropertyPrefixed(String name, int defVal) {
        try {
            return Integer.parseInt(readPropertyPrefixed(name, Integer.toString(defVal)));
        } catch (NumberFormatException ignored) {
            return defVal;
        }
    }

    static String readPropertyPrefixed(String name, String defVal) {
        return readProperty("org.jboss.threads.view-executor." + name, defVal);
    }

    static String readProperty(String name, String defVal) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return doPrivileged((PrivilegedAction<String>) () -> readPropertyRaw(name, defVal));
        } else {
            return readPropertyRaw(name, defVal);
        }
    }

    static String readPropertyRaw(final String name, final String defVal) {
        return System.getProperty(name, defVal);
    }
}
