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

/**
 * Specify the way a task rejection should be handled.
 */
public enum RejectionPolicy {

    /**
     * Abort execution with an exception.
     */
    ABORT,
    /**
     * Wait until there is room in the queue, or the calling thread is interrupted.
     */
    BLOCK,
    /**
     * Discard the excess task silently.
     */
    DISCARD,
    /**
     * Discard the oldest task in the queue silently, and enqueue the new task.  If the queue has no capacity
     * then the new task will be discarded.
     */
    DISCARD_OLDEST,
    /**
     * Hand off the task to another executor.
     */
    HANDOFF,
}
