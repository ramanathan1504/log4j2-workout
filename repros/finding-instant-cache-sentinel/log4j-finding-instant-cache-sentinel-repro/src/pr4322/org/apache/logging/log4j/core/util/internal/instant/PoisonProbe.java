package org.apache.logging.log4j.core.util.internal.instant;

import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;

public final class PoisonProbe {

    public static void main(final String[] args) {
        final InstantPatternFormatter failing = new InstantPatternFormatter() {
            public String getPattern() { return "probe"; }
            public Locale getLocale() { return Locale.US; }
            public TimeZone getTimeZone() { return TimeZone.getTimeZone("UTC"); }
            public ChronoUnit getPrecision() { return ChronoUnit.MILLIS; }
            public void formatTo(final StringBuilder buffer, final Instant instant) {
                buffer.append("half-");
                if (instant.getEpochMillisecond() == 7) {
                    throw new IllegalStateException("failed on purpose");
                }
                buffer.append(instant.getEpochMillisecond());
            }
        };
        final InstantFormatter cached = InstantPatternThreadLocalCachedFormatter.ofMilliPrecision(failing);
        System.out.println("first -1 ms: [" + cached.format(at(-1)) + "]");
        System.out.println("5 ms: [" + cached.format(at(5)) + "]");
        try {
            cached.format(at(7));
        } catch (final IllegalStateException expected) {
            System.out.println("7 ms: threw");
        }
        System.out.println("-1 ms after the failure: [" + cached.format(at(-1)) + "]");
    }

    private static MutableInstant at(final long epochMilli) {
        final MutableInstant instant = new MutableInstant();
        instant.initFromEpochMilli(epochMilli, 0);
        return instant;
    }
}
