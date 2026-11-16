import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;

public class Repro {
    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    public static void main(final String[] args) {
        final PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%d{" + PATTERN + "}{UTC}")
                .build();
        final DateTimeFormatter reference = DateTimeFormatter.ofPattern(PATTERN).withZone(ZoneOffset.UTC);
        final long[] epochMillis = {-1L, -1500L, -61_001L, -86_400_000L + 250L, 1_500L};
        int failures = 0;
        for (final long millis : epochMillis) {
            final LogEvent event = Log4jLogEvent.newBuilder()
                    .setTimeMillis(millis)
                    .setMessage(new SimpleMessage("m"))
                    .build();
            final String actual = new String(layout.toByteArray(event), StandardCharsets.UTF_8);
            final String expected = reference.format(Instant.ofEpochMilli(millis));
            final boolean pass = expected.equals(actual);
            final String label = millis < 0 ? "before-epoch" : "control-after-epoch";
            System.out.printf("%s %s epochMillis=%d expected=%s observed=%s%n",
                    pass ? "PASS" : "FAIL", label, millis, expected, actual);
            if (!pass) {
                failures++;
            }
        }
        System.out.println("failures=" + failures);
    }
}
