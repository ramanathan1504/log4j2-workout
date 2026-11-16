import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

public class Repro {

    private static final StackTraceElement EXPLICIT =
            new StackTraceElement("com.example.Caller", "explicitMethod", "Caller.java", 4242);

    static final class RecordingAppender extends AbstractAppender {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch blocked = new CountDownLatch(1);
        final Map<String, String> sources = new ConcurrentHashMap<>();

        RecordingAppender() {
            super("recording", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            final String text = event.getMessage().getFormattedMessage();
            if (text.equals("block")) {
                blocked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            if (text.startsWith("case-")) {
                final StackTraceElement source = event.getSource();
                sources.put(text, (source == null ? "null" : source.getClassName() + "." + source.getMethodName() + ":"
                        + source.getLineNumber()) + " thread=" + Thread.currentThread().getName());
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        final Logger logger = LogManager.getLogger(Repro.class);
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration config = context.getConfiguration();
        final RecordingAppender appender = new RecordingAppender();
        appender.start();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();

        System.out.println("info logger=" + logger.getClass().getName() + " threadlocals="
                + System.getProperty("log4j2.enableThreadlocals", "default"));
        logger.info("block");
        appender.blocked.await(10, TimeUnit.SECONDS);
        for (int i = 0; i < 300; i++) {
            logger.info("filler {}", i);
        }
        final int classicLine = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        logger.info("case-classic");
        final int builderLine = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        logger.atInfo().log("case-builder");
        logger.atInfo().withLocation(EXPLICIT).log("case-explicit");
        appender.release.countDown();
        LogManager.shutdown();

        final String main = Thread.currentThread().getName();
        check(appender, "case-classic", "Repro.main:" + classicLine + " thread=" + main);
        check(appender, "case-builder", "Repro.main:" + builderLine + " thread=" + main);
        check(appender, "case-explicit", "com.example.Caller.explicitMethod:4242 thread=" + main);
    }

    private static void check(final RecordingAppender appender, final String key, final String expected) {
        final String observed = appender.sources.getOrDefault(key, "<not delivered>");
        System.out.println((observed.equals(expected) ? "PASS " : "FAIL ") + key + " expected=" + expected
                + " observed=" + observed);
    }
}
