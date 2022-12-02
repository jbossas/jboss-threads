package org.jboss.threads;

import static org.jboss.threads.JBossExecutors.unsafe;

import org.wildfly.common.annotation.NotNull;

/**
 * EQE base class: head section.
 */
abstract class EnhancedQueueExecutorBase3 extends EnhancedQueueExecutorBase2 {
    static final long headOffset;

    static {
        try {
            headOffset = unsafe.objectFieldOffset(EnhancedQueueExecutorBase3.class.getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    // =======================================================
    // Current state fields
    // =======================================================

    /**
     * The node <em>preceding</em> the head node; this field is not {@code null}.  This is
     * the removal point for tasks (and the insertion point for waiting threads).
     */
    @NotNull
    @SuppressWarnings("unused") // used by field updater
    volatile EnhancedQueueExecutor.TaskNode head;

    EnhancedQueueExecutorBase3() {
        head = tail = new EnhancedQueueExecutor.TaskNode(null);
    }

    // =======================================================
    // Compare-and-set operations
    // =======================================================

    boolean compareAndSetHead(final EnhancedQueueExecutor.TaskNode expect, final EnhancedQueueExecutor.TaskNode update) {
        return unsafe.compareAndSwapObject(this, headOffset, expect, update);
    }
}
