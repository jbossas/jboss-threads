package org.jboss.threads;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.misc.Unsafe;

final class JDKSpecific {
    private JDKSpecific() {}

    private static final Unsafe unsafe;
    private static final long contextClassLoaderOffs;

    static {
        unsafe = AccessController.doPrivileged(new PrivilegedAction<Unsafe>() {
            public Unsafe run() {
                try {
                    Field field = Unsafe.class.getDeclaredField("theUnsafe");
                    field.setAccessible(true);
                    return (Unsafe) field.get(null);
                } catch (IllegalAccessException e) {
                    IllegalAccessError error = new IllegalAccessError(e.getMessage());
                    error.setStackTrace(e.getStackTrace());
                    throw error;
                } catch (NoSuchFieldException e) {
                    NoSuchFieldError error = new NoSuchFieldError(e.getMessage());
                    error.setStackTrace(e.getStackTrace());
                    throw error;
                }
            }
        });
        try {
            contextClassLoaderOffs = unsafe.objectFieldOffset(Thread.class.getDeclaredField("contextClassLoader"));
        } catch (NoSuchFieldException e) {
            NoSuchFieldError error = new NoSuchFieldError(e.getMessage());
            error.setStackTrace(e.getStackTrace());
            throw error;
        }
    }

    static void setThreadContextClassLoader(Thread thread, ClassLoader classLoader) {
        unsafe.putObject(thread, contextClassLoaderOffs, classLoader);
    }

    static ClassLoader getThreadContextClassLoader(Thread thread) {
        return (ClassLoader) unsafe.getObject(thread, contextClassLoaderOffs);
    }

    static final class ThreadAccess {
        private static final long threadLocalMapOffs;
        private static final long inheritableThreadLocalMapOffs;

        static {
            try {
                threadLocalMapOffs = unsafe.objectFieldOffset(Thread.class.getDeclaredField("threadLocals"));
                inheritableThreadLocalMapOffs = unsafe.objectFieldOffset(Thread.class.getDeclaredField("inheritableThreadLocals"));
            } catch (NoSuchFieldException e) {
                NoSuchFieldError error = new NoSuchFieldError(e.getMessage());
                error.setStackTrace(e.getStackTrace());
                throw error;
            }
        }

        static void clearThreadLocals() {
            Thread thread = Thread.currentThread();
            unsafe.putObject(thread, threadLocalMapOffs, null);
            unsafe.putObject(thread, inheritableThreadLocalMapOffs, null);
        }
    }
}
