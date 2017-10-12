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

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

final class ThreadLocalResetter implements Runnable {
    private static final ThreadLocalResetter INSTANCE = new ThreadLocalResetter();

    private static final Field THREAD_LOCAL_MAP_FIELD;
    private static final Field INHERITABLE_THREAD_LOCAL_MAP_FIELD;

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

    static ThreadLocalResetter getInstance() {
        return INSTANCE;
    }

    private ThreadLocalResetter() {
    }

    public void run() {
        final Thread thread = Thread.currentThread();
        clear(thread, THREAD_LOCAL_MAP_FIELD);
        clear(thread, INHERITABLE_THREAD_LOCAL_MAP_FIELD);
    }

    private static void clear(final Thread currentThread, final Field field) {
        try {
            if (field != null) field.set(currentThread, null);
        } catch (IllegalAccessException e) {
            // ignore
        }
    }

    public String toString() {
        return "Thread-local resetting Runnable";
    }
}
