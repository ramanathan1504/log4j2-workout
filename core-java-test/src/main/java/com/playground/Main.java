package com.playground;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point for the core-java Log4j 2 test suite.
 *
 * Run with:
 *   mvn exec:java                                              (sync, default config)
 *   mvn exec:java -Dlog4j.configurationFile=log4j2-async.xml  (async config)
 *
 * Switch Log4j version:
 *   mvn package exec:java -Dlog4j.version=3.0.0-SNAPSHOT -U
 */
public final class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("cron-test")) {
            int minutes = 10;
            if (args.length > 1) {
                try {
                    minutes = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    logger.warn("[CRON TEST] Invalid duration '{}', using default 10 minutes", args[1]);
                }
            }
            runCronTest(minutes);
            return;
        }
        printBanner();
        LogTestScenarios.testAllLevels();
        LogTestScenarios.testMdcContext();
        LogTestScenarios.testMarkers();
        LogTestScenarios.testParameterizedMessages();
        LogTestScenarios.testExceptionLogging();
        LogTestScenarios.testFluentApi();
        LogTestScenarios.testMultiThread();
        LogTestScenarios.testLargeEventRollover();
        logger.info("═══════════════ All scenarios complete ═══════════════");
    }

    /**
     * Logs every 10 seconds for the given number of minutes to help test CronTriggeringPolicy rollover and DST issues.
     */
    private static void runCronTest(int minutes) {
        logger.info("[CRON TEST] Starting periodic logging for CronTriggeringPolicy test ({} minutes)", minutes);
        int iterations = (minutes * 60) / 10;
        for (int i = 0; i < iterations; i++) {
            logger.info("[CRON TEST] Log event {} at {}", i + 1, java.time.LocalDateTime.now());
            try {
                Thread.sleep(10_000); // 10 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[CRON TEST] Interrupted");
                break;
            }
        }
        logger.info("[CRON TEST] Finished periodic logging");
    }

    private static void printBanner() {
        String log4jVersion = resolveLog4jVersion();
        logger.info("╔═══════════════════════════════════════════════╗");
        logger.info("║     Core Java — Log4j 2 Real-Case Test Suite  ║");
        logger.info("╠═══════════════════════════════════════════════╣");
        logger.info("║  Log4j  : {}", log4jVersion);
        logger.info("║  Java   : {}", System.getProperty("java.version"));
        logger.info("║  Config : {}", System.getProperty("log4j.configurationFile", "log4j2.xml (default)"));
        logger.info("╚═══════════════════════════════════════════════╝");
    }

    private static String resolveLog4jVersion() {
        Package pkg = LogManager.class.getPackage();
        return (pkg != null && pkg.getImplementationVersion() != null)
                ? pkg.getImplementationVersion()
                : "unknown (check pom.xml log4j.version)";
    }
}
