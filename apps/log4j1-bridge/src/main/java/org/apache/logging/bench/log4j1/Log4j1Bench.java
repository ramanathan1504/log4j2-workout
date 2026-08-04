package org.apache.logging.bench.log4j1;

import java.util.Enumeration;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.NDC;
import org.apache.log4j.Priority;

/**
 * An application written entirely against the <strong>Log4j 1.x API</strong>,
 * running on Log4j 2 core through {@code log4j-1.2-api}. Feature matrix §12.
 *
 * <p>Not a single Log4j 2 type appears below — that is the point. This is what a
 * legacy application looks like, and it is the surface that has to keep working
 * when the implementation underneath it changes.
 *
 * <p>Run it with a 1.x config to also exercise
 * {@code Log4j1PropertiesConfigurationFactory}:
 * <pre>
 *   -Dlog4j1.compatibility=true
 *   -Dlog4j.configuration=configs/log4j1/log4j.properties
 * </pre>
 * Note that the 1.x property is {@code log4j.configuration}, not
 * {@code log4j.configurationFile}, and that compatibility mode is off by default.
 */
public final class Log4j1Bench {

    // 1.x style: Logger.getLogger, not LogManager.getLogger
    private static final Logger log = Logger.getLogger(Log4j1Bench.class);
    private static final Logger namedLog = Logger.getLogger("org.apache.logging.bench.named");

    // Category is the pre-1.2 name for Logger, deprecated but still on the API
    @SuppressWarnings("deprecation")
    private static final Category category = Category.getInstance(Log4j1Bench.class);

    public static void main(final String[] args) {
        banner();
        levels();
        mdcAndNdc();
        deprecatedApi();
        exceptions();
        introspectAppenders();
        System.out.println("\nLog4j 1.x bridge bench complete.");
    }

    private static void banner() {
        System.out.println("Log4j 1.x bridge bench");
        System.out.println("  compatibility mode  " + System.getProperty("log4j1.compatibility", "false"));
        System.out.println("  log4j.configuration " + System.getProperty("log4j.configuration", "<unset>"));
        System.out.println("  bridge from         " + packageVersion("org.apache.log4j.Logger"));
        System.out.println("  core                " + packageVersion("org.apache.logging.log4j.core.LoggerContext"));
        System.out.println();
    }

    /** All six 1.x levels, including FATAL, which 2.x keeps only for compatibility. */
    private static void levels() {
        log.trace("TRACE via the 1.x API");
        log.debug("DEBUG via the 1.x API");
        log.info("INFO via the 1.x API");
        log.warn("WARN via the 1.x API");
        log.error("ERROR via the 1.x API");
        log.fatal("FATAL via the 1.x API");

        // 1.x guards: isDebugEnabled / isEnabledFor(Priority)
        if (log.isDebugEnabled()) {
            log.debug("Guarded by isDebugEnabled()");
        }
        if (log.isEnabledFor(Level.WARN)) {
            log.warn("Guarded by isEnabledFor(Level.WARN)");
        }

        // Level is a subclass of the older Priority type
        log.log(Level.INFO, "Logged via log(Priority, Object)");
        namedLog.info("Logged through a string-named logger");
    }

    /** 1.x had two context mechanisms; both map onto Log4j 2's ThreadContext. */
    private static void mdcAndNdc() {
        MDC.put("traceId", "1x-bridge-abc123");
        MDC.put("userId", "legacy-user");
        NDC.push("outer");
        NDC.push("inner");
        try {
            log.info("MDC and NDC set through the 1.x API — %X and %x should render them");
            System.out.println("  MDC.get(traceId) = " + MDC.get("traceId"));
            System.out.println("  NDC depth        = " + NDC.getDepth());
            System.out.println("  NDC peek         = " + NDC.peek());
        } finally {
            NDC.pop();
            NDC.pop();
            NDC.remove();
            MDC.clear();
        }
    }

    @SuppressWarnings("deprecation")
    private static void deprecatedApi() {
        // Category and Priority are the 1.0-era names. Applications this old are
        // exactly the ones that cannot easily migrate, so the bridge must keep
        // them working.
        category.info("Logged through the deprecated Category API");
        category.log(Priority.WARN, "Logged through Category.log(Priority, Object)");

        // setLevel on a logger — in 2.x this mutates the configuration
        final Level previous = log.getLevel();
        log.setLevel(Level.DEBUG);
        log.debug("Visible after setLevel(DEBUG) through the 1.x API");
        log.setLevel(previous);

        System.out.println("  root logger level = " + Logger.getRootLogger().getLevel());
        System.out.println("  effective level   = " + log.getEffectiveLevel());
    }

    private static void exceptions() {
        try {
            throw new IllegalStateException("failure raised in 1.x-API code");
        } catch (final RuntimeException e) {
            // 1.x signature: log(Object message, Throwable t)
            log.error("Exception logged through the 1.x two-argument form", e);
        }
    }

    /** Walks the appenders the bridge exposes — 1.x code that inspects its own config. */
    private static void introspectAppenders() {
        System.out.println("\n  Appenders visible through the 1.x API:");
        @SuppressWarnings("unchecked")
        final Enumeration<Appender> appenders = Logger.getRootLogger().getAllAppenders();
        if (!appenders.hasMoreElements()) {
            // Observed on 2.27.0-SNAPSHOT with log4j1.compatibility=true and a
            // 1.x properties config that plainly works (it reconfigured the
            // pattern and wrote the file): getAllAppenders still returns empty.
            // The bridge routes logging faithfully but does not reflect Log4j 2
            // appenders back through the 1.x introspection API. Legacy code that
            // walks its own appenders — to attach one at runtime, say — sees
            // nothing here.
            System.out.println("    <none — the bridge does not expose Log4j 2 appenders"
                    + " through the 1.x introspection API, even in compatibility mode>");
            return;
        }
        while (appenders.hasMoreElements()) {
            final Appender appender = appenders.nextElement();
            System.out.printf("    %-20s %s%n", appender.getName(), appender.getClass().getName());
        }
        LogManager.getLoggerRepository();
    }

    private static String packageVersion(final String className) {
        try {
            final Package pkg = Class.forName(className).getPackage();
            final String v = pkg == null ? null : pkg.getImplementationVersion();
            return v == null ? "<unknown>" : v;
        } catch (final ClassNotFoundException e) {
            return "<absent>";
        }
    }

    private Log4j1Bench() {}
}
