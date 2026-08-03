package org.apache.logging.bench;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void banner() {
        System.out.println("Log4j bench");
        System.out.println("  log4j-api    " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("  log4j-core   " + versionOf("org.apache.logging.log4j.core.LoggerContext"));
        System.out.println("  java         " + System.getProperty("java.version"));
        System.out.println("  config       "
                + System.getProperty("log4j.configurationFile", "<default lookup: log4j2.xml on classpath>"));
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
