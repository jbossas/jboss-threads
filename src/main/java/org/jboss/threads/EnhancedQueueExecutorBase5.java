package org.jboss.threads;

import static org.jboss.threads.JBossExecutors.unsafe;

/**
 * EQE base: thread status
 */
abstract class EnhancedQueueExecutorBase5 extends EnhancedQueueExecutorBase4 {
    static final long threadStatusOffset;

    static {
        try {
            threadStatusOffset = unsafe.objectFieldOffset(EnhancedQueueExecutorBase5.class.getDeclaredField("threadStatus"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    // =======================================================
    // Current state fields
    // =======================================================

    /**
     * Active consumers:
     * <ul>
     *     <li>Bit 00..19: current number of running threads</li>
     *     <li>Bit 20..39: core pool size</li>
     *     <li>Bit 40..59: maximum pool size</li>
     *     <li>Bit 60: 1 = allow core thread timeout; 0 = disallow core thread timeout</li>
     *     <li>Bit 61: 1 = shutdown requested; 0 = shutdown not requested</li>
     *     <li>Bit 62: 1 = shutdown task interrupt requested; 0 = interrupt not requested</li>
     *     <li>Bit 63: 1 = shutdown complete; 0 = shutdown not complete</li>
     * </ul>
     */
    @SuppressWarnings("unused") // used by field updater
    volatile long threadStatus;

    EnhancedQueueExecutorBase5() {
        super();
    }

    // =======================================================
    // Compare-and-set operations
    // =======================================================

    boolean compareAndSetThreadStatus(final long expect, final long update) {
        return unsafe.compareAndSwapLong(this, threadStatusOffset, expect, update);
    }
}
