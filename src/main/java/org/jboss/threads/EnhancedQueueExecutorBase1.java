package org.jboss.threads;

import static org.jboss.threads.JBossExecutors.unsafe;

import org.wildfly.common.annotation.NotNull;

/**
 * EQE base class: tail section.
 */
abstract class EnhancedQueueExecutorBase1 extends EnhancedQueueExecutorBase0 {

    static final long tailOffset;

    static {
        try {
            tailOffset = unsafe.objectFieldOffset(EnhancedQueueExecutorBase1.class.getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }


    /**
     * The node <em>preceding</em> the tail node; this field is not {@code null}.  This
     * is the insertion point for tasks (and the removal point for waiting threads).
     */
    @NotNull
    @SuppressWarnings("unused") // used by field updater
    volatile EnhancedQueueExecutor.TaskNode tail;

    EnhancedQueueExecutorBase1() {}

    // =======================================================
    // Compare-and-set operations
    // =======================================================

    boolean compareAndSetTail(final EnhancedQueueExecutor.TaskNode expect, final EnhancedQueueExecutor.TaskNode update) {
        return tail == expect && unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }
}
