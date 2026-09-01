package org.apache.logging.repro;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;

public final class Main {

    private static final List<StatusData> STATUS_ERRORS = new ArrayList<>();

    static Exception unstableCause() {
        return new Exception("Exception") {
            @Override
            public synchronized Throwable getCause() {
                return new Throwable("Throwable");
            }
        };
    }

    static Exception stableCause() {
        return new Exception("Exception", new Throwable("Throwable"));
    }

    static Exception wrappedUnstableCause() {
        return new Exception("Wrapper", unstableCause());
    }

    static Exception lateCause() {
        return new Exception("Exception") {
            private int calls;

            @Override
            public synchronized Throwable getCause() {
                return calls++ == 0 ? null : new Throwable("Throwable");
            }
        };
    }

    static Exception unstableStackTrace() {
        return new Exception("Exception", new Throwable("Throwable")) {
            @Override
            public StackTraceElement[] getStackTrace() {
                return new StackTraceElement[] {new StackTraceElement("Fresh", "each", "Call.java", 1)};
            }
        };
    }

    static Exception unstableCauseChain() {
        class Unstable extends Exception {
            Unstable(final String message) {
                super(message);
            }

            @Override
            public synchronized Throwable getCause() {
                return new Unstable("depth");
            }
        }
        return new Unstable("Exception");
    }

    static void probe(final String caseName, final String loggerName, final Exception thrown) {
        final Logger logger = LogManager.getLogger(loggerName);
        String verdict;
        STATUS_ERRORS.clear();
        try {
            logger.error("Error", thrown);
            verdict = STATUS_ERRORS.isEmpty() ? "PASS" : "FAIL   " + describe(STATUS_ERRORS.get(0).getThrowable());
        } catch (final Throwable error) {
            verdict = "THROWN " + describe(error);
        }
        System.out.printf("%-22s %-5s %s%n", caseName, loggerName, verdict);
    }

    static String describe(final Throwable error) {
        if (error == null) {
            return "(no throwable on status event)";
        }
        final Throwable root = error.getCause() != null ? error.getCause() : error;
        final StackTraceElement[] trace = root.getStackTrace();
        final String at = trace.length > 0 ? "  at " + trace[0] : "";
        return root.getClass().getName() + (root.getMessage() == null ? "" : ": " + root.getMessage()) + at;
    }

    public static void main(final String[] args) {
        StatusLogger.getLogger().registerListener(new StatusListener() {
            @Override
            public void log(final StatusData data) {
                STATUS_ERRORS.add(data);
            }

            @Override
            public Level getStatusLevel() {
                return Level.ERROR;
            }

            @Override
            public void close() {}
        });
        System.out.printf("%-22s %-5s %s%n", "case", "conv", "result");
        final String[] loggerNames = {"EX", "EXQ", "XEX", "REX", "JSON"};
        for (final String loggerName : loggerNames) {
            probe("stable-cause", loggerName, stableCause());
            probe("unstable-cause", loggerName, unstableCause());
            probe("wrapped-unstable", loggerName, wrappedUnstableCause());
            probe("late-cause", loggerName, lateCause());
            probe("unstable-stacktrace", loggerName, unstableStackTrace());
        }
        for (final String loggerName : loggerNames) {
            probe("unstable-cause-chain", loggerName, unstableCauseChain());
        }
        LogManager.shutdown();
        for (final String loggerName : loggerNames) {
            reportRendered(loggerName);
        }
    }

    static void reportRendered(final String loggerName) {
        final java.nio.file.Path path = java.nio.file.Paths.get("target/out-" + loggerName.toLowerCase() + ".log");
        try {
            final List<String> lines = java.nio.file.Files.readAllLines(path);
            long caused = lines.stream()
                    .filter(line -> line.contains("Caused by") || line.contains("Throwable"))
                    .count();
            System.out.printf("rendered %-5s %d lines, %d mentioning the cause%n", loggerName, lines.size(), caused);
        } catch (final Exception error) {
            System.out.printf("rendered %-5s unreadable: %s%n", loggerName, error);
        }
    }
}
