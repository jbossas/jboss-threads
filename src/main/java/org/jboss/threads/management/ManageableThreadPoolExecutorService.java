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

package org.jboss.threads.management;

import java.util.concurrent.ExecutorService;

import org.wildfly.common.annotation.NotNull;

/**
 * A thread pool for which an MBean can be obtained.
 */
public interface ManageableThreadPoolExecutorService extends ExecutorService {
    /**
     * Create or acquire an MXBean instance for this thread pool.  Note that the thread pool itself will not
     * do anything in particular to register (or unregister) the MXBean with a JMX server; that is the caller's
     * responsibility.
     *
     * @return the MXBean instance (must not be {@code null})
     */
    @NotNull
    StandardThreadPoolMXBean getThreadPoolMXBean();
}
