package org.jboss.threads;

/**
 * A handler for propagating context from a task submitter to a task execution.
 *
 * @param <T> the class of the context to capture
 */
public interface ContextHandler<T> {
    /**
     * The context handler which captures no context.
     */
    ContextHandler<?> NONE = new ContextHandler<Object>() {
        public Object captureContext() {
            return null;
        }

        public void runWith(final Runnable task, final Object context) {
            task.run();
        }
    };

    /**
     * Capture the current context from the submitting thread.
     *
     * @return the captured context
     */
    T captureContext();

    /**
     * Run the given task with the given captured context.  The context should be cleared
     * when this method returns.
     *
     * @param task the task to run (not {@code null})
     * @param context the context returned from {@link #captureContext()}
     */
    void runWith(Runnable task, T context);
}
