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

import java.util.ArrayList;
import java.util.List;

/**
 * A simple shutdown-listenable registry.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SimpleShutdownListenable implements ShutdownListenable {
    private List<Registration<?>> list = new ArrayList<Registration<?>>();

    /** {@inheritDoc} */
    public <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        synchronized (this) {
            final Registration<A> reg = new Registration<A>(shutdownListener, attachment);
            if (list == null) {
                reg.run();
            } else {
                list.add(reg);
            }
        }
    }

    /**
     * Run and remove all registered listeners, and mark this object as having been shut down so that
     * future listeners are invoked immediately.
     */
    public void shutdown() {
        synchronized (this) {
            final List<Registration<?>> list = this.list;
            this.list = null;
            if (list != null) {
                for (Registration<?> registration : list) {
                    registration.run();
                }
            }
        }
    }

    private static final class Registration<A> {
        private final EventListener<A> listener;
        private final A attachment;

        private Registration(final EventListener<A> listener, final A attachment) {
            this.listener = listener;
            this.attachment = attachment;
        }

        void run() {
            try {
                listener.handleEvent(attachment);
            } catch (Throwable t) {
                // todo log it
            }
        }
    }
}
