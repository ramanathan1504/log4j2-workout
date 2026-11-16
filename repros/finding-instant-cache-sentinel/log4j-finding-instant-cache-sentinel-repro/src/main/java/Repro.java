import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.AbstractLogEvent;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;

public final class Repro {

    private static Configuration config;

    public static void main(final String[] args) throws Exception {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        config = ctx.getConfiguration();

        check("MS", "%d{HH:mm:ss.SSS}{UTC}, events -1,-1,0,-1 ms on a fresh thread",
                millis(-1, -1, 0, -1), "23:59:59.999,23:59:59.999,00:00:00.000,23:59:59.999");
        check("MS", "%d{HH:mm:ss.SSS}{UTC}, same layout, -1 ms on a second fresh thread",
                millis(-1), "23:59:59.999");
        check("MS_POS", "control: %d{HH:mm:ss.SSS}{UTC}, events 1000,2000 ms",
                millis(1000, 2000), "00:00:01.000,00:00:02.000");
        check("MS_WARM", "%d{HH:mm:ss.SSS}{UTC}, events 1000,-1000 ms (cache already populated)",
                millis(1000, -1000), "00:00:01.000,23:59:59.000");
        check("DEF", "%d{DEFAULT}{UTC}, -1 ms", millis(-1), "1969-12-31 23:59:59,999");
        check("ISO", "%d{ISO8601}{UTC}, -1 ms", millis(-1), "1969-12-31T23:59:59,999");
        check("SEC", "%d{HH:mm:ss}{UTC}, events -1000,-1000,0,-1000 ms",
                millis(-1000, -1000, 0, -1000), "23:59:59,23:59:59,00:00:00,23:59:59");
        check("SEC_SUB", "%d{HH:mm:ss}{UTC}, -500 ms", millis(-500), "23:59:59");
        check("SEC_INSTANT", "%d{HH:mm:ss}{UTC}, setInstant(epochSecond=-1)", instant(-1, 0), "23:59:59");
        check("MIN", "%d{yyyy-MM-dd HH:mm}{UTC}, -1000 ms", millis(-1000), "1969-12-31 23:59");
        check("HOUR", "%d{yyyy-MM-dd HH}{UTC}, -1000 ms", millis(-1000), "1969-12-31 23");
        check("MICRO", "%d{HH:mm:ss.SSSSSS}{UTC} (not cached), -1000 ms",
                millis(-1000), "23:59:59.000000");
        check("UNIX", "control: %d{UNIX_MILLIS} (not cached), -1 ms", millis(-1), "-1");
        check("ZERO_FIXED", "sweep: %d{HH:mm:ss.SSS}{UTC}, events 0,5 ms", millis(0, 5), "00:00:00.000,00:00:00.005");
        check("ZERO_FREE", "sweep: %d{EEE HH:mm:ss.SSS}{UTC}, events 5000,0,0 ms",
                millis(5000, 0, 0), "Thu 00:00:05.000,Thu 00:00:00.000,Thu 00:00:00.000");
        check("JTL", "JsonTemplateLayout timestamp HH:mm:ss.SSS UTC, -1 ms",
                millis(-1), "{\"t\":\"23:59:59.999\"}");

        final Appender rfc = config.getAppender("RFC5424");
        final String rfcOut = new String(rfc.getLayout().toByteArray(base().setTimeMillis(-1).build()), StandardCharsets.UTF_8);
        final String[] rfcFields = rfcOut.split(" ");
        report("sweep: Rfc5424Layout (instance field lastTimestamp = -1), timestamp field of first event at -1 ms",
                "1969-12-31T23:59:59.999Z", rfcFields.length > 1 ? rfcFields[1] : rfcOut);

        final String logdir = System.getProperty("logdir");
        final String clockLabel =
                "custom clock returning -1,-1,0,-1 ms, Logger.info through File appender %d{HH:mm:ss.SSS}{UTC}|%m";
        final Class<?> clockCase;
        try {
            clockCase = Class.forName("ClockCase");
        } catch (final ClassNotFoundException absent) {
            System.out.println("SKIP | " + clockLabel + " | no custom Clock property on this line");
            return;
        }
        final String clockObserved = (String) clockCase.getMethod("run", String.class).invoke(null, logdir);
        report(clockLabel,
                "23:59:59.999|a,23:59:59.999|b,00:00:00.000|c,23:59:59.999|d", clockObserved);
    }

    private static LogEvent[] millis(final long... values) {
        final LogEvent[] events = new LogEvent[values.length];
        for (int i = 0; i < values.length; i++) {
            events[i] = values[i] == 0L ? new EpochZeroEvent() : base().setTimeMillis(values[i]).build();
        }
        return events;
    }

    private static LogEvent[] instant(final long epochSecond, final int nanoOfSecond) {
        final MutableInstant instant = new MutableInstant();
        instant.initFromEpochSecond(epochSecond, nanoOfSecond);
        return new LogEvent[] {base().setInstant(instant).build()};
    }

    private static Log4jLogEvent.Builder base() {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("repro")
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage("m"));
    }

    private static void check(final String appender, final String label, final LogEvent[] events, final String expected)
            throws InterruptedException {
        final Appender app = config.getAppender(appender);
        final List<String> out = new ArrayList<>();
        final Thread t = new Thread(() -> {
            for (final LogEvent event : events) {
                final String s = new String(app.getLayout().toByteArray(event), StandardCharsets.UTF_8);
                out.add(s.replace("\r", "").replace("\n", ""));
            }
        });
        t.start();
        t.join();
        final StringBuilder observed = new StringBuilder();
        for (final String s : out) {
            if (observed.length() > 0) {
                observed.append(',');
            }
            observed.append(s.isEmpty() ? "<empty>" : s);
        }
        report(appender + ": " + label, expected, observed.toString());
    }

    private static void report(final String label, final String expected, final String observed) {
        System.out.println((expected.equals(observed) ? "PASS" : "FAIL") + " | " + label + " | expected="
                + expected + " | observed=" + observed);
    }

    private static final class EpochZeroEvent extends AbstractLogEvent {

        private static final long serialVersionUID = 1L;

        private final MutableInstant instant = new MutableInstant();

        @Override
        public Instant getInstant() {
            return instant;
        }

        @Override
        public long getTimeMillis() {
            return 0L;
        }

        @Override
        public Level getLevel() {
            return Level.INFO;
        }

        @Override
        public String getLoggerName() {
            return "repro";
        }

        @Override
        public Message getMessage() {
            return new SimpleMessage("m");
        }
    }
}
