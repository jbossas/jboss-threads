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
        final List<Registration<?>> list;
        synchronized (this) {
            list = this.list;
            this.list = null;
        }
        if (list != null) {
            for (Registration<?> registration : list) {
                registration.run();
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
