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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

class PrivilegedExecutor implements DirectExecutor {

    private final DirectExecutor delegate;
    private final AccessControlContext context;

    PrivilegedExecutor(final DirectExecutor delegate) {
        this.delegate = delegate;
        this.context = AccessController.getContext();
    }

    public void execute(final Runnable command) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            delegate.execute(command);
            return null;
        }, context);
    }

    public String toString() {
        return String.format("%s (for %s) -> %s", super.toString(), context, delegate);
    }
}
