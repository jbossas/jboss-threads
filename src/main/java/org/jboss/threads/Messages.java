/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "JBTHR", length = 5)
interface Messages extends BasicLogger {
    Messages msg = Logger.getMessageLogger(Messages.class, "org.jboss.threads");
    Messages intMsg = Logger.getMessageLogger(Messages.class, "org.jboss.threads.interrupt-handler");

    // version
    @Message(value = "JBoss Threads version %s")
    @LogMessage(level = Logger.Level.DEBUG)
    void version(String version);

    // execution

//    @Message(id = 1, value = "Thread factory did not produce a thread")

//    @Message(id = 2, value = "Task limit reached")

//    @Message(id = 3, value = "Operation timed out")

//    @Message(id = 4, value = "Operation was cancelled")

//    @Message(id = 5, value = "Operation failed")

//    @Message(id = 6, value = "Unable to add new thread to the running set")

    // @Message(id = 7, value = "Task execution interrupted")

//    @Message(id = 8, value = "Task rejected")

    @Message(id = 9, value = "Executor has been shut down")
    StoppedExecutorException shutDownInitiated();

    // @Message(id = 10, value = "Task execution timed out")

//    @Message(id = 11, value = "Task execution failed for task %s")

    @Message(id = 12, value = "Cannot await termination of a thread pool from one of its own threads")
    IllegalStateException cannotAwaitWithin();

//    @Message(id = 13, value = "No executors available to run task")

//    @Message(id = 14, value = "Error submitting task %s to executor")

    // validation

//    @Message(id = 100, value = "Keep-alive may only be set to 0 for this executor type")

//    @Message(id = 101, value = "Cannot reduce maximum threads below current thread number of running threads")

//    @Message(id = 102, value = "Empty array parameter is not empty")

    @Message(id = 103, value = "The current thread does not support interrupt handlers")
    IllegalStateException noInterruptHandlers();

    @Message(id = 104, value = "Executor is not shut down")
    @Deprecated
    IllegalStateException notShutDown();

//    @Message(id = 105, value = "Concurrent modification of collection detected")

//    @Message(id = 106, value = "No such element (iteration past end)")

//    @Message(id = 107, value = "Unknown throwable received")

    @Message(id = 108, value = "Interrupt handler %s threw an exception")
    @LogMessage(level = Logger.Level.ERROR)
    void interruptHandlerThrew(@Cause Throwable cause, InterruptHandler interruptHandler);

    // security

    @Message(id = 200, value = "%s() not allowed on container-managed executor")
    SecurityException notAllowedContainerManaged(String methodName);
}
