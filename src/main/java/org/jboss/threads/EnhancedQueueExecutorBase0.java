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

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;
import java.util.concurrent.AbstractExecutorService;

/**
 * EQE base class: shared utilities and initial padding.
 */
abstract class EnhancedQueueExecutorBase0 extends AbstractExecutorService {
    /**
     * Padding fields.
     */
    @SuppressWarnings("unused")
    int p00, p01, p02, p03,
        p04, p05, p06, p07,
        p08, p09, p0A, p0B,
        p0C, p0D, p0E, p0F;

    EnhancedQueueExecutorBase0() {}

    static int readIntPropertyPrefixed(String name, int defVal) {
        try {
            return Integer.parseInt(readPropertyPrefixed(name, Integer.toString(defVal)));
        } catch (NumberFormatException ignored) {
            return defVal;
        }
    }

    static boolean readBooleanPropertyPrefixed(String name, boolean defVal) {
        return Boolean.parseBoolean(readPropertyPrefixed(name, Boolean.toString(defVal)));
    }

    static String readPropertyPrefixed(String name, String defVal) {
        return readProperty("jboss.threads.eqe." + name, defVal);
    }

    static String readProperty(String name, String defVal) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return readPropertyRaw(name, defVal);
                }
            });
        } else {
            return readPropertyRaw(name, defVal);
        }
    }

    static String readPropertyRaw(final String name, final String defVal) {
        return System.getProperty(name, defVal);
    }
}
