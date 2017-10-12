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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.AccessControlContext;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A factory for {@link JBossThread} instances.
 */
public final class JBossThreadFactory implements ThreadFactory {
    private final ThreadGroup threadGroup;
    private final Boolean daemon;
    private final Integer initialPriority;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private final Long stackSize;
    private final String namePattern;

    private final AtomicLong factoryThreadIndexSequence = new AtomicLong(1L);

    private final long factoryIndex;

    private final AccessControlContext creatingContext;

    private static final AtomicLong globalThreadIndexSequence = new AtomicLong(1L);
    private static final AtomicLong factoryIndexSequence = new AtomicLong(1L);

    /**
     * Construct a new instance.  The access control context of the calling thread will be the one used to create
     * new threads if a security manager is installed.
     *
     * @param threadGroup the thread group to assign threads to by default (may be {@code null})
     * @param daemon whether the created threads should be daemon threads, or {@code null} to use the thread group's setting
     * @param initialPriority the initial thread priority, or {@code null} to use the thread group's setting
     * @param namePattern the name pattern string
     * @param uncaughtExceptionHandler the uncaught exception handler, if any
     * @param stackSize the JVM-specific stack size, or {@code null} to leave it unspecified
     */
    public JBossThreadFactory(ThreadGroup threadGroup, final Boolean daemon, final Integer initialPriority, String namePattern, final Thread.UncaughtExceptionHandler uncaughtExceptionHandler, final Long stackSize) {
        if (threadGroup == null) {
            final SecurityManager sm = System.getSecurityManager();
            threadGroup = sm != null ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }
        this.threadGroup = threadGroup;
        this.daemon = daemon;
        this.initialPriority = initialPriority;
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        this.stackSize = stackSize;
        factoryIndex = factoryIndexSequence.getAndIncrement();
        if (namePattern == null) {
            namePattern = "pool-%f-thread-%t";
        }
        this.namePattern = namePattern;
        this.creatingContext = AccessController.getContext();
    }

    /**
     * @deprecated Use {@link #JBossThreadFactory(ThreadGroup, Boolean, Integer, String, Thread.UncaughtExceptionHandler, Long)} instead.
     */
    public JBossThreadFactory(ThreadGroup threadGroup, final Boolean daemon, final Integer initialPriority, String namePattern, final Thread.UncaughtExceptionHandler uncaughtExceptionHandler, final Long stackSize, final AccessControlContext ignored) {
        this(threadGroup, daemon, initialPriority, namePattern, uncaughtExceptionHandler, stackSize);
    }

    public Thread newThread(final Runnable target) {
        final AccessControlContext context;
        if ((context = creatingContext) != null) {
            return AccessController.doPrivileged(new ThreadCreateAction(target), context);
        } else {
            return createThread(target);
        }
    }

    private final class ThreadCreateAction implements PrivilegedAction<Thread> {
        private final Runnable target;

        private ThreadCreateAction(final Runnable target) {
            this.target = target;
        }

        public Thread run() {
            return createThread(target);
        }
    }

    private Thread createThread(final Runnable target) {
        final ThreadNameInfo nameInfo = new ThreadNameInfo(globalThreadIndexSequence.getAndIncrement(), factoryThreadIndexSequence.getAndIncrement(), factoryIndex);
        final JBossThread thread;
        if (stackSize != null) {
            thread = new JBossThread(threadGroup, target, "<new>", stackSize.longValue());
        } else {
            thread = new JBossThread(threadGroup, target);
        }
        thread.setThreadNameInfo(nameInfo);
        thread.setName(nameInfo.format(thread, namePattern));
        if (initialPriority != null) thread.setPriority(initialPriority.intValue());
        if (daemon != null) thread.setDaemon(daemon.booleanValue());
        if (uncaughtExceptionHandler != null) thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        return thread;
    }
}
