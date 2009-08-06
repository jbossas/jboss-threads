/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;
import java.util.Date;

/**
 * A simple lock impl which does no deadlock detection, no fairness, no reentrancy protection, no error checking,
 * and has basically no frills whatsoever.  Non-public due to its highly experimental nature.  This probably won't work;
 * in fact the only reason I'm even trying this is because I'm an obsessive moron.
 */
final class SimpleLock implements Lock {
    private boolean locked;

    public void lock() {
        boolean intr = Thread.interrupted();
        try {
            synchronized (this) {
                while (locked) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
                locked = true;
            }
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    public void lockInterruptibly() throws InterruptedException {
        synchronized (this) {
            while (locked) {
                wait();
            }
            locked = true;
        }
    }

    public boolean tryLock() {
        synchronized (this) {
            if (locked) {
                return false;
            } else {
                locked = true;
                return true;
            }
        }
    }

    private static long clipHigh(long value) {
        return value < 0 ? Long.MAX_VALUE : value;
    }

    public boolean tryLock(final long time, final TimeUnit unit) throws InterruptedException {
        if (time < 0) {
            return tryLock();
        }
        long now = System.currentTimeMillis();
        long deadline = clipHigh(now + unit.toMillis(time));
        synchronized (this) {
            if (! locked) {
                return locked = true;
            }
            for (;;) {
                final long remaining = deadline - now;
                if (remaining <= 0) {
                    return false;
                }
                wait(remaining);
                if (! locked) {
                    return locked = true;
                }
                now = System.currentTimeMillis();
            }
        }
    }

    public void unlock() {
        synchronized (this) {
            locked = false;
        }
    }

    public Condition newCondition() {
        return new SimpleCondition();
    }

    public final class SimpleCondition implements Condition {

        SimpleCondition() {
        }

        public void await() throws InterruptedException {
            synchronized (this) {
                unlock();
                try {
                    wait();
                } finally {
                    lock();
                }
            }
        }

        public void awaitUninterruptibly() {
            boolean intr = Thread.interrupted();
            try {
                synchronized (this) {
                    unlock();
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    } finally {
                        lock();
                    }
                }
            } finally {
                if (intr) Thread.currentThread().interrupt();
            }
        }

        public long awaitNanos(final long nanosTimeout) throws InterruptedException {
            unlock();
            try {
                final long start = System.nanoTime();
                synchronized (this) {
                    wait(nanosTimeout / 1000000L, (int) (nanosTimeout % 1000000L));
                }
                return nanosTimeout - (System.nanoTime() - start);
            } finally {
                lock();
            }
        }

        public boolean await(final long time, final TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException("not implemented yet");
        }

        public boolean awaitUntil(final Date deadline) throws InterruptedException {
            throw new UnsupportedOperationException("not implemented yet");
        }

        public void signal() {
            synchronized (this) {
                notify();
            }
        }

        public void signalAll() {
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
