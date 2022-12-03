package org.jboss.threads;

/**
 */
final class Waiter {
    private volatile Thread thread;
    private Waiter next;

    Waiter(final Waiter next) {
        this.next = next;
    }

    Thread getThread() {
        return thread;
    }

    Waiter setThread(final Thread thread) {
        this.thread = thread;
        return this;
    }

    Waiter getNext() {
        return next;
    }

    Waiter setNext(final Waiter next) {
        this.next = next;
        return this;
    }
}
