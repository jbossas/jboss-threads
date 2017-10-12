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

import java.util.concurrent.ThreadFactory;

class WrappingThreadFactory implements ThreadFactory {

    private final ThreadFactory delegate;
    private final DirectExecutor taskWrapper;

    WrappingThreadFactory(final ThreadFactory delegate, final DirectExecutor taskWrapper) {
        this.delegate = delegate;
        this.taskWrapper = taskWrapper;
    }

    public Thread newThread(final Runnable r) {
        return delegate.newThread(JBossExecutors.executorTask(taskWrapper, r));
    }

    public String toString() {
        return String.format("%s (%s) -> %s", super.toString(), taskWrapper, delegate);
    }
}
