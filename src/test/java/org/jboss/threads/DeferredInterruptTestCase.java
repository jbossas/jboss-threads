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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class DeferredInterruptTestCase {

    @Test
    public void testDeferral() throws Exception {
        final AtomicBoolean delivered0 = new AtomicBoolean();
        final AtomicBoolean deferred = new AtomicBoolean();
        final AtomicBoolean delivered = new AtomicBoolean();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final JBossThread thread = new JBossThread(new Runnable() {
            public void run() {
                Thread.interrupted();
                latch1.countDown();
                LockSupport.parkNanos(3000000000L);
                delivered0.set(Thread.interrupted());
                JBossThread.executeWithInterruptDeferred(new Runnable() {
                    public void run() {
                        latch2.countDown();
                        LockSupport.parkNanos(500000000L);
                        deferred.set(! Thread.interrupted());
                    }
                });
                delivered.set(Thread.interrupted());
            }
        });
        thread.start();
        latch1.await();
        thread.interrupt();
        latch2.await();
        thread.interrupt();
        thread.join();
        assertTrue(delivered0.get());
        assertTrue(deferred.get());
        assertTrue(delivered.get());
    }
}
