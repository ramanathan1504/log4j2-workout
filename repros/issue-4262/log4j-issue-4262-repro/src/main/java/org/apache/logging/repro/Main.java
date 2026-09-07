package org.apache.logging.repro;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.OutputStreamManager;

/**
 * Reproduction for https://github.com/apache/logging-log4j2/issues/4262
 * <p>
 * {@code ConsoleAppender} inherits {@code immediateFlush}, {@code bufferedIo} and
 * {@code bufferSize} from {@code AbstractOutputStreamAppender.Builder}, and its own
 * constructor hands {@code true} to {@code super(...)} instead of the configured value.
 * </p>
 */
public final class Main {

    private static final PrintStream REPORT = System.err;
    private static final String CONTROL_FILE = "target/repro-4262/control.log";

    private static int failed;
    private static int total;

    public static void main(final String[] args) throws Exception {
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final PrintStream realOut = System.out;
        System.setOut(new PrintStream(captured, false, "UTF-8"));

        final Logger consoleLogger = LogManager.getLogger("console");
        final Logger fileLogger = LogManager.getLogger("file");

        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final AbstractOutputStreamAppender<?> console = outputStreamAppender(context, "CONSOLE");
        final AbstractOutputStreamAppender<?> file = outputStreamAppender(context, "FILE");

        REPORT.println();
        REPORT.println("Log4j " + version() + "  --  issue #4262");
        REPORT.println("config: <Console immediateFlush=\"false\" bufferedIo=\"true\" bufferSize=\"256\">");
        REPORT.println();
        REPORT.printf("  %-46s %-12s %-12s %s%n", "check", "expected", "actual", "");

        check(
                "[A] Console appender field immediateFlush",
                "false",
                String.valueOf(field(console, "immediateFlush")));

        consoleLogger.info("console-event");
        check("[B] bytes on System.out after one event", "0", String.valueOf(captured.size()));

        final ByteBuffer consoleBuffer = ((OutputStreamManager) console.getManager()).getByteBuffer();
        check("[C] Console manager buffer capacity", "256", String.valueOf(consoleBuffer.capacity()));

        check(
                "[D] control: File appender field immediateFlush",
                "false",
                String.valueOf(field(file, "immediateFlush")));

        fileLogger.info("file-event");
        check("[E] control: bytes in file after one event", "0", String.valueOf(new File(CONTROL_FILE).length()));

        final ByteBuffer fileBuffer = ((OutputStreamManager) file.getManager()).getByteBuffer();
        check("[F] control: File manager buffer capacity", "256", String.valueOf(fileBuffer.capacity()));

        LogManager.shutdown();

        REPORT.println();
        REPORT.println("  after LogManager.shutdown()");
        REPORT.printf("    System.out received : %s%n", quote(captured.toString(StandardCharsets.UTF_8.name())));
        REPORT.printf("    %s : %d bytes%n", CONTROL_FILE, new File(CONTROL_FILE).length());
        REPORT.println();
        REPORT.printf("RESULT: %d of %d checks failed%n", failed, total);
        REPORT.println(failed == 0
                ? "VERDICT: PASS -- ConsoleAppender honours the buffering attributes it advertises"
                : "VERDICT: FAIL -- ConsoleAppender ignores the buffering attributes it advertises");
        REPORT.flush();

        System.setOut(realOut);
    }

    private static void check(final String label, final String expected, final String actual) {
        total++;
        final boolean pass = expected.equals(actual);
        if (!pass) {
            failed++;
        }
        REPORT.printf("  %-46s %-12s %-12s %s%n", label, expected, actual, pass ? "PASS" : "FAIL");
    }

    private static AbstractOutputStreamAppender<?> outputStreamAppender(
            final LoggerContext context, final String name) {
        final Appender appender = context.getConfiguration().getAppender(name);
        if (appender == null) {
            throw new IllegalStateException("no appender named " + name + " in the configuration");
        }
        return (AbstractOutputStreamAppender<?>) appender;
    }

    private static Object field(final Object target, final String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field declared = type.getDeclaredField(name);
                declared.setAccessible(true);
                return declared.get(target);
            } catch (final NoSuchFieldException continueUpTheHierarchy) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String version() {
        final Package core = LoggerContext.class.getPackage();
        final String implementation = core == null ? null : core.getImplementationVersion();
        return implementation == null ? "(version not in the manifest)" : implementation;
    }

    private static String quote(final String value) {
        return '"' + value.replace("\n", "\\n").replace("\r", "\\r") + '"';
    }
}
