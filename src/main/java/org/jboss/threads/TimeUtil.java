package org.jboss.threads;

import static java.lang.Math.max;

import java.time.Duration;

/**
 */
final class TimeUtil {
    private TimeUtil() {}

    private static final long LARGEST_SECONDS = 9_223_372_035L; // Long.MAX_VALUE / 1_000_000_000L - 1

    static long clampedPositiveNanos(Duration duration) {
        final long seconds = max(0L, duration.getSeconds());
        return seconds > LARGEST_SECONDS ? Long.MAX_VALUE : max(1, seconds * 1_000_000_000L + duration.getNano());
    }
}
