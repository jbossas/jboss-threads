/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class QueuelessViewExecutorTest {

    private static final String THREAD_BASE_NAME = "CachedExecutorViewTest-";

    /*
     * Both implementations have enough permits that queues shouldn't
     * be used so the implementations should be equivalent.
     */
    public enum ExecutorType {
        QUEUELESS_VIEW() {
            @Override
            ExecutorService wrap(Executor delegate) {
                return ViewExecutor.builder(delegate)
                        .setQueueLimit(0)
                        .setMaxSize(Short.MAX_VALUE)
                        .build();
            }
        },
        QUEUED() {
            @Override
            ExecutorService wrap(Executor delegate) {
                return ViewExecutor.builder(delegate)
                        .setQueueLimit(Integer.MAX_VALUE)
                        .setMaxSize(Short.MAX_VALUE)
                        .build();
            }
        };

        abstract ExecutorService wrap(Executor delegate);
    }

    @ParameterizedTest
    @EnumSource(QueuelessViewExecutorTest.ExecutorType.class)
    public void testShutdownNow(ExecutorType executorType) throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService cached = cachedExecutor();
        ExecutorService view = executorType.wrap(cached);
        assertThat(view.isShutdown()).isEqualTo(cached.isShutdown()).isFalse();
        assertThat(view.isTerminated()).isEqualTo(cached.isTerminated()).isFalse();
        CountDownLatch executionStartedLatch = new CountDownLatch(1);
        CountDownLatch interruptedLatch = new CountDownLatch(1);
        view.execute(() -> {
            try {
                executionStartedLatch.countDown();
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                // Wait so we can validate the time between shutdown and terminated
                try {
                    interruptedLatch.await();
                } catch (InterruptedException ee) {
                    throw new AssertionError(ee);
                }
            }
        });
        executionStartedLatch.await();
        assertThat(view.shutdownNow()).as("Cached executors have no queue").isEmpty();
        assertThat(view.isShutdown()).isTrue();
        assertThatThrownBy(() -> view.execute(NullRunnable.getInstance()))
                .as("Submitting work after invoking shutdown or shutdownNow should fail")
                .isInstanceOf(RejectedExecutionException.class);
        Awaitility.waitAtMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertThat(interrupted).isTrue();
            assertThat(view.isTerminated()).isFalse();
        });
        interruptedLatch.countDown();
        Awaitility.waitAtMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(view.isTerminated()).as("%s", view).isTrue());

        assertCleanShutdown(cached);
    }

    @ParameterizedTest
    @EnumSource(QueuelessViewExecutorTest.ExecutorType.class)
    public void testShutdownNow_immediatelyAfterTaskIsSubmitted(ExecutorType executorType) throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService cached = cachedExecutor();
        ExecutorService view = executorType.wrap(runnable -> {
            cached.execute(() -> {
                // Emphasize the jitter between when a task is submitted, and when it begins to execute
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                runnable.run();
            });
        });
        assertThat(view.isShutdown()).isEqualTo(cached.isShutdown()).isFalse();
        assertThat(view.isTerminated()).isEqualTo(cached.isTerminated()).isFalse();
        view.execute(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        assertThat(view.shutdownNow()).as("Cached executors have no queue").isEmpty();
        assertThat(view.awaitTermination(3, TimeUnit.SECONDS))
                .as("View failed to terminate within 3 seconds: %s", view)
                .isTrue();
        assertThat(interrupted).as("Task should have been interrupted").isTrue();

        assertCleanShutdown(cached);
    }

    @Timeout(5_000) // Failing awaitTermination should return quickly
    @ParameterizedTest
    @EnumSource(QueuelessViewExecutorTest.ExecutorType.class)
    public void testAwaitTermination(ExecutorType executorType) throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService cached = cachedExecutor();
        ExecutorService view = executorType.wrap(cached);
        assertThat(view.isShutdown()).isEqualTo(cached.isShutdown()).isFalse();
        assertThat(view.isTerminated()).isEqualTo(cached.isTerminated()).isFalse();
        view.execute(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        view.shutdown();
        assertThat(view.awaitTermination(10, TimeUnit.MILLISECONDS))
                .as("Task should not have been interrupted, and is still sleeping")
                .isFalse();

        assertThat(interrupted).as("Task should not be interrupted by 'shutdown'").isFalse();

        assertThat(view.shutdownNow()).as("Cached executors have no queue").isEmpty();
        assertThat(view.awaitTermination(3, TimeUnit.SECONDS))
                .as("Task should have been interrupted: %s", view)
                .isTrue();

        assertThat(interrupted).as("Task should be interrupted by 'shutdownNow'").isTrue();

        assertCleanShutdown(cached);
    }

    @ParameterizedTest
    @EnumSource(QueuelessViewExecutorTest.ExecutorType.class)
    public void testShutdown(ExecutorType executorType) throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService cached = cachedExecutor();
        ExecutorService view = executorType.wrap(cached);
        assertThat(view.isShutdown()).isEqualTo(cached.isShutdown()).isFalse();
        assertThat(view.isTerminated()).isEqualTo(cached.isTerminated()).isFalse();
        CountDownLatch executionStartedLatch = new CountDownLatch(1);
        view.execute(() -> {
            try {
                executionStartedLatch.countDown();
                Thread.sleep(500);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        executionStartedLatch.await();
        view.shutdown();
        assertThat(view.isShutdown()).isTrue();
        assertThatThrownBy(() -> view.execute(NullRunnable.getInstance()))
                .as("Submitting work after invoking shutdown or shutdown should fail")
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(view.isTerminated()).isFalse();
        Awaitility.waitAtMost(600, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(view.isTerminated()).as("%s", view).isTrue());
        assertThat(interrupted).isFalse();

        assertCleanShutdown(cached);
    }

    private static ExecutorService cachedExecutor() {
        AtomicInteger index = new AtomicInteger();
        return Executors.newCachedThreadPool(
                task -> {
                    Thread thread = new Thread(task);
                    thread.setDaemon(true);
                    thread.setName(THREAD_BASE_NAME + index.getAndIncrement());
                    return thread;
                });
    }

    private static void assertCleanShutdown(ExecutorService executor) {
        assertThat(executor.isShutdown()).isFalse();
        assertThat(executor.isTerminated()).isFalse();
        executor.shutdown();
        try {
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS))
                    .as("Failed to clean up the executor")
                    .isTrue();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
