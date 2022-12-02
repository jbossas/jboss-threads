package org.jboss.threads;

import org.jboss.logging.Logger;

class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger log;

    LoggingUncaughtExceptionHandler(final Logger log) {
        this.log = log;
    }

    public void uncaughtException(final Thread thread, final Throwable throwable) {
        log.errorf(throwable, "Thread %s threw an uncaught exception", thread);
    }

    public String toString() {
        return String.format("%s to \"%s\"", super.toString(), log.getName());
    }
}
