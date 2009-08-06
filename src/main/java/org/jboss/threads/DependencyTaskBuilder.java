/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.concurrent.Executor;

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
        if (dependency == null) {
            throw new NullPointerException("dependency is null");
        }
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
