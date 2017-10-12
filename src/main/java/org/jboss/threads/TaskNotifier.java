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

/**
 * A notifier which is called when tasks start, stop, or fail.
 *
 * @param <R> the task type
 * @param <A> the attachment type
 */
public interface TaskNotifier<R extends Runnable, A> {

    /**
     * A task was started.
     *
     * @param runnable the task
     * @param attachment the attachment
     */
    void started(R runnable, A attachment);

    /**
     * A task has failed.
     *
     * @param runnable the task
     * @param reason the reason for the failure
     * @param attachment the attachment
     */
    void failed(R runnable, Throwable reason, A attachment);

    /**
     * A task has completed.
     *
     * @param runnable the task
     * @param attachment the attachment
     */
    void finished(R runnable, A attachment);
}
