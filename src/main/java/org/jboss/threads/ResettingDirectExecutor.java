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

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A direct executor which resets the thread-local maps after every task (if possible).
 */
final class ResettingDirectExecutor implements DirectExecutor {
    private final DirectExecutor delegate;

    private static final Field THREAD_LOCAL_MAP_FIELD;
    private static final Field INHERITABLE_THREAD_LOCAL_MAP_FIELD;
    private static final RuntimePermission MODIFY_THREAD_PERMISSION = new RuntimePermission("modifyThread");

    static {
        THREAD_LOCAL_MAP_FIELD = AccessController.doPrivileged(new PrivilegedAction<Field>() {
            public Field run() {
                final Field field;
                try {
                    field = Thread.class.getDeclaredField("threadLocals");
                    field.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    return null;
                }
                return field;
            }
        });
        INHERITABLE_THREAD_LOCAL_MAP_FIELD = AccessController.doPrivileged(new PrivilegedAction<Field>() {
            public Field run() {
                final Field field;
                try {
                    field = Thread.class.getDeclaredField("inheritableThreadLocals");
                    field.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    return null;
                }
                return field;
            }
        });
    }

    /**
     * Construct a new instance.
     *
     * @param delegate the executor which will actually execute the task
     */
    ResettingDirectExecutor(DirectExecutor delegate) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(MODIFY_THREAD_PERMISSION);
        }
        this.delegate = delegate;
    }

    private static void clear(final Thread currentThread, final Field field) {
        try {
            field.set(currentThread, null);
        } catch (IllegalAccessException e) {
            // ignore
        }
    }

    /** {@inheritDoc} */
    public void execute(Runnable command) {
        try {
            delegate.execute(command);
        } finally {
            final Thread thread = Thread.currentThread();
            clear(thread, THREAD_LOCAL_MAP_FIELD);
            clear(thread, INHERITABLE_THREAD_LOCAL_MAP_FIELD);
        }
    }
}
