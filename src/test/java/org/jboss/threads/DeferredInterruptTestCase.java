/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import junit.framework.TestCase;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class DeferredInterruptTestCase extends TestCase {

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
