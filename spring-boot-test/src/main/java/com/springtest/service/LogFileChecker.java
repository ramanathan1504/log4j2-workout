//package com.springtest.service;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//
//@Component
//public class LogFileChecker {
//    private static final Logger logger = LogManager.getLogger(LogFileChecker.class);
//    private static final org.slf4j.Logger slf4j = LoggerFactory.getLogger(LogFileChecker.class);
//
//    private static final Path LOG_DIR = Paths.get("logs");
//    private static final Path LOG_FILE = LOG_DIR.resolve("spring-boot-app.log");
//    private static final Path JSON_LOG_FILE = LOG_DIR.resolve("spring-boot-app-json.log");
//
//    private final long startedAtMs = System.currentTimeMillis();
//    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor();
//
//    private volatile boolean dirSeen;
//    private volatile boolean appLogSeen;
//    private volatile boolean jsonLogSeen;
//
//    @PostConstruct
//    public void checkLogFileCreation() {
//        logger.info("LogFileChecker started: polling log artifacts for creation timing");
//        slf4j.info("LogFileChecker started (SLF4J bridge)");
//        printState("@PostConstruct initial check");
//
//        watcher.scheduleAtFixedRate(this::pollCreationEvents, 0, 100, TimeUnit.MILLISECONDS);
//        watcher.schedule(() -> {
//            printState("final check (+10s)");
//            watcher.shutdown();
//        }, 10, TimeUnit.SECONDS);
//    }
//
//    @PreDestroy
//    public void shutdownWatcher() {
//        logger.info("LogFileChecker stopping watcher");
//        watcher.shutdownNow();
//    }
//
//    private void pollCreationEvents() {
//        long elapsed = System.currentTimeMillis() - startedAtMs;
//
//        if (!dirSeen && Files.exists(LOG_DIR)) {
//            dirSeen = true;
//            System.out.printf("[LogFileChecker][+%dms] logs/ directory created%n", elapsed);
//        }
//        if (!appLogSeen && Files.exists(LOG_FILE)) {
//            appLogSeen = true;
//            System.out.printf("[LogFileChecker][+%dms] spring-boot-app.log created%n", elapsed);
//        }
//        if (!jsonLogSeen && Files.exists(JSON_LOG_FILE)) {
//            jsonLogSeen = true;
//            System.out.printf("[LogFileChecker][+%dms] spring-boot-app-json.log created%n", elapsed);
//        }
//
//        if (dirSeen && appLogSeen && jsonLogSeen) {
//            printState("all artifacts detected");
//            watcher.shutdown();
//        }
//    }
//
//    private void printState(String phase) {
//        System.out.printf(
//                "[LogFileChecker][%s] dir=%s app.log=%s app-json.log=%s%n",
//                phase,
//                Files.exists(LOG_DIR),
//                Files.exists(LOG_FILE),
//                Files.exists(JSON_LOG_FILE)
//        );
//    }
//
//    public static class JdbcLab {
//        private static final Logger logger = LogManager.getLogger(JdbcLab.class);
//
//        public static void main(String[] args) {
//            System.out.println("Starting SQL Logging Lab...");
//
//            logger.info("SQL Lab Check: Pipeline is open.");
//            logger.warn("SQL Lab Check: Testing persistence at " + java.time.LocalDateTime.now());
//
//            try {
//                throw new Exception("Database Test Exception");
//            } catch (Exception e) {
//                logger.error("SQL Lab Check: Capturing stack trace", e);
//            }
//
//            System.out.println("✅ Check MySQL table now.");
//        }
//    }
//}
