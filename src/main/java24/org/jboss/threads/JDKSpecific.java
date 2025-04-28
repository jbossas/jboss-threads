package org.jboss.threads;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.UndeclaredThrowableException;

final class JDKSpecific {
    private JDKSpecific() {}

    static void setThreadContextClassLoader(Thread thread, ClassLoader classLoader) {
        thread.setContextClassLoader(classLoader);
    }

    static ClassLoader getThreadContextClassLoader(Thread thread) {
        return thread.getContextClassLoader();
    }

    static final class ThreadAccess {
        private static final MethodHandle setThreadLocalsHandle;
        private static final MethodHandle setInheritableThreadLocalsHandle;

        static {
            try {
                MethodHandles.Lookup threadLookup = MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
                setThreadLocalsHandle = threadLookup.unreflectVarHandle(Thread.class.getDeclaredField("threadLocals")).toMethodHandle(VarHandle.AccessMode.SET).asType(MethodType.methodType(void.class, Thread.class, Object.class));
                setInheritableThreadLocalsHandle = threadLookup.unreflectVarHandle(Thread.class.getDeclaredField("inheritableThreadLocals")).toMethodHandle(VarHandle.AccessMode.SET).asType(MethodType.methodType(void.class, Thread.class, Object.class));
            } catch (IllegalAccessException e) {
                Module myModule = ThreadAccess.class.getModule();
                String myName = myModule.isNamed() ? myModule.getName() : "ALL-UNNAMED";
                throw new IllegalAccessError(e.getMessage() +
                    "; to use the thread-local-reset capability on Java 24 or later, use this JVM option: --add-opens java.base/java.lang=" + myName);
            } catch (NoSuchFieldException e) {
                throw new NoSuchFieldError(e.getMessage());
            }
        }

        static void clearThreadLocals() {
            final Thread thread = Thread.currentThread();
            try {
                setThreadLocalsHandle.invokeExact(thread, (Object) null);
                setInheritableThreadLocalsHandle.invokeExact(thread, (Object) null);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        }
    }
}
