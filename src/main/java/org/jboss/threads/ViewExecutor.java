package org.jboss.threads;

import org.wildfly.common.Assert;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;

/**
 * An executor service that is actually a "view" over another executor service.
 */
public abstract class ViewExecutor extends AbstractExecutorService {

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
        private boolean preserveContextClassLoaders = true;
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

        /**
         * Returns true if submitting threads context classloaders are preserved.
         *
         * @see #setPreserveContextClassLoaders(boolean)
         */
        public boolean getPreserveContextClassLoaders() {
            return preserveContextClassLoaders;
        }

        /**
         * Configures whether tasks submitted to this executor will preserve the callers context classloader.
         * By default context class loaders are preserved.
         *
         * @see JBossExecutors#classLoaderPreservingTaskUnchecked(Runnable)
         */
        public Builder setPreserveContextClassLoaders(final boolean preserveContextClassLoaders) {
            this.preserveContextClassLoaders = preserveContextClassLoaders;
            return this;
        }

        public ViewExecutor build() {
            if (queueLimit == 0) {
                return new QueuelessViewExecutor(
                        Assert.checkNotNullParam("delegate", delegate),
                        maxSize,
                        preserveContextClassLoaders,
                        handler);
            }
            return new QueuedViewExecutor(
                    Assert.checkNotNullParam("delegate", delegate),
                    maxSize,
                    queueLimit,
                    queueInitialSize,
                    preserveContextClassLoaders,
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
}
