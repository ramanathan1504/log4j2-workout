import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

public final class Repro {

    private static final String M1 = "1969-12-31T23:59:59.999Z";
    private static final String M2 = "1969-12-31T23:59:59.998Z";
    private static final String ONE = "1970-01-01T00:00:00.001Z";

    private static Configuration config;

    public static void main(final String[] args) throws Exception {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        config = ctx.getConfiguration();

        check("SENTINEL", "Rfc5424Layout, events -1,-1,1,-1 ms on one layout", M1 + "," + M1 + "," + ONE + "," + M1,
                -1, -1, 1, -1);
        check("FRESH", "Rfc5424Layout, first event -1 ms on a second layout instance", M1, -1);
        check("OTHER_PRE_EPOCH", "Rfc5424Layout, first event -2 ms (pre-epoch, not the sentinel)", M2, -2);
        check("WARM", "Rfc5424Layout, 1000 ms then -1 ms (cache populated first)",
                "1970-01-01T00:00:01.000Z," + M1, 1000, -1);
        check("POS", "control: Rfc5424Layout, 1000,2000 ms",
                "1970-01-01T00:00:01.000Z,1970-01-01T00:00:02.000Z", 1000, 2000);
        final long y999 = ZonedDateTime.of(999, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        check("YEAR", "sweep: Rfc5424Layout, year 999 (DATE-FULLYEAR = 4DIGIT)",
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX").format(
                        ZonedDateTime.of(999, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC)), y999);

        final String label = "custom clock returning -1,-1,1,-1 ms, Logger.info through File appender with Rfc5424Layout";
        final Class<?> clockCase;
        try {
            clockCase = Class.forName("ClockCase");
        } catch (final ClassNotFoundException absent) {
            System.out.println("SKIP | " + label + " | no custom Clock property on this line");
            return;
        }
        final String observed = (String) clockCase.getMethod("run", String.class).invoke(null, System.getProperty("logdir"));
        report(label, M1 + "," + M1 + "," + ONE + "," + M1, observed);
    }

    private static void check(final String appender, final String label, final String expected, final long... millis) {
        final StringBuilder observed = new StringBuilder();
        for (final long ms : millis) {
            final LogEvent event = Log4jLogEvent.newBuilder()
                    .setLoggerName("repro")
                    .setLevel(Level.INFO)
                    .setMessage(new SimpleMessage("m"))
                    .setTimeMillis(ms)
                    .build();
            final String line = new String(config.getAppender(appender).getLayout().toByteArray(event), StandardCharsets.UTF_8);
            final String[] fields = line.split(" ");
            if (observed.length() > 0) {
                observed.append(',');
            }
            observed.append(fields.length > 1 ? fields[1] : line);
        }
        report(appender + ": " + label, expected, observed.toString());
    }

    private static void report(final String label, final String expected, final String observed) {
        System.out.println((expected.equals(observed) ? "PASS" : "FAIL") + " | " + label + " | expected="
                + expected + " | observed=" + observed);
    }
}
