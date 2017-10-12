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

class NotifyingDirectExecutor<A> extends DelegatingDirectExecutor implements DirectExecutor {

    private final TaskNotifier<Runnable, ? super A> notifier;
    private final A attachment;

    NotifyingDirectExecutor(final DirectExecutor delegate, final TaskNotifier<Runnable, ? super A> notifier, final A attachment) {
        super(delegate);
        this.notifier = notifier;
        this.attachment = attachment;
    }

    public void execute(final Runnable command) {
        JBossExecutors.run(command, getDelegate(), notifier, attachment);
    }
}
