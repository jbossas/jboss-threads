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

import java.lang.reflect.Field;
import java.security.AccessController;

final class ThreadLocalResettingRunnable extends DelegatingRunnable {

    ThreadLocalResettingRunnable(final Runnable delegate) {
        super(delegate);
    }

    public void run() {
        try {
            super.run();
        } finally {
            Resetter.run();
        }
    }

    public String toString() {
        return "Thread-local resetting Runnable";
    }

    static final class Resetter {
        private static final long threadLocalMapOffs;
        private static final long inheritableThreadLocalMapOffs;

        static {
            final Field threadLocals = AccessController.doPrivileged(new DeclaredFieldAction(Thread.class, "threadLocals"));
            if (threadLocals == null) {
                threadLocalMapOffs = 0;
            } else {
                threadLocalMapOffs = JBossExecutors.unsafe.objectFieldOffset(threadLocals);
            }
            final Field inheritableThreadLocals = AccessController.doPrivileged(new DeclaredFieldAction(Thread.class, "inheritableThreadLocals"));
            if (inheritableThreadLocals == null) {
                inheritableThreadLocalMapOffs = 0;
            } else {
                inheritableThreadLocalMapOffs = JBossExecutors.unsafe.objectFieldOffset(inheritableThreadLocals);
            }
        }

        static void run() {
            final Thread thread = Thread.currentThread();
            if (threadLocalMapOffs != 0) JBossExecutors.unsafe.putObject(thread, threadLocalMapOffs, null);
            if (inheritableThreadLocalMapOffs != 0) JBossExecutors.unsafe.putObject(thread, inheritableThreadLocalMapOffs, null);
        }
    }
}
