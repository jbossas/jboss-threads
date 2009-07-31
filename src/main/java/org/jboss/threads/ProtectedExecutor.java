/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.util.concurrent.Executor;

/**
 * An executor that simply delegates to another executor.  Use instances of this class to hide extra methods on
 * another executor.
 */
public class ProtectedExecutor implements Executor {
    private final Executor delegate;

    public ProtectedExecutor(final Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Execute a task by passing it to the delegate executor.
     *
     * @param command the task
     */
    public void execute(final Runnable command) {
        delegate.execute(command);
    }

    public String toString() {
        return String.format("%s -> %s", super.toString(), delegate);
    }
}
