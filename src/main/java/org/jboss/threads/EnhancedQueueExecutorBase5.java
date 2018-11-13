/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

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
