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

import org.jboss.logging.Logger;

/**
 *
 */
public final class JBossThread extends Thread {
    private static final Logger log = Logger.getLogger(JBossThread.class);

    private final InterruptHandler[] interruptHandlers;

    public JBossThread(final InterruptHandler[] handlers, final ThreadGroup group, final Runnable target, final String name, final long stackSize) {
        super(group, target, name, stackSize);
        interruptHandlers = handlers;
    }

    public JBossThread(final InterruptHandler[] handlers, final ThreadGroup group, final Runnable target, final String name) {
        super(group, target, name);
        interruptHandlers = handlers;
    }

    public void interrupt() {
        try {
            super.interrupt();
        } finally {
            if (interruptHandlers != null) {
                for (InterruptHandler interruptHandler : interruptHandlers) try {
                    interruptHandler.handleInterrupt(this);
                } catch (Throwable t) {
                    log.errorf(t, "Interrupt handler %s threw an exception", interruptHandler);
                }
            }
        }
    }

    public void run() {
        log.tracef("Starting thread %s", this);
        try {
            super.run();
        } finally {
            log.tracef("Terminating thread %s", this);
        }
    }
}
