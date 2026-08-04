package org.apache.logging.bench.bridges;

import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.util.logging.LogManager;

import org.apache.logging.log4j.io.IoBuilder;

/**
 * Every bridge that routes another logging API <em>into</em> Log4j.
 * Feature matrix §12.
 *
 * <p>Each section logs through a foreign API and then reports whether the call
 * actually landed in Log4j, because a bridge that is absent or mis-wired does
 * not fail — the foreign API just keeps using its own default backend and the
 * events quietly go somewhere else. Checking the concrete implementation class
 * is the only reliable signal.
 *
 * <pre>
 *   ./bench run bridges-in --config xml/baseline-console
 *   ./bench matrix --apps bridges-in --log4j 2.24.1,2.27.0-SNAPSHOT
 * </pre>
 */
public final class BridgesInBench {

    public static void main(final String[] args) {
        banner();

        slf4j();
        julBridge();
        commonsLogging();
        systemLogger();
        ioStreams();

        org.apache.logging.log4j.LogManager.shutdown();
        System.out.println();
        System.out.println("Bridges-in bench complete.");
    }

    private static void banner() {
        System.out.println("Log4j bench — bridges into Log4j");
        System.out.println("  log4j-api    " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("  log4j-core   " + versionOf("org.apache.logging.log4j.core.LoggerContext"));
        System.out.println("  config       "
                + System.getProperty("log4j.configurationFile", "<none set>"));

        final org.apache.logging.log4j.spi.LoggerContext ctx =
                org.apache.logging.log4j.LogManager.getContext(false);
        if (ctx instanceof org.apache.logging.log4j.core.LoggerContext) {
            System.out.println("  loaded       "
                    + ((org.apache.logging.log4j.core.LoggerContext) ctx).getConfiguration().getName());
        }
        System.out.println();
    }

    private static String versionOf(final String className) {
        try {
            final Package pkg = Class.forName(className).getPackage();
            final String version = pkg == null ? null : pkg.getImplementationVersion();
            return version == null ? "<unknown — running from classes>" : version;
        } catch (final ClassNotFoundException e) {
            return "<absent from classpath>";
        }
    }

    /** SLF4J 1.7 → Log4j, via log4j-slf4j-impl. */
    private static void slf4j() {
        System.out.println("──── slf4j 1.7 (log4j-slf4j-impl)");

        final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BridgesInBench.class);
        log.info("INFO through the SLF4J 1.7 API");
        log.warn("WARN with a parameter: {}", "value");
        log.error("ERROR with a throwable", new IllegalStateException("synthetic, via slf4j"));

        // SLF4J MDC is a separate class from Log4j's ThreadContext; the bridge
        // is what makes one visible as the other.
        org.slf4j.MDC.put("traceId", "slf4j-0001");
        log.info("With SLF4J MDC set — %X{traceId} should render it");
        System.out.println("  Log4j sees MDC traceId  "
                + org.apache.logging.log4j.ThreadContext.get("traceId"));
        org.slf4j.MDC.clear();

        System.out.println("  binding      " + org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName());
        System.out.println("  logger impl  " + log.getClass().getName());
    }

    /**
     * java.util.logging → Log4j, via log4j-jul.
     *
     * <p>This one is only active if {@code java.util.logging.manager} is set
     * before any JUL class initialises, which ./bench passes for this app. Set
     * it late and the JDK's own LogManager wins, silently.
     */
    private static void julBridge() {
        System.out.println("──── java.util.logging (log4j-jul)");

        final java.util.logging.Logger log =
                java.util.logging.Logger.getLogger(BridgesInBench.class.getName());
        log.info("INFO through the JUL API");
        log.warning("WARNING through the JUL API");
        log.severe("SEVERE through the JUL API — maps to Log4j ERROR");
        log.log(java.util.logging.Level.FINE, "FINE — maps to Log4j DEBUG");

        System.out.println("  java.util.logging.manager  "
                + System.getProperty("java.util.logging.manager", "<unset — bridge inactive>"));
        System.out.println("  LogManager impl            " + LogManager.getLogManager().getClass().getName());
        System.out.println("  Logger impl                " + log.getClass().getName());
    }

    /** Commons Logging → Log4j, via log4j-jcl. */
    private static void commonsLogging() {
        System.out.println("──── commons-logging (log4j-jcl)");

        final org.apache.commons.logging.Log log =
                org.apache.commons.logging.LogFactory.getLog(BridgesInBench.class);
        log.info("INFO through the Commons Logging API");
        log.warn("WARN through the Commons Logging API");
        log.error("ERROR with a throwable", new IllegalStateException("synthetic, via jcl"));

        System.out.println("  LogFactory impl  "
                + org.apache.commons.logging.LogFactory.getFactory().getClass().getName());
        System.out.println("  Log impl         " + log.getClass().getName());
    }

    /** JDK 9+ System.Logger → Log4j, via log4j-jpl. */
    private static void systemLogger() {
        System.out.println("──── System.Logger (log4j-jpl)");

        final System.Logger log = System.getLogger(BridgesInBench.class.getName());
        log.log(Level.INFO, "INFO through the System.Logger API");
        log.log(Level.WARNING, "WARNING through the System.Logger API");
        log.log(Level.ERROR, "ERROR through the System.Logger API",
                new IllegalStateException("synthetic, via jpl"));

        System.out.println("  System.Logger impl  " + log.getClass().getName());
    }

    /** Streams, writers and readers → Log4j, via log4j-iostreams. */
    private static void ioStreams() {
        System.out.println("──── iostreams (log4j-iostreams)");

        final org.apache.logging.log4j.Logger log =
                org.apache.logging.log4j.LogManager.getLogger("iostreams");

        final PrintStream out = IoBuilder.forLogger(log)
                .setLevel(org.apache.logging.log4j.Level.INFO)
                .buildPrintStream();
        out.println("A line written to a PrintStream, logged at INFO");
        out.printf("printf into the log: %s = %d%n", "answer", Integer.valueOf(42));
        out.flush();

        // The point of the module: legacy code that only knows how to print,
        // and a stack trace being one of the commonest things printed.
        new IllegalStateException("printStackTrace redirected into Log4j").printStackTrace(out);
        out.flush();

        System.out.println("  PrintStream impl  " + out.getClass().getName());
    }

    private BridgesInBench() {}
}