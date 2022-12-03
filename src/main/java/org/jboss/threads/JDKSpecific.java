package org.jboss.threads;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

import org.wildfly.common.Assert;

/**
 */
final class JDKSpecific {

    static TemporalUnit timeToTemporal(final TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS: return ChronoUnit.NANOS;
            case MICROSECONDS: return ChronoUnit.MICROS;
            case MILLISECONDS: return ChronoUnit.MILLIS;
            case SECONDS: return ChronoUnit.SECONDS;
            case MINUTES: return ChronoUnit.MINUTES;
            case HOURS: return ChronoUnit.HOURS;
            case DAYS: return ChronoUnit.DAYS;
            default: throw Assert.impossibleSwitchCase(timeUnit);
        }
    }

    static void onSpinWait() {
        // no operation
    }
}
