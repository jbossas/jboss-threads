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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jboss.logging.Logger;

/**
 * A task which depends on other tasks, and which may have tasks depending upon it.  Such a task is automatically
 * run when using a provided executor when all its dependencies are satisfied.
 */
public final class Dependency {
    private static final Logger log = Logger.getLogger(Dependency.class);

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
                default: throw new IllegalStateException();
            }
        }
        task.dependencyFinished();
    }

    void dependencyFinished() {
        final AtomicIntegerFieldUpdater<Dependency> updater = depUpdater;
        final int res = updater.decrementAndGet(this);
        if (res == 0) {
            synchronized (lock) {
                try {
                    executor.execute(runner);
                    state = State.RUNNING;
                } catch (RejectedExecutionException e) {
                    log.errorf(e, "Error submitting task %s to executor", runner.runnable);
                    state = State.FAILED;
                    final Dependency.Runner runner = this.runner;
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
        if (res == 0) {
            synchronized (lock) {
                state = State.FAILED;
                final Dependency.Runner runner = this.runner;
                runner.runnable = null;
                runner.dependents = null;
                this.runner = null;
            }
        } else if (res < 0) {
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
                    dependents = null;
                    runnable = null;
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
