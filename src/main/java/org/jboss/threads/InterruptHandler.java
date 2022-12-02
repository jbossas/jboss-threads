package org.jboss.threads;

/**
 * A thread interrupt handler.  Called when a thread's {@code interrupt()} method is invoked.  The handler should
 * not throw an exception; otherwise user code might end up in an unexpected state.
 */
public interface InterruptHandler {

    /**
     * Handle an interrupt condition on the given thread.  This method should not throw an exception.
     *
     * @param thread the thread which was interrupted
     */
    void handleInterrupt(Thread thread);
}
