/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import java.util.concurrent.TimeUnit;

/**
 * Enhanced CountDownLatch adding awaitUninterruptibly() methods.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class CountDownLatch extends java.util.concurrent.CountDownLatch {

    public CountDownLatch(final int count) {
        super(count);
    }

    /**
     * Behaves the same way as {@link #await()} except it never throws InterruptedException.
     * Thread interrupt status will be preserved if thread have been interrupted inside this method.
     */
    public void awaitUninterruptibly() {
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try {
                    await();
                    break;
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Behaves the same way as {@link #await(long,TimeUnit)} except it never throws InterruptedException.
     * Thread interrupt status will be preserved if thread have been interrupted inside this method.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero and {@code false}
     *         if the waiting time elapsed before the count reached zero
     */
    public boolean awaitUninterruptibly(final long timeout, final TimeUnit unit) {
        boolean interrupted = Thread.interrupted();
        long now = System.nanoTime();
        long remaining = unit.toNanos(timeout);
        try {
            while (true) {
                if (remaining <= 0L) return false;
                try {
                    return await(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ie) {
                    interrupted = true;
                    remaining -= (-now + (now = System.nanoTime()));
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

}
