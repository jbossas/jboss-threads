package org.jboss.threads.virtual;

/**
 * The virtual thread scheduling mode.
 */
enum Mode {
    /**
     * Schedule on to the I/O thread.
     */
    IO,
    /**
     * Schedule on to the worker thread.
     */
    WORKER,
    ;
}
