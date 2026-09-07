package org.apache.logging.repro;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * What the hardcoded flush costs. Run as {@code Bench flush} or {@code Bench noflush};
 * {@code noflush} reflectively writes the value the configuration asked for.
 */
public final class Bench {

    private static final int WARMUP = 50_000;
    private static final int EVENTS = 500_000;

    public static void main(final String[] args) throws Exception {
        final boolean honour = args.length > 0 && "noflush".equals(args[0]);
        final String loggerName = args.length > 1 ? args[1] : "console";
        final String appenderName = args.length > 2 ? args[2] : "CONSOLE";

        System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream("/dev/null"), 8192), true));

        final Logger logger = LogManager.getLogger(loggerName);
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Appender console = context.getConfiguration().getAppender(appenderName);

        if (honour) {
            final Field immediateFlush = console.getClass()
                    .getSuperclass()
                    .getDeclaredField("immediateFlush");
            immediateFlush.setAccessible(true);
            immediateFlush.setBoolean(console, false);
        }

        for (int i = 0; i < WARMUP; i++) {
            logger.info("warmup {}", i);
        }

        final long start = System.nanoTime();
        for (int i = 0; i < EVENTS; i++) {
            logger.info("event {}", i);
        }
        final long elapsed = System.nanoTime() - start;

        LogManager.shutdown();
        System.err.printf(
                "%-14s immediateFlush=%-5s  %,d events  %6d ms  %,10.0f events/s%n",
                loggerName,
                honour ? "false" : "true",
                EVENTS,
                elapsed / 1_000_000,
                EVENTS / (elapsed / 1_000_000_000.0));
    }
}
