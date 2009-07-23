package org.jboss.threads;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A direct executor which resets the thread-local maps after every task (if possible).
 */
public class ResettingDirectExecutor implements DirectExecutor {
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
                    field = Thread.class.getDeclaredField("threadLocals");
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
    public ResettingDirectExecutor(DirectExecutor delegate) {
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
