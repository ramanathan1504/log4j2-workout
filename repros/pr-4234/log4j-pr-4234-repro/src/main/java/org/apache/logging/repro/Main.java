package org.apache.logging.repro;

/**
 * Standalone reproduction for pr #4234.
 *
 * <p>Self-contained: depends only on Log4j itself. Run with
 * {@code mvn -Dlog4j.version=<version> compile exec:java}.
 */
public final class Main {

    public static void main(final String[] args) throws Exception {
        System.out.println("log4j-api  " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("log4j-core " + versionOf("org.apache.logging.log4j.core.LoggerContext"));
        System.out.println("java       " + System.getProperty("java.version"));
        System.out.println();

        new RolloverScenario().run();

        System.out.println();
        System.out.println("Completed without error.");
    }

    private static String versionOf(final String className) {
        try {
            final Package pkg = Class.forName(className).getPackage();
            final String v = pkg == null ? null : pkg.getImplementationVersion();
            return v == null ? "<unknown>" : v;
        } catch (final ClassNotFoundException e) {
            return "<absent>";
        }
    }

    private Main() {}
}
