package org.jboss.threads;

/**
 * An interface which supports asynchronous cancellation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface AsyncCancellable {

    /**
     * Handle an asynchronous cancellation.  Does not block, and may be called more than once;
     * in particular, this method may be re-invoked to set the {@code interruptionDesired} flag
     * even if it has already been called without that flag set before.  Otherwise, this method is
     * idempotent, and thus may be called more than once without additional effect.
     *
     * @param interruptionDesired {@code true} if interruption of threads is desired
     */
    void asyncCancel(boolean interruptionDesired);
}
