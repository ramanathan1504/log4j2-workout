import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.TimeZone;
import org.apache.logging.log4j.core.util.CronExpression;

public class Repro {

    private static int failures;

    public static void main(final String[] args) throws Exception {
        final Instant sydApr5 = ZonedDateTime.of(2025, 4, 4, 13, 0, 0, 0, ZoneOffset.UTC).toInstant();
        final Instant sydApr6 = sydApr5.plus(24, ChronoUnit.HOURS);
        final Instant sydApr7 = sydApr6.plus(25, ChronoUnit.HOURS);
        final CronExpression sydney = daily("Australia/Sydney");
        check("sydney-fallback prev(apr6-1ms)", sydney.getPrevFireTime(Date.from(sydApr6.minusMillis(1))), sydApr5);
        check("sydney-fallback next(apr6-1ms)", sydney.getNextValidTimeAfter(Date.from(sydApr6.minusMillis(1))), sydApr6);
        check("sydney-fallback prev(apr6+1ms)", sydney.getPrevFireTime(Date.from(sydApr6.plusMillis(1))), sydApr6);
        check("sydney-fallback next(apr6+1ms)", sydney.getNextValidTimeAfter(Date.from(sydApr6.plusMillis(1))), sydApr7);

        final Instant sclApr5 = ZonedDateTime.of(2025, 4, 5, 3, 0, 0, 0, ZoneOffset.UTC).toInstant();
        final Instant sclApr6 = sclApr5.plus(25, ChronoUnit.HOURS);
        final Instant sclApr7 = sclApr6.plus(24, ChronoUnit.HOURS);
        final CronExpression santiago = daily("America/Santiago");
        check("santiago-fallback prev(apr6-1ms)", santiago.getPrevFireTime(Date.from(sclApr6.minusMillis(1))), sclApr5);
        check("santiago-fallback prev(apr6+1ms)", santiago.getPrevFireTime(Date.from(sclApr6.plusMillis(1))), sclApr6);
        check("santiago-fallback next(apr6+1ms)", santiago.getNextValidTimeAfter(Date.from(sclApr6.plusMillis(1))), sclApr7);

        final Instant oct5 = ZonedDateTime.of(2025, 10, 4, 14, 0, 0, 0, ZoneOffset.UTC).toInstant();
        final Instant oct6 = oct5.plus(23, ChronoUnit.HOURS);
        final Instant oct7 = oct6.plus(24, ChronoUnit.HOURS);
        check("sydney-springforward prev(oct6-1ms)", sydney.getPrevFireTime(Date.from(oct6.minusMillis(1))), oct5);
        check("sydney-springforward prev(oct6+1ms)", sydney.getPrevFireTime(Date.from(oct6.plusMillis(1))), oct6);
        check("sydney-springforward next(oct6+1ms)", sydney.getNextValidTimeAfter(Date.from(oct6.plusMillis(1))), oct7);

        final CronExpression utc = daily("UTC");
        final Instant utcMidnight = Instant.parse("2025-06-02T00:00:00Z");
        check("no-dst-utc prev(midnight+201ms)", utc.getPrevFireTime(Date.from(utcMidnight.plusMillis(201))), utcMidnight);
        System.out.println("SUMMARY failures=" + failures);
    }

    private static CronExpression daily(final String zone) throws Exception {
        final CronExpression expression = new CronExpression("0 0 0 * * ?");
        expression.setTimeZone(TimeZone.getTimeZone(ZoneId.of(zone)));
        return expression;
    }

    private static void check(final String name, final Date actual, final Instant expected) {
        final Instant observed = actual == null ? null : actual.toInstant();
        final boolean ok = expected.equals(observed);
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "PASS " : "FAIL ") + name + " expected=" + expected + " observed=" + observed);
    }
}
