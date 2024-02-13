package org.jboss.threads;

import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

/**
 */
final class JDKSpecific {

    static TemporalUnit timeToTemporal(final TimeUnit timeUnit) {
        return timeUnit.toChronoUnit();
    }

    static void onSpinWait() {
        Thread.onSpinWait();
    }
}
