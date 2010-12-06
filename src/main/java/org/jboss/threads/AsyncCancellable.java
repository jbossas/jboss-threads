/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

/**
 * An interface which supports asynchronous cancellation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface AsyncCancellable {

    /**
     * Handle an asynchronous cancellation.  Does not block, and may be called more than once;
     * in particular, this method may be re-invoked to set the {@code interruptionDesired} flag
     * even if it has already been called without that flag set before.  Otherwise, this method is
     * idempotent, and thus may be called more than once without additional effect.
     *
     * @param interruptionDesired {@code true} if interruption of threads is desired
     */
    void asyncCancel(boolean interruptionDesired);
}
