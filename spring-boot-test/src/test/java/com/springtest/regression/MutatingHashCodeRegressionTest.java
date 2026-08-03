package com.springtest.regression;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression test for Log4j issue: ThrowableStackTraceRenderer NPE with mutating hashCode.
 *
 * Root cause: In 2.25.0+, ThrowableStackTraceRenderer uses a HashMap for metadata caching.
 * If an exception's hashCode() mutates between calls, the map lookup returns null → NPE.
 *
 * Fix (2.27.0-SNAPSHOT): Uses IdentityHashMap which relies on object identity, not hashCode.
 *
 * Affected versions : 2.25.0 – 2.26.0
 * Safe versions     : 2.24.1 (pre-refactoring), 2.27.0-SNAPSHOT (fixed)
 */
public class MutatingHashCodeRegressionTest {

    /**
     * An exception whose hashCode() increments on every call.
     * Simulates objects that violate the hashCode contract (e.g., Spark's TaskContext).
     * When stored in a HashMap<Throwable, ?>, the entry becomes unreachable after the first
     * put() because subsequent get() calls with the same key use a different bucket.
     */
    static class MutatingException extends RuntimeException {
        private int counter = 0;

        @Override
        public int hashCode() {
            return ++counter;
        }
    }

    /** Collects StatusLogger ERROR/WARN messages during the test. */
    private final List<StatusData> capturedStatusEvents = new ArrayList<>();
    private StatusListener statusListener;

    @BeforeEach
    void installStatusListener() {
        statusListener = new StatusListener() {
            @Override
            public void log(StatusData data) {
                if (!data.getLevel().isLessSpecificThan(Level.WARN)) {
                    capturedStatusEvents.add(data);
                }
            }

            @Override
            public Level getStatusLevel() {
                return Level.WARN;
            }

            @Override
            public void close() {}
        };
        StatusLogger.getLogger().registerListener(statusListener);
    }

    @AfterEach
    void removeStatusListener() {
        StatusLogger.getLogger().removeListener(statusListener);
        capturedStatusEvents.clear();
    }

    @Test
    public void testMutatingHashCodeDoesNotCauseNPE() {
        Logger logger = LogManager.getLogger(MutatingHashCodeRegressionTest.class);

        // Log with a MutatingException — triggers ThrowableStackTraceRenderer in JsonTemplateLayout
        logger.error("Testing mutating hashCode exception", new MutatingException());

        // Collect diagnostic info for the report
        capturedStatusEvents.forEach(event -> System.out.println(
                "[StatusLogger " + event.getLevel() + "] " + event.getMessage().getFormattedMessage()
                + (event.getThrowable() != null ? " | " + event.getThrowable() : "")));

        // On broken versions (2.25.0–2.26.0), StatusLogger records NPE from ThrowableStackTraceRenderer
        boolean hasNPE = capturedStatusEvents.stream().anyMatch(event ->
                event.getThrowable() instanceof NullPointerException
                || (event.getMessage() != null
                    && event.getMessage().getFormattedMessage().contains("NullPointerException")));

        assertFalse(hasNPE,
                "REGRESSION DETECTED: NullPointerException in ThrowableStackTraceRenderer.\n" +
                "This confirms the mutating-hashCode bug is present in this Log4j version.\n" +
                "Failing status events: " + capturedStatusEvents);
    }
}
