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

class ThreadFormattedNameExecutor implements DirectExecutor {

    private final String nameFormat;
    private final DirectExecutor delegate;

    ThreadFormattedNameExecutor(final String nameFormat, final DirectExecutor delegate) {
        this.nameFormat = nameFormat;
        this.delegate = delegate;
    }

    public void execute(final Runnable command) {
        final JBossThread thr = JBossThread.currentThread();
        final String oldName = thr.getName();
        thr.setName(thr.getThreadNameInfo().format(thr, nameFormat));
        try {
            delegate.execute(command);
        } finally {
            thr.setName(oldName);
        }
    }

    public String toString() {
        return String.format("%s format=\"%s\" -> %s", super.toString(), nameFormat, delegate);
    }
}