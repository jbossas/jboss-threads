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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.wildfly.common.Assert;

/**
 * A task which depends on other tasks, and which may have tasks depending upon it.  Such a task is automatically
 * run when using a provided executor when all its dependencies are satisfied.
 */
public final class Dependency {

    private static final AtomicIntegerFieldUpdater<Dependency> depUpdater = AtomicIntegerFieldUpdater.newUpdater(Dependency.class, "remainingDependencies");

    /**
     * Number of dependencies left before this task can start.
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    private volatile int remainingDependencies;

    private final Executor executor;
    private final Object lock = new Object();
    private Runner runner;
    private State state;

    Dependency(final Executor executor, final Runnable runnable, final int initialDepCount) {
        this.executor = executor;
        synchronized (lock) {
            runner = new Runner(runnable);
            state = State.WAITING;
            remainingDependencies = initialDepCount;
        }
    }

    void addDependent(Dependency task) {
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case RUNNING: runner.dependents.add(task); return;
                case FAILED: return;
                case DONE: break; // fall out of lock
                default: throw Assert.impossibleSwitchCase(state);
            }
        }
        task.dependencyFinished();
    }

    void dependencyFinished() {
        final AtomicIntegerFieldUpdater<Dependency> updater = depUpdater;
        final int res = updater.decrementAndGet(this);
        if (res == 0) {
            final Dependency.Runner runner = this.runner;
            synchronized (lock) {
                try {
                    executor.execute(runner);
                    state = State.RUNNING;
                } catch (RejectedExecutionException e) {
                    Messages.msg.taskSubmitFailed(e, runner.runnable);
                    state = State.FAILED;
                    // clear stuff out since this object will likely be kept alive longer than these objects need to be
                    runner.runnable = null;
                    runner.dependents = null;
                    this.runner = null;
                }
            }
        } else if (res < 0) {
            // oops?
            updater.incrementAndGet(this);
        }
    }

    private void dependencyFailed() {
        final AtomicIntegerFieldUpdater<Dependency> updater = depUpdater;
        final int res = updater.decrementAndGet(this);
        synchronized (lock) {
            state = State.FAILED;
            final Dependency.Runner runner = this.runner;
            // clear stuff out since this object will likely be kept alive longer than these objects need to be
            runner.runnable = null;
            runner.dependents = null;
            this.runner = null;
        }
        if (res < 0) {
            // oops?
            updater.incrementAndGet(this);
        }
    }

    private class Runner implements Runnable {

        private List<Dependency> dependents = new ArrayList<Dependency>();
        private Runnable runnable;

        public Runner(final Runnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            boolean ok = false;
            CTH.set(Dependency.this);
            try {
                runnable.run();
                ok = true;
            } finally {
                CTH.set(null);
                final List<Dependency> tasks;
                synchronized (lock) {
                    tasks = dependents;
                    // clear stuff out in case some stupid executor holds on to the runnable
                    dependents = null;
                    runnable = null;
                    runner = null;
                    state = ok ? State.DONE : State.FAILED;
                }
                if (ok) {
                    for (Dependency task : tasks) {
                        task.dependencyFinished();
                    }
                } else {
                    for (Dependency task : tasks) {
                        task.dependencyFailed();
                    }
                }
            }
        }
    }

    private enum State {
        /**
         * Waiting for dependencies to be resolved.
         */
        WAITING,
        /**
         * Now running.
         */
        RUNNING,
        /**
         * Execution failed.
         */
        FAILED,
        /**
         * Execution completed.
         */
        DONE,
    }

    /**
     * Get the dependency task which this thread is currently running.  This may be used to add dependencies on the currently
     * running task.
     *
     * @return the currently running task, or {@code null} if the current thread is not running a dependency task
     */
    public static Dependency currentTask() {
        return CTH.get();
    }

    private static final ThreadLocal<Dependency> CTH = new ThreadLocal<Dependency>();
}
