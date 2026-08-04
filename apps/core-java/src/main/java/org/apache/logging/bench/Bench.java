package org.apache.logging.bench;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;

import org.apache.logging.bench.scenario.ExceptionScenario;
import org.apache.logging.bench.scenario.LookupScenario;
import org.apache.logging.bench.scenario.MessageScenario;
import org.apache.logging.bench.scenario.ProgrammaticScenario;
import org.apache.logging.bench.scenario.RolloverScenario;
import org.apache.logging.bench.scenario.ThreadContextScenario;

/**
 * Entry point for the no-framework bench.
 *
 * <pre>
 *   java ... org.apache.logging.bench.Bench                  # run every scenario
 *   java ... org.apache.logging.bench.Bench messages lookups # run named scenarios
 *   java ... org.apache.logging.bench.Bench --list           # index of coverage
 * </pre>
 *
 * <p>Which configuration is in force is controlled entirely from outside, via
 * {@code -Dlog4j.configurationFile=configs/xml/layout-ecs.xml}. That is what lets
 * one set of scenarios run against every config in {@code configs/}.
 */
public final class Bench {

    private static final Map<String, Scenario> SCENARIOS = register(
            new MessageScenario(),
            new LookupScenario(),
            new ThreadContextScenario(),
            new ExceptionScenario(),
            new RolloverScenario(),
            new ProgrammaticScenario());

    public static void main(final String[] args) {
        if (args.length > 0 && ("--list".equals(args[0]) || "list".equals(args[0]))) {
            printIndex();
            return;
        }

        final List<String> requested = args.length == 0 ? List.copyOf(SCENARIOS.keySet()) : List.of(args);

        final List<String> unknown = requested.stream().filter(n -> !SCENARIOS.containsKey(n)).toList();
        if (!unknown.isEmpty()) {
            System.err.println("Unknown scenario(s): " + String.join(", ", unknown));
            printIndex();
            System.exit(2);
        }

        banner();

        int failed = 0;
        for (final String name : requested) {
            final Scenario scenario = SCENARIOS.get(name);
            System.out.printf("%n──── %s ── %s%n", scenario.name(), scenario.describes());
            try {
                scenario.run();
            } catch (final Exception e) {
                // Keep going: one broken scenario must not hide the rest, and a
                // thrown exception here is frequently the bug under investigation.
                failed++;
                System.err.printf("Scenario '%s' failed: %s%n", name, e);
                e.printStackTrace(System.err);
            }
        }

        System.out.printf("%n──── done: %d scenario(s), %d failed%n", requested.size(), failed);

        // Stop Log4j explicitly rather than leaving it to the shutdown hook.
        //
        // RollingFileManager gives every rolling appender its own
        // ScheduledThreadPoolExecutor built from
        // Log4jThreadFactory.createThreadFactory(), which produces NON-daemon
        // threads. The pool is lazy, so it costs nothing until an asynchronous
        // rollover (any compressing one) submits the first task — after which
        // that thread lives until the manager is released.
        //
        // Nothing then ends the process. The JVM will not begin shutting down
        // while a non-daemon thread is alive, and the hook that would stop
        // Log4j and release the managers only runs once shutdown has begun. A
        // config like rollover-full, which rolls six appenders, simply hangs
        // after the last scenario. Shutting down here breaks that cycle, and
        // also guarantees appenders have flushed before the bench reports.
        LogManager.shutdown();

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void banner() {
        System.out.println("Log4j bench");
        System.out.println("  log4j-api    " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("  log4j-core   " + versionOf("org.apache.logging.log4j.core.LoggerContext"));
        System.out.println("  java         " + System.getProperty("java.version"));
        // 2.x reads log4j.configurationFile; 3.x reads log4j.configuration.location
        // and ignores the 2.x name entirely. Report whichever was actually set,
        // so a config passed under the wrong name shows up as "<none>" here
        // rather than as a silent fallback to the default configuration.
        String configLocation = System.getProperty("log4j.configurationFile");
        if (configLocation == null) {
            configLocation = System.getProperty("log4j.configuration.location");
        }
        if (configLocation == null) {
            configLocation = System.getProperty("log4j.configuration");
        }
        System.out.println("  config       "
                + (configLocation == null
                        ? "<none set — default lookup: log4j2.xml on classpath>"
                        : configLocation));

        // Report what Log4j actually loaded, not just what it was asked to load.
        // A config with a missing dependency (Jackson for JSON/YAML, say) is
        // skipped silently and the default configuration takes over — which
        // otherwise looks exactly like the config being applied.
        try {
            final org.apache.logging.log4j.core.LoggerContext ctx =
                    (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            final org.apache.logging.log4j.core.config.Configuration cfg = ctx.getConfiguration();
            System.out.println("  loaded       " + cfg.getName() + "  appenders=" + cfg.getAppenders().keySet());
        } catch (final LinkageError | ClassCastException e) {
            System.out.println("  loaded       <not a log4j-core context: " + e + ">");
        }
    }

    /** Reads the version straight off the JAR manifest, so it reflects what actually loaded. */
    private static String versionOf(final String className) {
        try {
            final Package pkg = Class.forName(className).getPackage();
            final String version = pkg == null ? null : pkg.getImplementationVersion();
            return version == null ? "<unknown — running from classes, not a jar>" : version;
        } catch (final ClassNotFoundException e) {
            return "<absent from classpath>";
        }
    }

    private static void printIndex() {
        System.out.println("Scenarios:");
        SCENARIOS.values()
                .forEach(s -> System.out.printf("  %-14s %s%n", s.name(), s.describes()));
    }

    private static Map<String, Scenario> register(final Scenario... scenarios) {
        final Map<String, Scenario> map = new LinkedHashMap<>();
        for (final Scenario scenario : scenarios) {
            map.put(scenario.name(), scenario);
        }
        return map;
    }

    private Bench() {}
}
