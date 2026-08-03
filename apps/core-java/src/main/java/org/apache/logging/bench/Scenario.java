package org.apache.logging.bench;

/**
 * One runnable slice of Log4j's feature surface.
 *
 * <p>Scenarios are deliberately self-describing: {@code ./bench list} prints
 * {@link #name()} and {@link #describes()} for every registered scenario, so the
 * bench doubles as an index of what is actually covered.
 */
public interface Scenario {

    /** Short kebab-case identifier used on the command line. */
    String name();

    /** What part of the feature matrix this exercises. */
    String describes();

    /** Run it. Configuration comes from {@code -Dlog4j.configurationFile}. */
    void run() throws Exception;

    /**
     * Log4j lines this scenario applies to. Scenarios covering artifacts absent
     * from 3.x (log4j-1.2-api, log4j-jcl, log4j-web) override this so the matrix
     * runner skips rather than fails them.
     */
    default boolean supports(String log4jVersion) {
        return true;
    }
}
