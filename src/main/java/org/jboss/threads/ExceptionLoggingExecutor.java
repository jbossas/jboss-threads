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

import org.jboss.logging.Logger;

class ExceptionLoggingExecutor implements DirectExecutor {

    private final DirectExecutor delegate;
    private final Logger log;
    private final Logger.Level level;

    ExceptionLoggingExecutor(final DirectExecutor delegate, final Logger log, final Logger.Level level) {
        this.delegate = delegate;
        this.log = log;
        this.level = level;
    }

    ExceptionLoggingExecutor(final DirectExecutor delegate, final Logger log) {
        this(delegate, log, Logger.Level.ERROR);
    }

    public void execute(final Runnable command) {
        try {
            delegate.execute(command);
        } catch (Throwable t) {
            log.logf(level, t, "Exception thrown from thread task");
        }
    }

    public String toString() {
        return String.format("%s to \"%s\" -> %s", super.toString(), log.getName(), delegate);
    }
}
