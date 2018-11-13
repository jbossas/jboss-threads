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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.wildfly.common.Assert;

/**
 * A spin lock.  Such locks are designed to only be held for a <em>very</em> short time - for example, long enough to compare and
 * swap two fields.
 * <p>
 * Spin locks do not support conditions, and they do not support timed waiting.  Normally only the uninterruptible {@code lock},
 * {@code tryLock}, and {@code unlock} methods should be used to control the lock.
 */
public class SpinLock implements ExtendedLock {
    private static final long ownerOffset;

    static {
        try {
            ownerOffset = unsafe.objectFieldOffset(SpinLock.class.getDeclaredField("owner"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private volatile Thread owner;

    /**
     * Construct a new instance.
     */
    public SpinLock() {}

    /**
     * Determine if this spin lock is held.  Useful for assertions.
     *
     * @return {@code true} if the lock is held by any thread, {@code false} otherwise
     */
    public boolean isLocked() {
        return owner != null;
    }

    /**
     * Determine if this spin lock is held by the calling thread.  Useful for assertions.
     *
     * @return {@code true} if the lock is held by the calling thread, {@code false} otherwise
     */
    public boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    /**
     * Determine if this lock is fair.
     *
     * @return {@code true}; the lock is fair
     */
    public boolean isFair() {
        return true;
    }

    /**
     * Acquire the lock by spinning until it is held.
     */
    public void lock() {
        Thread owner;
        for (;;) {
            owner = this.owner;
            if (owner == Thread.currentThread()) {
                throw new IllegalMonitorStateException();
            } else if (owner == null && unsafe.compareAndSwapObject(this, ownerOffset, null, Thread.currentThread())) {
                return;
            } else {
                JDKSpecific.onSpinWait();
            }
        }
    }

    public void lockInterruptibly() throws InterruptedException {
        Thread owner;
        for (;;) {
            if (Thread.interrupted()) throw new InterruptedException();
            owner = this.owner;
            if (owner == Thread.currentThread()) {
                throw new IllegalMonitorStateException();
            } else if (owner == null && unsafe.compareAndSwapObject(this, ownerOffset, null, Thread.currentThread())) {
                return;
            } else {
                JDKSpecific.onSpinWait();
            }
        }
    }

    public boolean tryLock() {
        return unsafe.compareAndSwapObject(this, ownerOffset, null, Thread.currentThread());
    }

    public boolean tryLock(final long time, final TimeUnit unit) {
        throw Assert.unsupported();
    }

    public void unlock() {
        if (! unsafe.compareAndSwapObject(this, ownerOffset, Thread.currentThread(), null)) {
            throw new IllegalMonitorStateException();
        }
    }

    public Condition newCondition() {
        throw Assert.unsupported();
    }
}
