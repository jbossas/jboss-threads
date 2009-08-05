/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

    private final AccessControlContext CREATING_CONTEXT;

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
        CREATING_CONTEXT = AccessController.getContext();
    }

    public Thread newThread(final Runnable target) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new ThreadCreateAction(target), CREATING_CONTEXT);
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
