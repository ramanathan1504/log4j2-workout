//package com.springtest.layout;
//
//import org.apache.logging.log4j.Level;
//import org.apache.logging.log4j.core.config.DefaultConfiguration;
//import org.apache.logging.log4j.core.impl.Log4jLogEvent;
//import org.apache.logging.log4j.core.layout.PatternLayout;
//import org.apache.logging.log4j.message.SimpleMessage;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.Locale;
//
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
///**
// * Validates the fix for GitHub issue #4129:
// * DatePatternConverter locale argument must be honoured even when the timezone argument is omitted.
// *
// * %d{pattern}{timezone}{locale} — always worked
// * %d{pattern}{locale}           — was broken (locale silently ignored); fixed in 2.27.0-SNAPSHOT
// */
//class PatternLayoutLocaleParsingTest {
//
//    private Locale originalLocale;
//
//    @BeforeEach
//    void forceEnglishDefaultLocale() {
//        originalLocale = Locale.getDefault();
//        // Ensure JVM default is English so locale-absence and locale-presence are distinguishable
//        Locale.setDefault(Locale.ENGLISH);
//    }
//
//    @AfterEach
//    void restoreDefaultLocale() {
//        Locale.setDefault(originalLocale);
//    }
//
//    /** Baseline: three-argument form {pattern}{timezone}{locale} — must produce German month. */
//    @Test
//    void datePatternUsesLocaleWhenTimezoneAndLocaleAreBothProvided() {
//        String formatted = formatDate("%d{dd-MMMM-yyyy}{UTC}{de-DE} %p %m%n");
//        assertTrue(formatted.contains("Januar"),
//                () -> "Expected German month when locale is explicit third argument, got: " + formatted);
//    }
//
//    /**
//     * Fix: two-argument form {pattern}{locale} — locale must now be applied.
//     * Regression was: locale silently treated as timezone (resolved to GMT), English month returned.
//     * Fixed in: https://github.com/apache/logging-log4j2/issues/4129
//     */
//    @Test
//    void localeAppliedWhenTimezoneOmitted_monthName() {
//        String formatted = formatDate("%d{dd-MMMM-yyyy}{de-DE} %p %m%n");
//        assertTrue(formatted.contains("Januar"),
//                () -> "Expected German month 'Januar' with locale-only argument, got: " + formatted);
//        assertFalse(formatted.contains("January"),
//                () -> "Must NOT produce English 'January' when de-DE locale is set, got: " + formatted);
//    }
//
//    /**
//     * Fix: full date pattern with locale only — day-of-week and month must be in German.
//     * 2024-01-01 is a Monday → Montag in German.
//     */
//    @Test
//    void localeAppliedWhenTimezoneOmitted_fullDate() {
//        String formatted = formatDate("%d{EEEE, dd. MMMM yyyy}{de-DE} %p %m%n");
//        assertTrue(formatted.contains("Januar"),
//                () -> "Expected German month 'Januar' in full-date pattern, got: " + formatted);
//        assertTrue(formatted.contains("Montag"),
//                () -> "Expected German day-of-week 'Montag' in full-date pattern, got: " + formatted);
//    }
//
//    private static String formatDate(String pattern) {
//        PatternLayout layout = PatternLayout.newBuilder()
//                .withPattern(pattern)
//                .withConfiguration(new DefaultConfiguration())
//                .build();
//
//        Log4jLogEvent event = Log4jLogEvent.newBuilder()
//                .setLoggerName(PatternLayoutLocaleParsingTest.class.getName())
//                .setLevel(Level.INFO)
//                .setMessage(new SimpleMessage("locale-check"))
//                .setTimeMillis(1704067200000L) // 2024-01-01T00:00:00Z UTC → January / Januar
//                .build();
//
//        return layout.toSerializable(event);
//    }
//}
