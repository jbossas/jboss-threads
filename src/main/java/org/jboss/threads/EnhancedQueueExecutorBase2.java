package org.jboss.threads;

/**
 * EQE base class: padding.
 */
abstract class EnhancedQueueExecutorBase2 extends EnhancedQueueExecutorBase1 {
    /**
     * Padding fields.
     */
    @SuppressWarnings("unused")
    int p00, p01, p02, p03,
        p04, p05, p06, p07,
        p08, p09, p0A, p0B,
        p0C, p0D, p0E, p0F;

    EnhancedQueueExecutorBase2() {}
}
