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
                    case 'i': builder.append(thread.getId()); break;
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
