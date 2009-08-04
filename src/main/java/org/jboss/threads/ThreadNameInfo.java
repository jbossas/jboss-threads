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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Thread name information.
 */
final class ThreadNameInfo {
    private final long globalThreadSequenceNum;
    private final long perFactoryThreadSequenceNum;
    private final long factorySequenceNum;

    ThreadNameInfo(final long globalThreadSequenceNum, final long perFactoryThreadSequenceNum, final long factorySequenceNum) {
        this.globalThreadSequenceNum = globalThreadSequenceNum;
        this.perFactoryThreadSequenceNum = perFactoryThreadSequenceNum;
        this.factorySequenceNum = factorySequenceNum;
    }

    public long getGlobalThreadSequenceNum() {
        return globalThreadSequenceNum;
    }

    public long getPerFactoryThreadSequenceNum() {
        return perFactoryThreadSequenceNum;
    }

    public long getFactorySequenceNum() {
        return factorySequenceNum;
    }

    private static final Pattern searchPattern = Pattern.compile("([^%]+)|%.");

    /**
     * Format the thread name string.
     * <ul>
     * <li>{@code %%} - emit a percent sign</li>
     * <li>{@code %t} - emit the per-factory thread sequence number</li>
     * <li>{@code %g} - emit the global thread sequence number</li>
     * <li>{@code %f} - emit the factory sequence number</li>
     * <li>{@code %p} - emit the {@code ":"}-separated thread group path</li>
     * <li>{@code %i} - emit the thread ID</li>
     * <li>{@code %G} - emit the thread group name</li>
     * </ul>
     *
     * @param thread the thread
     * @param formatString the format string
     * @return the thread name string
     */
    public String format(Thread thread, String formatString) {
        final StringBuilder builder = new StringBuilder(formatString.length() * 5);
        final ThreadGroup group = thread.getThreadGroup();
        final Matcher matcher = searchPattern.matcher(formatString);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                builder.append(matcher.group());
            } else {
                switch (matcher.group().charAt(1)) {
                    case '%': builder.append('%'); break;
                    case 't': builder.append(perFactoryThreadSequenceNum); break;
                    case 'g': builder.append(globalThreadSequenceNum); break;
                    case 'f': builder.append(factorySequenceNum); break;
                    case 'p': if (group != null) appendGroupPath(group, builder); break;
                    case 'i': builder.append(thread.getId());
                    case 'G': if (group != null) builder.append(group.getName()); break;
                }
            }
        }
        return builder.toString();
    }

    private static void appendGroupPath(ThreadGroup group, StringBuilder builder) {
        final ThreadGroup parent = group.getParent();
        if (parent != null) {
            appendGroupPath(parent, builder);
            builder.append(':');
        }
        builder.append(group.getName());
    }
}
