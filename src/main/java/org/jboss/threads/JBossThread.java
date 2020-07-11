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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.wildfly.common.Assert;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.common.function.ExceptionBiFunction;
import org.wildfly.common.function.ExceptionConsumer;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionObjIntConsumer;
import org.wildfly.common.function.ExceptionObjLongConsumer;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.common.function.Functions;

/**
 * A JBoss thread.  Supports logging and extra operations.
 */
public class JBossThread extends Thread {

    private static final int MAX_INTERRUPT_SPINS = AccessController.doPrivileged(new PrivilegedAction<Integer>() {
        public Integer run() {
            return Integer.valueOf(Integer.parseInt(System.getProperty("jboss.threads.interrupt.spins", ProcessorInfo.availableProcessors() == 1 ? "0" : "128")));
        }
    }).intValue();

    static {
        Version.getVersionString();
    }

    private volatile InterruptHandler interruptHandler;
    private ThreadNameInfo threadNameInfo;

    /**
     * The thread is maybe interrupted.  Possible transitions:
     * <ul>
     *     <li>{@link #STATE_INTERRUPT_DEFERRED}</li>
     *     <li>{@link #STATE_INTERRUPT_IN_PROGRESS}</li>
     * </ul>
     */
    private static final int STATE_MAYBE_INTERRUPTED = 0;
    /**
     * The thread is not interrupted, and interrupts will be deferred.  Possible transitions:
     * <ul>
     *     <li>{@link #STATE_MAYBE_INTERRUPTED}</li>
     *     <li>{@link #STATE_INTERRUPT_PENDING}</li>
     * </ul>
     */
    private static final int STATE_INTERRUPT_DEFERRED = 1;
    /**
     * The thread is not interrupted, but there is an interrupt pending for once deferral is ended.  Possible transitions:
     * <ul>
     *     <li>{@link #STATE_INTERRUPT_IN_PROGRESS}</li>
     * </ul>
     */
    private static final int STATE_INTERRUPT_PENDING = 2;
    /**
     * The thread is in the process of executing interruption logic.  If the thread attempts to defer interrupts
     * during this phase, it will block until the interruption logic is complete.
     */
    private static final int STATE_INTERRUPT_IN_PROGRESS = 3;

    private final AtomicInteger stateRef = new AtomicInteger();

    /**
     * Construct a new instance.
     *
     * @param target the runnable target
     * @see Thread#Thread(Runnable)
     */
    public JBossThread(final Runnable target) {
        super(target);
    }

    /**
     * Construct a new instance.
     *
     * @param target the runnable target
     * @param name the initial thread name
     * @see Thread#Thread(Runnable, String)
     */
    public JBossThread(final Runnable target, final String name) {
        super(target, name);
    }

    /**
     * Construct a new instance.
     *
     * @param group the parent thread group
     * @param target the runnable target
     * @see Thread#Thread(ThreadGroup, Runnable)
     * @throws SecurityException if the current thread cannot create a thread in the specified thread group
     */
    public JBossThread(final ThreadGroup group, final Runnable target) throws SecurityException {
        super(group, target);
    }

    /**
     * Construct a new instance.
     *
     * @param group the parent thread group
     * @param target the runnable target
     * @param name the initial thread name
     * @see Thread#Thread(ThreadGroup,Runnable,String)
     * @throws SecurityException if the current thread cannot create a thread in the specified thread group
     */
    public JBossThread(final ThreadGroup group, final Runnable target, final String name) throws SecurityException {
        super(group, target, name);
    }

    /**
     * Construct a new instance.
     *
     * @param group the parent thread group
     * @param target the runnable target
     * @param name the initial thread name
     * @see Thread#Thread(ThreadGroup,Runnable,String,long)
     * @throws SecurityException if the current thread cannot create a thread in the specified thread group
     */
    public JBossThread(final ThreadGroup group, final Runnable target, final String name, final long stackSize) throws SecurityException {
        super(group, target, name, stackSize);
    }

    /**
     * Interrupt this thread.  Logs a trace message and calls the current interrupt handler, if any.  The interrupt
     * handler is called from the <em>calling</em> thread, not the thread being interrupted.
     */
    public void interrupt() {
        final boolean differentThread = Thread.currentThread() != this;
        if (differentThread) checkAccess();
        // fast check
        if (isInterrupted()) return;
        final AtomicInteger stateRef = this.stateRef;
        int oldVal, newVal;
        int spins = 0;
        for (;;) {
            oldVal = stateRef.get();
            if (oldVal == STATE_INTERRUPT_PENDING) {
                // already set
                Messages.msg.tracef("Interrupting thread \"%s\" (already interrupted)", this);
                return;
            } else if (oldVal == STATE_INTERRUPT_IN_PROGRESS) {
                // wait for interruption on other thread to be completed
                if (spins < MAX_INTERRUPT_SPINS) {
                    //JDKSpecific.onSpinWait();
                    spins++;
                } else {
                    Thread.yield();
                }
                continue;
            } else {
                if (oldVal == STATE_INTERRUPT_DEFERRED) {
                    newVal = STATE_INTERRUPT_PENDING;
                } else {
                    newVal = STATE_INTERRUPT_IN_PROGRESS;
                }
            }
            if (stateRef.compareAndSet(oldVal, newVal)) {
                break;
            }
        }
        if (newVal == STATE_INTERRUPT_IN_PROGRESS) try {
            doInterrupt();
        } finally {
            // after we return, the thread could be un-interrupted at any time without our knowledge
            stateRef.set(STATE_MAYBE_INTERRUPTED);
            if (differentThread) {
                // unpark the thread if it was waiting to defer interrupts
                // interrupting the thread will unpark it; it might park after the interrupt though, or wake up before the state is restored
                LockSupport.unpark(this);
            }
        } else {
            Messages.intMsg.tracef("Interrupting thread \"%s\" (deferred)", this);
        }
    }

    private void doInterrupt() {
        if (isInterrupted()) return;
        Messages.msg.tracef("Interrupting thread \"%s\"", this);
        try {
            super.interrupt();
        } finally {
            final InterruptHandler interruptHandler = this.interruptHandler;
            if (interruptHandler != null) {
                try {
                    interruptHandler.handleInterrupt(this);
                } catch (Throwable t) {
                    Messages.msg.interruptHandlerThrew(t, interruptHandler);
                }
            }
        }
    }

    public boolean isInterrupted() {
        return this == Thread.currentThread() ? super.isInterrupted() : super.isInterrupted() || (stateRef.get() == STATE_INTERRUPT_PENDING);
    }

    /**
     * Defer interrupts for the duration of some task.  Once the task is complete, any deferred interrupt will be
     * delivered to the thread, thus the thread interrupt status should be checked upon return.  If the current thread
     * is not a {@code JBossThread}, the task is simply run as-is.
     *
     * @param task the task to run
     */
    public static void executeWithInterruptDeferred(final Runnable task) {
        executeWithInterruptDeferred(JBossExecutors.directExecutor(), task);
    }

    /**
     * Defer interrupts for the duration of some task.  Once the task is complete, any deferred interrupt will be
     * delivered to the thread, thus the thread interrupt status should be checked upon return.  If the current thread
     * is not a {@code JBossThread}, the task is simply run as-is.
     *
     * @param directExecutor the task executor to use
     * @param task the task to run
     */
    public static void executeWithInterruptDeferred(final DirectExecutor directExecutor, final Runnable task) {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            directExecutor.execute(task);
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            directExecutor.execute(task);
        }
    }

    /**
     * Defer interrupts for the duration of some task.  Once the task is complete, any deferred interrupt will be
     * delivered to the thread, thus the thread interrupt status should be checked upon return.  If the current thread
     * is not a {@code JBossThread}, the task is simply run as-is.
     *
     * @param action the task to run
     * @param <T> the callable's return type
     * @return the value returned from the callable
     * @throws Exception if the action throws an exception
     */
    public static <T> T executeWithInterruptDeferred(final Callable<T> action) throws Exception {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            return action.call();
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            return action.call();
        }
    }

    /**
     * Defer interrupts for the duration of some task.  Once the task is complete, any deferred interrupt will be
     * delivered to the thread, thus the thread interrupt status should be checked upon return.  If the current thread
     * is not a {@code JBossThread}, the task is simply run as-is.
     *
     * @param action the task to run
     * @param <T> the action's return type
     * @return the value returned from the callable
     */
    public static <T> T executeWithInterruptDeferred(final PrivilegedAction<T> action) {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            return action.run();
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            return action.run();
        }
    }

    /**
     * Defer interrupts for the duration of some task.  Once the task is complete, any deferred interrupt will be
     * delivered to the thread, thus the thread interrupt status should be checked upon return.  If the current thread
     * is not a {@code JBossThread}, the task is simply run as-is.
     *
     * @param action the task to run
     * @param <T> the action's return type
     * @return the value returned from the callable
     * @throws Exception if the action throws an exception
     */
    public static <T> T executeWithInterruptDeferred(final PrivilegedExceptionAction<T> action) throws Exception {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            return action.run();
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            return action.run();
        }
    }

    public static <T, U, R, E extends Exception> R applyInterruptDeferredEx(final ExceptionBiFunction<T, U, R, E> function, T param1, U param2) throws E {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            return function.apply(param1, param2);
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            return function.apply(param1, param2);
        }
    }

    public static <T, R, E extends Exception> R applyInterruptDeferredEx(final ExceptionFunction<T, R, E> function, T param) throws E {
        return applyInterruptDeferredEx(Functions.exceptionFunctionBiFunction(), function, param);
    }

    public static <T, E extends Exception> T getInterruptDeferredEx(final ExceptionSupplier<T, E> supplier) throws E {
        return applyInterruptDeferredEx(Functions.exceptionFunctionBiFunction(), Functions.exceptionSupplierFunction(), supplier);
    }

    public static <T, E extends Exception> void acceptInterruptDeferredEx(final ExceptionObjLongConsumer<T, E> consumer, T param1, long param2) throws E {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            consumer.accept(param1, param2);
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            consumer.accept(param1, param2);
        }
    }

    public static <T, E extends Exception> void acceptInterruptDeferredEx(final ExceptionObjIntConsumer<T, E> consumer, T param1, int param2) throws E {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            consumer.accept(param1, param2);
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            consumer.accept(param1, param2);
        }
    }

    public static <T, U, E extends Exception> void acceptInterruptDeferredEx(final ExceptionBiConsumer<T, U, E> consumer, T param1, U param2) throws E {
        final JBossThread thread = currentThread();
        if (registerDeferral(thread)) try {
            consumer.accept(param1, param2);
        } finally {
            unregisterDeferral(thread);
        } else {
            // already deferred
            consumer.accept(param1, param2);
        }
    }

    public static <T, E extends Exception> void acceptInterruptDeferredEx(final ExceptionConsumer<T, E> consumer, T param) throws E {
        acceptInterruptDeferredEx(Functions.exceptionConsumerBiConsumer(), consumer, param);
    }

    public static <E extends Exception> void runInterruptDeferredEx(final ExceptionRunnable<E> runnable) throws E {
        acceptInterruptDeferredEx(Functions.exceptionConsumerBiConsumer(), Functions.exceptionRunnableConsumer(), runnable);
    }


    public static <T, U, R, E extends Exception> R applyInterruptResumedEx(final ExceptionBiFunction<T, U, R, E> function, T param1, U param2) throws E {
        final JBossThread thread = currentThread();
        if (unregisterDeferral(thread)) try {
            return function.apply(param1, param2);
        } finally {
            registerDeferral(thread);
        } else {
            // already resumed
            return function.apply(param1, param2);
        }
    }

    public static <T, R, E extends Exception> R applyInterruptResumedEx(final ExceptionFunction<T, R, E> function, T param) throws E {
        return applyInterruptResumedEx(Functions.exceptionFunctionBiFunction(), function, param);
    }

    public static <T, E extends Exception> T getInterruptResumedEx(final ExceptionSupplier<T, E> supplier) throws E {
        return applyInterruptResumedEx(Functions.exceptionFunctionBiFunction(), Functions.exceptionSupplierFunction(), supplier);
    }

    public static <T, E extends Exception> void acceptInterruptResumedEx(final ExceptionObjLongConsumer<T, E> consumer, T param1, long param2) throws E {
        final JBossThread thread = currentThread();
        if (unregisterDeferral(thread)) try {
            consumer.accept(param1, param2);
        } finally {
            registerDeferral(thread);
        } else {
            // already resumed
            consumer.accept(param1, param2);
        }
    }

    public static <T, E extends Exception> void acceptInterruptResumedEx(final ExceptionObjIntConsumer<T, E> consumer, T param1, int param2) throws E {
        final JBossThread thread = currentThread();
        if (unregisterDeferral(thread)) try {
            consumer.accept(param1, param2);
        } finally {
            registerDeferral(thread);
        } else {
            // already resumed
            consumer.accept(param1, param2);
        }
    }

    public static <T, U, E extends Exception> void acceptInterruptResumedEx(final ExceptionBiConsumer<T, U, E> consumer, T param1, U param2) throws E {
        final JBossThread thread = currentThread();
        if (unregisterDeferral(thread)) try {
            consumer.accept(param1, param2);
        } finally {
            registerDeferral(thread);
        } else {
            // already resumed
            consumer.accept(param1, param2);
        }
    }

    public static <T, E extends Exception> void acceptInterruptResumedEx(final ExceptionConsumer<T, E> consumer, T param) throws E {
        acceptInterruptResumedEx(Functions.exceptionConsumerBiConsumer(), consumer, param);
    }

    public static <E extends Exception> void runInterruptResumedEx(final ExceptionRunnable<E> runnable) throws E {
        acceptInterruptResumedEx(Functions.exceptionConsumerBiConsumer(), Functions.exceptionRunnableConsumer(), runnable);
    }

    private static boolean unregisterDeferral(final JBossThread thread) {
        if (thread == null) {
            return false;
        }
        int oldVal, newVal;
        final AtomicInteger stateRef = thread.stateRef;
        do {
            oldVal = stateRef.get();
            if (oldVal == STATE_MAYBE_INTERRUPTED || oldVal == STATE_INTERRUPT_IN_PROGRESS) {
                // already not deferred
                return false;
            } else if (oldVal == STATE_INTERRUPT_DEFERRED) {
                newVal = STATE_MAYBE_INTERRUPTED;
            } else if (oldVal == STATE_INTERRUPT_PENDING) {
                newVal = STATE_INTERRUPT_IN_PROGRESS;
            } else {
                throw Assert.unreachableCode();
            }
        } while (! stateRef.compareAndSet(oldVal, newVal));
        if (newVal == STATE_INTERRUPT_IN_PROGRESS) try {
            thread.doInterrupt();
        } finally {
            stateRef.set(STATE_MAYBE_INTERRUPTED);
        }
        return true;
    }

    private static boolean registerDeferral(final JBossThread thread) {
        if (thread == null) {
            return false;
        }
        final AtomicInteger stateRef = thread.stateRef;
        int oldVal, newVal;
        do {
            oldVal = stateRef.get();
            while (oldVal == STATE_INTERRUPT_IN_PROGRESS) {
                LockSupport.park();
                oldVal = stateRef.get();
            }
            if (oldVal == STATE_MAYBE_INTERRUPTED) {
                newVal = Thread.interrupted() ? STATE_INTERRUPT_DEFERRED : STATE_INTERRUPT_PENDING;
            } else if (oldVal == STATE_INTERRUPT_DEFERRED || oldVal == STATE_INTERRUPT_PENDING) {
                // already deferred
                return false;
            } else {
                throw Assert.unreachableCode();
            }
        } while (! stateRef.compareAndSet(oldVal, newVal));
        if (newVal == STATE_INTERRUPT_DEFERRED && Thread.interrupted()) {
            // in case we got interrupted right after we checked interrupt state but before we CAS'd the value.
            stateRef.set(STATE_INTERRUPT_PENDING);
        }
        return true;
    }

    /**
     * Execute the thread's {@code Runnable}.  Logs a trace message at the start and end of execution.
     */
    public void run() {
        Messages.msg.tracef("Thread \"%s\" starting execution", this);
        try {
            super.run();
        } finally {
            Messages.msg.tracef("Thread \"%s\" exiting", this);
        }
    }

    /**
     * Get the current {@code JBossThread}, or {@code null} if the current thread is not a {@code JBossThread}.
     *
     * @return the current thread, or {@code null}
     */
    public static JBossThread currentThread() {
        final Thread thread = Thread.currentThread();
        return thread instanceof JBossThread ? (JBossThread) thread : null;
    }

    /**
     * Start the thread.
     *
     * @throws IllegalThreadStateException if the thread was already started.
     */
    public void start() {
        super.start();
        Messages.msg.tracef("Started thread \"%s\"", this);
    }

    /**
     * Change the uncaught exception handler for this thread.
     *
     * @param eh the new handler
     */
    public void setUncaughtExceptionHandler(final UncaughtExceptionHandler eh) {
        super.setUncaughtExceptionHandler(eh);
        Messages.msg.tracef("Changed uncaught exception handler for \"%s\" to %s", this, eh);
    }

    /**
     * Swap the current thread's active interrupt handler.  Most callers should restore the old handler in a {@code finally}
     * block like this:
     * <pre>
     * InterruptHandler oldHandler = JBossThread.getAndSetInterruptHandler(newHandler);
     * try {
     *     ...execute interrupt-sensitive operation...
     * } finally {
     *     JBossThread.getAndSetInterruptHandler(oldHandler);
     * }
     * </pre>
     *
     * @param newInterruptHandler the new interrupt handler
     * @return the old interrupt handler
     */
    public static InterruptHandler getAndSetInterruptHandler(final InterruptHandler newInterruptHandler) {
        final JBossThread thread = currentThread();
        if (thread == null) {
            throw Messages.msg.noInterruptHandlers();
        }
        try {
            return thread.interruptHandler;
        } finally {
            thread.interruptHandler = newInterruptHandler;
        }
    }

    public static <T, U, R, E extends Exception> R applyWithInterruptHandler(InterruptHandler interruptHandler, ExceptionBiFunction<T, U, R, E> function, T param1, U param2) throws E {
        final JBossThread thread = currentThread();
        if (thread == null) {
            return function.apply(param1, param2);
        } else {
            final InterruptHandler old = thread.interruptHandler;
            thread.interruptHandler = interruptHandler;
            try {
                return function.apply(param1, param2);
            } finally {
                thread.interruptHandler = old;
            }
        }
    }

    public static <T, R, E extends Exception> R applyWithInterruptHandler(InterruptHandler interruptHandler, ExceptionFunction<T, R, E> function, T param1) throws E {
        return applyWithInterruptHandler(interruptHandler, Functions.exceptionFunctionBiFunction(), function, param1);
    }

    public static <R, E extends Exception> R getWithInterruptHandler(InterruptHandler interruptHandler, ExceptionSupplier<R, E> function) throws E {
        return applyWithInterruptHandler(interruptHandler, Functions.exceptionFunctionBiFunction(), Functions.exceptionSupplierFunction(), function);
    }

    public static <T, E extends Exception> void acceptWithInterruptHandler(InterruptHandler interruptHandler, ExceptionObjLongConsumer<T, E> function, T param1, long param2) throws E {
        final JBossThread thread = currentThread();
        if (thread == null) {
            function.accept(param1, param2);
            return;
        } else {
            final InterruptHandler old = thread.interruptHandler;
            thread.interruptHandler = interruptHandler;
            try {
                function.accept(param1, param2);
                return;
            } finally {
                thread.interruptHandler = old;
            }
        }
    }

    public static <T, E extends Exception> void acceptWithInterruptHandler(InterruptHandler interruptHandler, ExceptionObjIntConsumer<T, E> function, T param1, int param2) throws E {
        final JBossThread thread = currentThread();
        if (thread == null) {
            function.accept(param1, param2);
            return;
        } else {
            final InterruptHandler old = thread.interruptHandler;
            thread.interruptHandler = interruptHandler;
            try {
                function.accept(param1, param2);
                return;
            } finally {
                thread.interruptHandler = old;
            }
        }
    }

    public static <T, U, E extends Exception> void acceptWithInterruptHandler(InterruptHandler interruptHandler, ExceptionBiConsumer<T, U, E> function, T param1, U param2) throws E {
        final JBossThread thread = currentThread();
        if (thread == null) {
            function.accept(param1, param2);
            return;
        } else {
            final InterruptHandler old = thread.interruptHandler;
            thread.interruptHandler = interruptHandler;
            try {
                function.accept(param1, param2);
                return;
            } finally {
                thread.interruptHandler = old;
            }
        }
    }

    public static <T, E extends Exception> void acceptWithInterruptHandler(InterruptHandler interruptHandler, ExceptionConsumer<T, E> function, T param1) throws E {
        acceptWithInterruptHandler(interruptHandler, Functions.exceptionConsumerBiConsumer(), function, param1);
    }

    public static <E extends Exception> void runWithInterruptHandler(InterruptHandler interruptHandler, ExceptionRunnable<E> function) throws E {
        acceptWithInterruptHandler(interruptHandler, Functions.exceptionConsumerBiConsumer(), Functions.exceptionRunnableConsumer(), function);
    }

    /**
     * Get the thread name information.  This includes information about the thread's sequence number and so forth.
     *
     * @return the thread name info
     */
    ThreadNameInfo getThreadNameInfo() {
        return threadNameInfo;
    }

    /**
     * Set the thread name information.  This includes information about the thread's sequence number and so forth.
     *
     * @param threadNameInfo the new thread name info
     * @throws SecurityException if the calling thread is not allowed to modify this thread
     */
    void setThreadNameInfo(final ThreadNameInfo threadNameInfo) throws SecurityException {
        checkAccess();
        this.threadNameInfo = threadNameInfo;
    }
}
