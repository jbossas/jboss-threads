/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.threads;

import static org.jboss.threads.JBossExecutors.unsafe;

import org.wildfly.common.annotation.NotNull;
import org.wildfly.common.cpu.ProcessorInfo;

/**
 * EQE base class: head section.
 */
abstract class EnhancedQueueExecutorBase3 extends EnhancedQueueExecutorBase2 {
    static final long headLockOffset;
    static final long headOffset;

    static {
        try {
            headLockOffset = unsafe.objectFieldOffset(EnhancedQueueExecutorBase3.class.getDeclaredField("headLock"));
            headOffset = unsafe.objectFieldOffset(EnhancedQueueExecutorBase3.class.getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    // =======================================================
    // Locks
    // =======================================================

    /**
     * Attempt to lock frequently-contended operations on the list head.
     */
    static final boolean HEAD_LOCK = readBooleanPropertyPrefixed("head-lock", true);
    /**
     * Use a spin lock for the head lock.
     */
    @SuppressWarnings("unused")
    static final boolean HEAD_SPIN = readBooleanPropertyPrefixed("head-spin", true);

    /**
     * Number of spins before yielding.
     */
    static final int YIELD_SPINS = readIntPropertyPrefixed("lock-yield-spins", ProcessorInfo.availableProcessors() == 1 ? 0 : 128);

    // =======================================================
    // Current state fields
    // =======================================================

    @SuppressWarnings("unused") // used by field updater
    volatile int headLock;

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

    // =======================================================
    // Locks
    // =======================================================

    final void lockHead() {
        int spins = 0;
        for (;;) {
            if (headLock == 0 && unsafe.compareAndSwapInt(this, headLockOffset, 0, 1)) {
                return;
            }
            if (spins == YIELD_SPINS) {
                spins = 0;
                Thread.yield();
            } else {
                spins++;
                JDKSpecific.onSpinWait();
            }
        }
    }

    final void unlockHead() {
        assert headLock == 1;
        headLock =  0;
    }
}
