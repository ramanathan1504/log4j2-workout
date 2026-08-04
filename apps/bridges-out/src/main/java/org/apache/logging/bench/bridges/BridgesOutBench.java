package org.apache.logging.bench.bridges;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.StringMapMessage;

/**
 * Application code written against the Log4j API, with the events handed to
 * somebody else's backend. Feature matrix §12.
 *
 * <p>This is the direction that breaks quietly. log4j-core is not on this
 * classpath at all — if it were, it would serve {@code LogManager.getLogger}
 * and the bridge would never be exercised, while every message still appeared
 * on the console exactly as though it had been. The check below is therefore on
 * the concrete provider class, not on whether output shows up.
 *
 * <p>Two things do not survive the trip, and both are worth watching across
 * versions:
 * <ul>
 *   <li>Log4j's richer {@code Message} types collapse to their formatted
 *       string, since SLF4J and JUL have no equivalent concept.</li>
 *   <li>{@code FATAL} has no SLF4J level and arrives as ERROR.</li>
 * </ul>
 *
 * <pre>
 *   ./bench run bridges-out      # Log4j API -> SLF4J -> Logback
 *   ./bench run bridges-to-jul   # Log4j API -> java.util.logging
 * </pre>
 */
public final class BridgesOutBench {

    private static final Logger log = LogManager.getLogger(BridgesOutBench.class);

    public static void main(final String[] args) {
        banner();
        levels();
        messages();
        context();

        System.out.println();
        System.out.println("Bridges-out bench complete.");
    }

    private static void banner() {
        System.out.println("Log4j bench — Log4j API routed out to another backend");
        System.out.println("  log4j-api    " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("  log4j-core   " + versionOf("org.apache.logging.log4j.core.LoggerContext")
                + "   <- must be absent, or it would serve the API itself");

        // Which provider actually answered. This is the only honest signal that
        // the bridge is in play: output appears either way.
        System.out.println("  factory      " + LogManager.getFactory().getClass().getName());
        System.out.println("  logger impl  " + log.getClass().getName());
        System.out.println("  slf4j        " + versionOf("org.slf4j.LoggerFactory"));
        System.out.println("  logback      " + versionOf("ch.qos.logback.classic.LoggerContext"));
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

    /** Level mapping, including the one Log4j level SLF4J has no name for. */
    private static void levels() {
        System.out.println("──── levels");

        log.trace("TRACE through the Log4j API");
        log.debug("DEBUG through the Log4j API");
        log.info("INFO through the Log4j API");
        log.warn("WARN through the Log4j API");
        log.error("ERROR through the Log4j API");
        // SLF4J has no FATAL. Expect this to arrive as ERROR, possibly with a
        // marker — which is exactly the sort of thing that changes between
        // versions without anyone noticing.
        log.fatal("FATAL through the Log4j API — SLF4J has no such level");

        System.out.println("  isTraceEnabled  " + log.isTraceEnabled());
        System.out.println("  isDebugEnabled  " + log.isDebugEnabled());
        System.out.println("  level           " + log.getLevel());
    }

    /** Message types, which have no counterpart on the far side. */
    private static void messages() {
        System.out.println("──── messages");

        log.info("Brace form — user {} order {}", "alice", Integer.valueOf(4711));
        log.info(new ParameterizedMessage("ParameterizedMessage — {} of {}",
                new Object[] {"one", "two"}));

        // A structured message with no structured destination: the backend can
        // only receive the rendered string, so any consumer downstream that was
        // parsing fields loses them at this boundary.
        final StringMapMessage map = new StringMapMessage();
        map.put("event", "checkout");
        map.put("basket", "17");
        log.info(map);

        log.error("With a throwable", new IllegalStateException("synthetic, routed out"));
        log.info(() -> "Lambda supplier — evaluated only if enabled");
    }

    /** ThreadContext, which has to be mapped onto the backend's own MDC. */
    private static void context() {
        System.out.println("──── context");

        ThreadContext.put("traceId", "bridges-out-0001");
        ThreadContext.push("outer");
        try {
            log.info("With Log4j ThreadContext set — does the backend's MDC see it?");
            System.out.println("  Log4j ThreadContext  " + ThreadContext.getImmutableContext());
            System.out.println("  SLF4J MDC            " + slf4jMdc());
        } finally {
            ThreadContext.clearAll();
        }
    }

    private static String slf4jMdc() {
        try {
            return String.valueOf(org.slf4j.MDC.getCopyOfContextMap());
        } catch (final NoClassDefFoundError e) {
            return "<slf4j absent — running the to-jul profile>";
        }
    }

    private BridgesOutBench() {}
}
