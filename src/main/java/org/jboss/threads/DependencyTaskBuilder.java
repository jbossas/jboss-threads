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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.concurrent.Executor;

import org.wildfly.common.Assert;

/**
 * A builder for a dependency task.
 */
public final class DependencyTaskBuilder {
    private final Collection<Dependency> dependencies = new LinkedHashSet<Dependency>();
    private final Executor executor;
    private final Runnable task;

    DependencyTaskBuilder(final Executor executor, final Runnable task) {
        this.executor = executor;
        this.task = task;
    }

    /**
     * Add a dependency.
     *
     * @param dependency the dependency
     * @return this builder
     */
    public DependencyTaskBuilder add(Dependency dependency) {
        Assert.checkNotNullParam("dependency", dependency);
        dependencies.add(dependency);
        return this;
    }

    /**
     * Add many dependencies.
     *
     * @param dependencies the dependencies
     * @return this builder
     */
    public DependencyTaskBuilder add(Collection<Dependency> dependencies) {
        this.dependencies.addAll(dependencies);
        return this;
    }

    /**
     * Add many dependencies.
     *
     * @param dependencies the dependencies
     * @return this builder
     */
    public DependencyTaskBuilder add(Dependency... dependencies) {
        this.dependencies.addAll(Arrays.asList(dependencies));
        return this;
    }

    /**
     * Create, and possibly execute, a dependent task from this builder.
     *
     * @return the new dependent task
     */
    public Dependency create() {
        final Collection<Dependency> dependencies = this.dependencies;
        final Dependency dependentTask = new Dependency(executor, task, dependencies.size() + 1);
        for (Dependency dependency : dependencies) {
            dependency.addDependent(dependentTask);
        }
        dependentTask.dependencyFinished();
        return dependentTask;
    }
}
