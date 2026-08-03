package com.springtest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring Boot application for real-case Log4j 2 integration testing.
 *
 * REST endpoints (see LogTestController):
 *   GET /api/logs/all-levels   — all log levels + SLF4J bridge
 *   GET /api/logs/mdc          — MDC/ThreadContext with user+traceId params
 *   GET /api/logs/exception    — exception + chained cause logging
 *   GET /api/logs/markers      — SECURITY / AUDIT / PERF markers
 *   GET /api/logs/async        — async thread logging with MDC propagation
 *   GET /api/logs/all          — runs every scenario in one call
 *
 * Actuator endpoints:
 *   GET /actuator/health       — liveness + readiness
 *   GET /actuator/loggers      — list loggers and levels
 *   POST /actuator/loggers/{name} — change log level at runtime
 *
 * Run with async config:
 *   java -Dlog4j.configurationFile=log4j2-async.xml -jar spring-boot-test-1.0-SNAPSHOT.jar
 */
@SpringBootApplication
public class SpringBootTestApp {

    private static final Path LOG_DIR = Paths.get("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("spring-boot-app.log");
    private static final Path JSON_LOG_FILE = LOG_DIR.resolve("spring-boot-app-json.log");

    public static void main(String[] args) {
        try {
            Class<?> clazz = Class.forName("aQute.bnd.annotation.BaselineIgnore");
            System.out.println("DEBUG: Bnd Annotation JAR Location: " +
                    clazz.getProtectionDomain().getCodeSource().getLocation());
        } catch (ClassNotFoundException e) {
            System.out.println("DEBUG: BaselineIgnore class not found on classpath");
        }
        printLogPathState("main-entry (before logger init)");

        Logger logger = LogManager.getLogger(SpringBootTestApp.class);
        printLogPathState("after LogManager.getLogger");

        logger.info("╔══════════════════════════════════════════════════╗");
        printLogPathState("after first log event");
        logger.info("║   Spring Boot — Log4j2 Real App Test             ║");
        logger.info("╠══════════════════════════════════════════════════╣");
        logger.info("║  Log4j  : {}", resolveLog4jVersion());
        logger.info("║  Java   : {}", System.getProperty("java.version"));
        logger.info("║  Config : {}", System.getProperty("log4j.configurationFile", "log4j2.xml (default)"));
        logger.info("╚══════════════════════════════════════════════════╝");

        printLogPathState("after banner log lines");
        SpringApplication.run(SpringBootTestApp.class, args);
        printLogPathState("after SpringApplication.run");

        logger.info("Spring Boot app started — visit http://localhost:8081/api/logs/all");
        printLogPathState("after startup log line");
    }

    private static void printLogPathState(String phase) {
        long now = System.currentTimeMillis();
        System.out.printf(
                "[%d][%s] dir=%s app.log=%s app-json.log=%s%n",
                now,
                phase,
                Files.exists(LOG_DIR),
                Files.exists(LOG_FILE),
                Files.exists(JSON_LOG_FILE)
        );
    }

    private static String resolveLog4jVersion() {
        Package pkg = LogManager.class.getPackage();
        return (pkg != null && pkg.getImplementationVersion() != null)
                ? pkg.getImplementationVersion()
                : "unknown (check log4j.version in pom.xml)";
    }
}
