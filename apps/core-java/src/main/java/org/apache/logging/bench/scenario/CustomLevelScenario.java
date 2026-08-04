package org.apache.logging.bench.scenario;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import org.apache.logging.bench.Scenario;

/**
 * Custom levels — the ones the {@code custom-levels} configs declare.
 * Feature matrix §11.
 *
 * <p>Without this, a custom-levels configuration is only half exercised: the
 * levels get created and the filters get built, but nothing ever logs at one, so
 * every custom-level file comes out empty and a broken filter looks identical to
 * a working one.
 *
 * <p>The Log4j API has no {@code audit()} or {@code notice()} method, so the only
 * ways to reach a custom level are {@code Logger.log(Level, ...)} — used here —
 * or a generated custom-logger wrapper. That asymmetry with the built-in levels
 * is itself worth seeing.
 */
public final class CustomLevelScenario implements Scenario {

    private static final Logger log = LogManager.getLogger(CustomLevelScenario.class);

    /**
     * These must match the custom-levels configs — and matching means the
     * INTEGER, not the name. Level.forName registers a name globally the first
     * time it is seen and will not renumber it afterwards, so a config declaring
     * AUDIT=150 and code asking for AUDIT=250 silently gets whichever ran first.
     */
    private static final Level AUDIT = Level.forName("AUDIT", 150);
    private static final Level NOTICE = Level.forName("NOTICE", 350);
    private static final Level VERBOSE = Level.forName("VERBOSE", 550);

    @Override
    public String name() {
        return "custom-levels";
    }

    @Override
    public String describes() {
        return "Custom levels: AUDIT(150), NOTICE(350), VERBOSE(550) against the built-in scale";
    }

    @Override
    public void run() {
        System.out.println("  declared levels");
        report(AUDIT);
        report(NOTICE);
        report(VERBOSE);

        // The built-in scale, for comparison. Lower is more severe.
        System.out.println("  built-in scale");
        for (final Level level : new Level[] {
                Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE}) {
            report(level);
        }

        System.out.println("  logging at each custom level");
        ThreadContext.put("traceId", "custom-levels-0001");
        try {
            log.log(AUDIT, "AUDIT — more severe than ERROR, less than FATAL");
            log.log(AUDIT, "AUDIT with a throwable",
                    new IllegalStateException("synthetic audit failure"));
            log.log(NOTICE, "NOTICE — between WARN and INFO");
            log.log(VERBOSE, "VERBOSE — between DEBUG and TRACE");

            // Interleaved with built-ins, so the ordering in a file shows the
            // integers really are what sorts them.
            log.fatal("FATAL (100)");
            log.error("ERROR (200)");
            log.warn("WARN (300)");
            log.info("INFO (400)");
            log.debug("DEBUG (500)");
            log.trace("TRACE (600) — below VERBOSE, so a VERBOSE logger drops it");
        } finally {
            ThreadContext.clearAll();
        }

        // isEnabled against a custom level, which is what guards an expensive
        // audit payload in real code.
        System.out.printf("  isEnabled(AUDIT)   %s%n", log.isEnabled(AUDIT));
        System.out.printf("  isEnabled(NOTICE)  %s%n", log.isEnabled(NOTICE));
        System.out.printf("  isEnabled(VERBOSE) %s%n", log.isEnabled(VERBOSE));

        // Re-registering with a DIFFERENT integer. Level.forName does not
        // renumber an existing level and does not complain either — it simply
        // returns the one already registered, so this prints 150, not 999.
        final Level again = Level.forName("AUDIT", 999);
        System.out.printf("  Level.forName(\"AUDIT\", 999) -> intLevel %d  (registration is permanent)%n",
                again.intLevel());
    }

    private static void report(final Level level) {
        System.out.printf("    %-8s %d%n", level.name(), level.intLevel());
    }
}
