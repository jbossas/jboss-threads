/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
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
