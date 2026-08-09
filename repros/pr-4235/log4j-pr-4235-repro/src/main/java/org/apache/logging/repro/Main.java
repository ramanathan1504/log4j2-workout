/*
 * Reproduction for https://github.com/apache/logging-log4j2/pull/4235
 *
 * Rfc5424Layout sanitizes SD-PARAM-NAME and escapes SD-PARAM-VALUE (added by
 * #4073), but two neighbouring fields of the same record go out unmodified:
 *
 *   Rfc5424Layout#appendMessageId        appends StructuredDataMessage#getType() raw
 *   Rfc5424Layout#formatStructuredElement appends the SD-ID raw
 *
 * Neither StructuredDataMessage#setType nor the StructuredDataId constructors
 * validate characters -- they check length only. Both fields carry application
 * data.
 *
 * This writes one hostile event through an Rfc5424Layout and prints what came
 * out, so "one record or two" is decided by looking rather than by reasoning.
 */
package org.apache.logging.repro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StructuredDataMessage;

public final class Main {

    /** Written by the File appender in log4j2.xml. */
    private static final Path OUTPUT = Path.of("logs", "rfc5424", "syslog.log");

    /**
     * A ']' closes the structured element early; everything after it is read as a
     * second, caller-controlled element.
     */
    private static final String HOSTILE_SD_ID = "a] [forged@1 user=\"root";

    /**
     * A newline ends the syslog record; what follows is parsed as a separate
     * message, with its own priority and version.
     */
    private static final String HOSTILE_TYPE = "Audit\n<13>1 - - - - -";

    public static void main(final String[] args) throws IOException, InterruptedException {
        final Logger logger = LogManager.getLogger(Main.class);

        // Control: a well-formed structured message. Must be unchanged by #4235.
        final StructuredDataMessage benign = new StructuredDataMessage("Audit@32473", "login ok", "Audit");
        benign.put("user", "alice");
        logger.info(benign);

        // The injection. Both hostile values are ordinary application data as far
        // as Log4j is concerned.
        final StructuredDataMessage hostile = new StructuredDataMessage(HOSTILE_SD_ID, "login ok", HOSTILE_TYPE);
        hostile.put("user", "root");
        logger.info(hostile);

        // Let the appender flush before reading the file back.
        LogManager.shutdown();
        Thread.sleep(200);

        report();
    }

    private static void report() throws IOException {
        System.out.println();
        System.out.println("──── " + OUTPUT + " ────");

        if (!Files.exists(OUTPUT)) {
            System.out.println("NOT WRITTEN.");
            System.out.println();
            System.out.println("A clean exit proves nothing here -- Log4j catches appender exceptions,");
            System.out.println("reports them through StatusLogger and exits 0. Re-run with:");
            System.out.println("  MAVEN_OPTS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' ./run.sh");
            return;
        }

        final List<String> lines = Files.readAllLines(OUTPUT, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            System.out.printf("%2d | %s%n", i + 1, lines.get(i));
        }

        System.out.println();
        System.out.println("──── verdict ────");
        System.out.println("Two events were logged. Count the RECORDS above:");
        System.out.println();
        System.out.println("  2 records, and line 2 contains a literal '?' where the newline");
        System.out.println("  and the ']' were  ->  sanitized. PR #4235 is applied.");
        System.out.println();
        System.out.println("  3+ records, one of them starting '<13>1'  ->  injection reproduced.");
        System.out.println("  That third record was never logged by this program; it is the");
        System.out.println("  tail of the message type, promoted to a syslog record of its own.");
    }

    private Main() {}
}
