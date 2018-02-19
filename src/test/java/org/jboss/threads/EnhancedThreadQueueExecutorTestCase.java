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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class EnhancedThreadQueueExecutorTestCase {

    @Test
    public void testEnhancedExecutorShutdownNoTasks() throws Exception {
        final CountDownLatch terminateLatch = new CountDownLatch(1);
        EnhancedQueueExecutor executor = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(10)
                .setKeepAliveTime(1, TimeUnit.NANOSECONDS)
                .setTerminationTask(new Runnable() {
                    @Override
                    public void run() {
                        terminateLatch.countDown();
                    }
                })
                .build();

        executor.shutdown();
        Assert.assertTrue(terminateLatch.await(10, TimeUnit.SECONDS));
    }

    @Test //JBTHR-50
    public void testEnhancedExecutorShutdown() throws Exception {
        final CountDownLatch terminateLatch = new CountDownLatch(1);
        EnhancedQueueExecutor executor = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(10)
                .setKeepAliveTime(1, TimeUnit.NANOSECONDS)
                .setTerminationTask(new Runnable() {
                    @Override
                    public void run() {
                        terminateLatch.countDown();
                    }
                })
                .build();

        for(int i = 0; i < 10000; ++i) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            });
        }
        executor.shutdown();
        Assert.assertTrue(terminateLatch.await(10, TimeUnit.SECONDS));
    }
}
