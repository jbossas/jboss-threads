package org.jboss.threads;

import java.lang.reflect.Field;
import java.security.PrivilegedAction;

/**
 */
final class DeclaredFieldAction implements PrivilegedAction<Field> {
    private final Class<?> clazz;
    private final String fieldName;

    DeclaredFieldAction(final Class<?> clazz, final String fieldName) {
        this.clazz = clazz;
        this.fieldName = fieldName;
    }

    public Field run() {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
