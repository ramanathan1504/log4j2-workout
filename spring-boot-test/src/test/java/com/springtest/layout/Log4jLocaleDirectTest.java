package com.springtest.layout;

import org.apache.logging.log4j.core.pattern.DatePatternConverter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.time.MutableInstant;

/**
 * Direct reproduction from Github issue #4129 — tests DatePatternConverter
 * locale argument parsing at the lowest level.
 *
 * Reported issue:
 * - %d{dd-MMMM-yyyy}{GMT}{de-DE} ✓ works (German month names)
 * - %d{dd-MMMM-yyyy}{de-DE} ✗ broken (English month names, locale silently ignored)
 * - %d{EEEE, dd. MMMM yyyy}{de-DE} ✗ broken
 */
public class Log4jLocaleDirectTest {
    public static void main(String[] args) {
        MutableInstant instant = new MutableInstant();
        // May 25, 2026 12:00:00 UTC
        instant.initFromEpochMilli(1748174400000L, 0);

        LogEvent event = Log4jLogEvent.newBuilder()
            .setLoggerName("test")
            .setLoggerFqcn("test")
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage("test"))
            .setInstant(instant)
            .build();

        System.out.println("=== DatePatternConverter Locale Parsing ===\n");

        // Case 1: timezone + locale (works)
        test(event, "dd-MMMM-yyyy", "GMT", "de-DE");

        // Case 2: locale only (broken — locale ignored)
        test(event, "dd-MMMM-yyyy", "de-DE");

        // Case 3: full date format with locale only (broken)
        test(event, "EEEE, dd. MMMM yyyy", "de-DE");
    }

    private static void test(LogEvent event, String... options) {
        DatePatternConverter converter = DatePatternConverter.newInstance(options);
        StringBuilder sb = new StringBuilder();
        converter.format(event, sb);
        System.out.print("Options: ");
        for (String o : options) System.out.print("["+o+"] ");
        System.out.println("-> Result: " + sb.toString());
    }
}

