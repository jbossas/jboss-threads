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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public final class JBossThreadFactory implements ThreadFactory {
    private final ThreadGroup threadGroup;
    private final Boolean daemon;
    private final Integer initialPriority;
    private final List<Appender> nameAppenderList;
    private final InterruptHandler[] interruptHandlers;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private final Long stackSize;

    private final AtomicLong factoryThreadIndexSequence = new AtomicLong(1L);

    private final long factoryIndex;

    private static final AtomicLong globalThreadIndexSequence = new AtomicLong(1L);
    private static final AtomicLong factoryIndexSequence = new AtomicLong(1L);

    private static final Appender globalThreadIndexAppender = new Appender() {
        public void appendTo(final StringBuilder target, final ThreadSequenceInfo info) {
            target.append(info.getGlobalThreadNum());
        }
    };
    private static final Appender factoryIndexAppender = new Appender() {
        public void appendTo(final StringBuilder target, final ThreadSequenceInfo info) {
            target.append(info.getFactoryNum());
        }
    };
    private static final Appender perFactoryThreadIndexAppender = new Appender() {
        public void appendTo(final StringBuilder target, final ThreadSequenceInfo info) {
            target.append(info.getPerFactoryThreadNum());
        }
    };
    private static final Appender percentAppender = new Appender() {
        public void appendTo(final StringBuilder target, final ThreadSequenceInfo info) {
            target.append('%');
        }
    };
    private final Appender groupPathAppender;

    private static void appendParent(ThreadGroup group, StringBuilder builder) {
        final ThreadGroup parent = group.getParent();
        if (parent != null) {
            appendParent(parent, builder);
            builder.append(':');
        }
        builder.append(group.getName());
    }

    public JBossThreadFactory(ThreadGroup threadGroup, final Boolean daemon, final Integer initialPriority, String namePattern, final InterruptHandler[] interruptHandlers, final Thread.UncaughtExceptionHandler uncaughtExceptionHandler, final Long stackSize) {
        if (threadGroup == null) {
            final SecurityManager sm = System.getSecurityManager();
            threadGroup = sm != null ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }
        this.threadGroup = threadGroup;
        this.daemon = daemon;
        this.initialPriority = initialPriority;
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        this.interruptHandlers = interruptHandlers;
        this.stackSize = stackSize;
        final StringBuilder builder = new StringBuilder();
        appendParent(threadGroup, builder);
        final String groupPath = builder.toString();
        groupPathAppender = new StringAppender(groupPath);

        factoryIndex = factoryIndexSequence.getAndIncrement();
        if (namePattern == null) {
            namePattern = "pool-%f-thread-%t";
        }
        List<Appender> appenders = new ArrayList<Appender>();
        final int len = namePattern.length();
        for (int i = 0;; ) {
            final int n = namePattern.indexOf('%', i);
            if (n == -1) {
                if (i < len) {
                    appenders.add(new StringAppender(namePattern.substring(i)));
                }
                break;
            }
            if (n > i) {
                appenders.add(new StringAppender(namePattern.substring(i, n)));
            }
            if (n >= len - 1) {
                break;
            }
            final char c = namePattern.charAt(n + 1);
            switch (c) {
                case 't': {
                    appenders.add(perFactoryThreadIndexAppender);
                    break;
                }
                case 'g': {
                    appenders.add(globalThreadIndexAppender);
                    break;
                }
                case 'f': {
                    appenders.add(factoryIndexAppender);
                    break;
                }
                case 'p': {
                    appenders.add(groupPathAppender);
                    break;
                }
                case '%': {
                    appenders.add(percentAppender);
                    break;
                }
            }
            i = n + 2;
        }
        nameAppenderList = appenders;
    }

    public Thread newThread(final Runnable target) {
        return AccessController.doPrivileged(new ThreadCreateAction(target));
    }

    private final class ThreadCreateAction implements PrivilegedAction<Thread> {
        private final Runnable target;

        private ThreadCreateAction(final Runnable target) {
            this.target = target;
        }

        public Thread run() {
            final JBossThread thread;
            final ThreadSequenceInfo info = new ThreadSequenceInfo(globalThreadIndexSequence.getAndIncrement(), factoryThreadIndexSequence.getAndIncrement(), factoryIndex);
            final StringBuilder nameBuilder = new StringBuilder();
            for (Appender appender : nameAppenderList) {
                appender.appendTo(nameBuilder, info);
            }
            if (stackSize != null) {
                thread = new JBossThread(interruptHandlers, threadGroup, target, nameBuilder.toString(), stackSize.longValue());
            } else {
                thread = new JBossThread(interruptHandlers, threadGroup, target, nameBuilder.toString());
            }
            if (initialPriority != null) thread.setPriority(initialPriority.intValue());
            if (daemon != null) thread.setDaemon(daemon.booleanValue());
            if (uncaughtExceptionHandler != null) thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return thread;
        }
    }

    private static final class StringAppender implements Appender {
        private final String string;

        private StringAppender(final String string) {
            this.string = string;
        }

        public void appendTo(final StringBuilder target, final ThreadSequenceInfo info) {
            target.append(string);
        }
    }

    private static final class ThreadSequenceInfo {
        private final long globalThreadNum;
        private final long perFactoryThreadNum;
        private final long factoryNum;

        private ThreadSequenceInfo(final long globalThreadNum, final long perFactoryThreadNum, final long factoryNum) {
            this.globalThreadNum = globalThreadNum;
            this.perFactoryThreadNum = perFactoryThreadNum;
            this.factoryNum = factoryNum;
        }

        public long getGlobalThreadNum() {
            return globalThreadNum;
        }

        public long getPerFactoryThreadNum() {
            return perFactoryThreadNum;
        }

        public long getFactoryNum() {
            return factoryNum;
        }
    }

    private interface Appender {
        void appendTo(StringBuilder target, ThreadSequenceInfo info);
    }
}
