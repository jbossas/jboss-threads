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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

class PrivilegedExecutor implements DirectExecutor {

    private final DirectExecutor delegate;
    private final AccessControlContext context;

    PrivilegedExecutor(final DirectExecutor delegate, final AccessControlContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    public void execute(final Runnable command) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    delegate.execute(command);
                    return null;
                }
            }, context);
        } else {
            delegate.execute(command);
        }
    }

    public String toString() {
        return String.format("%s (for %s) -> %s", super.toString(), context, delegate);
    }
}
