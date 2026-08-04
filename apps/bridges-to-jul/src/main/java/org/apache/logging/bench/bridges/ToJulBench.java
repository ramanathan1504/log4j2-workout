package org.apache.logging.bench.bridges;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Application code written against the Log4j API, delivered to
 * {@code java.util.logging}. Feature matrix §12.
 *
 * <p>The counterpart of {@code apps/bridges-in}, which routes JUL <em>into</em>
 * Log4j. Running both directions matters because the level mappings are not
 * symmetric: Log4j has TRACE and FATAL, JUL has FINEST/FINER/FINE/CONFIG and
 * SEVERE, and the round trip is lossy in both directions.
 *
 * <p>log4j-core is not on this classpath. If it were, it would answer
 * {@code LogManager.getLogger} and the bridge would never run — while the
 * console output looked much the same, which is what makes this failure mode
 * worth an explicit check rather than an eyeball.
 *
 * <pre>
 *   ./bench run bridges-to-jul
 * </pre>
 */
public final class ToJulBench {

    private static final Logger log = LogManager.getLogger(ToJulBench.class);

    public static void main(final String[] args) {
        // JUL's default console handler only passes INFO and above, so the
        // finer Log4j levels would vanish before the mapping could be seen.
        final java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.setLevel(java.util.logging.Level.ALL);
        for (final Handler handler : root.getHandlers()) {
            handler.setLevel(java.util.logging.Level.ALL);
        }
        if (root.getHandlers().length == 0) {
            final ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(java.util.logging.Level.ALL);
            root.addHandler(handler);
        }

        banner();
        levels();
        messages();
        context();

        System.out.println();
        System.out.println("Log4j-to-JUL bench complete.");
    }

    private static void banner() {
        System.out.println("Log4j bench — Log4j API routed out to java.util.logging");
        System.out.println("  log4j-api    " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("  log4j-core   " + versionOf("org.apache.logging.log4j.core.LoggerContext")
                + "   <- must be absent, or it would serve the API itself");
        System.out.println("  factory      " + LogManager.getFactory().getClass().getName());
        System.out.println("  logger impl  " + log.getClass().getName());
        System.out.println("  JUL manager  "
                + java.util.logging.LogManager.getLogManager().getClass().getName());
        System.out.println();
    }

    private static String versionOf(final String className) {
        try {
            final Package pkg = Class.forName(className).getPackage();
            final String version = pkg == null ? null : pkg.getImplementationVersion();
            return version == null ? "<present, version unknown>" : version;
        } catch (final ClassNotFoundException e) {
            return "<absent from classpath>";
        }
    }

    /**
     * The level mapping, which is where this bridge is interesting.
     * Log4j TRACE/DEBUG/INFO/WARN/ERROR/FATAL has to land somewhere in JUL's
     * FINEST..SEVERE, and two Log4j levels share one JUL level at each end.
     */
    private static void levels() {
        System.out.println("──── levels");

        log.trace("TRACE through the Log4j API");
        log.debug("DEBUG through the Log4j API");
        log.info("INFO through the Log4j API");
        log.warn("WARN through the Log4j API");
        log.error("ERROR through the Log4j API");
        log.fatal("FATAL through the Log4j API — JUL has no level above SEVERE");

        System.out.println("  isTraceEnabled  " + log.isTraceEnabled());
        System.out.println("  isDebugEnabled  " + log.isDebugEnabled());
        System.out.println("  level           " + log.getLevel());
    }

    private static void messages() {
        System.out.println("──── messages");

        log.info("Brace form — user {} order {}", "alice", Integer.valueOf(4711));
        log.error("With a throwable", new IllegalStateException("synthetic, routed to JUL"));
        log.info(() -> "Lambda supplier — evaluated only if enabled");
    }

    private static void context() {
        System.out.println("──── context");

        // Stronger than "JUL has no MDC": under this bridge ThreadContext is a
        // no-op on the Log4j side too. JULProvider registers
        // NoOpThreadContextMap.INSTANCE, so the put below stores nothing and the
        // map reads back empty — application code that puts a trace id and later
        // reads it back gets null, not just unrendered output. Expect {} here.
        ThreadContext.put("traceId", "to-jul-0001");
        try {
            log.info("With Log4j ThreadContext set — JUL has nowhere to put it");
            System.out.println("  Log4j ThreadContext  " + ThreadContext.getImmutableContext()
                    + "   <- empty: NoOpThreadContextMap, the put was discarded");
            System.out.println("  JUL equivalent       <none — JUL has no MDC>");
        } finally {
            ThreadContext.clearAll();
        }
    }

    private ToJulBench() {}
}
