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

class ContextClassLoaderSavingRunnable implements Runnable {

    private final ClassLoader loader;
    private final Runnable delegate;

    ContextClassLoaderSavingRunnable(final ClassLoader loader, final Runnable delegate) {
        this.loader = loader;
        this.delegate = delegate;
    }

    public void run() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader old = JBossExecutors.getAndSetContextClassLoader(currentThread, loader);
        try {
            delegate.run();
        } finally {
            JBossExecutors.setContextClassLoader(currentThread, old);
        }
    }
}
