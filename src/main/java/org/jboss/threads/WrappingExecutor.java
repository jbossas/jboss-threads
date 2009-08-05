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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executor;

/**
 * An executor which runs a task within the given direct executor.
 */
public interface WrappingExecutor extends Executor {

    /**
     * Run the given task directly, without a wrapper.
     *
     * @param task the task to run
     * @throws RejectedExecutionException if the execution was rejected for some reason
     */
    void execute(Runnable task) throws RejectedExecutionException;

    /**
     * Run the given task within the given wrapper.
     *
     * @param directExecutor the task wrapper
     * @param task the task to run
     * @throws RejectedExecutionException if the execution was rejected for some reason
     */
    void execute(DirectExecutor directExecutor, Runnable task) throws RejectedExecutionException;
}
