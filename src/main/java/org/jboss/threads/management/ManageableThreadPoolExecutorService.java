package org.jboss.threads.management;

import java.util.concurrent.ExecutorService;

import org.wildfly.common.annotation.NotNull;

/**
 * A thread pool for which an MBean can be obtained.
 */
public interface ManageableThreadPoolExecutorService extends ExecutorService {
    /**
     * Create or acquire an MXBean instance for this thread pool.  Note that the thread pool itself will not
     * do anything in particular to register (or unregister) the MXBean with a JMX server; that is the caller's
     * responsibility.
     *
     * @return the MXBean instance (must not be {@code null})
     */
    @NotNull
    StandardThreadPoolMXBean getThreadPoolMXBean();
}
