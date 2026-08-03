package com.springtest.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.CronTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real-case Log4j 2 logging scenarios inside a Spring-managed service.
 *
 * Tests covered:
 *   - All log levels (TRACE → FATAL) + SLF4J bridge via Spring context
 *   - MDC / ThreadContext — traceId, userId, service name
 *   - Markers — SECURITY, AUDIT (inherits SECURITY), PERFORMANCE
 *   - Exception logging — single and chained cause
 *   - Async logging — MDC captured and propagated to background thread
 *   - Fluent API — atInfo/atWarn/atError with markers and exceptions
 */
@Service
public class LogService {

    private static final String ISSUE_2073_CRON_SCHEDULE = "0/1 0/1 * 1/1 * ? *";
    private static final Path ISSUE_2073_LOG_DIR = Paths.get("logs", "issue-2073");

    private static final Logger logger    = LogManager.getLogger(LogService.class);
    private static final org.slf4j.Logger slf4j = LoggerFactory.getLogger(LogService.class);

    // ── Markers ───────────────────────────────────────────────────────────
    private static final Marker SECURITY    = MarkerManager.getMarker("SECURITY");
    private static final Marker PERFORMANCE = MarkerManager.getMarker("PERFORMANCE");
    /** AUDIT inherits SECURITY — any filter on SECURITY also catches AUDIT */
    private static final Marker AUDIT       = MarkerManager.getMarker("AUDIT").addParents(SECURITY);

    // ─────────────────────────────────────────────────────────────────────
    // All log levels + SLF4J bridge
    // ─────────────────────────────────────────────────────────────────────
    public void logAllLevels(String requestId) {
        ThreadContext.put("requestId", requestId);
        try {
            logger.trace("TRACE  — request received, entering pipeline");
            logger.debug("DEBUG  — input parameters validated for requestId={}", requestId);
            logger.info ("INFO   — processing started for requestId={}", requestId);
            logger.warn ("WARN   — response time approaching SLA threshold");
            logger.error("ERROR  — downstream service returned 503");
            logger.fatal("FATAL  — circuit breaker OPEN, all traffic rejected");

            // SLF4J messages — verify bridge routes through Log4j
            slf4j.info ("SLF4J INFO  — bridged via log4j-slf4j2-impl");
            slf4j.warn ("SLF4J WARN  — bridged via log4j-slf4j2-impl");
            slf4j.error("SLF4J ERROR — bridged via log4j-slf4j2-impl");
        } finally {
            ThreadContext.clearAll();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MDC / ThreadContext
    // ─────────────────────────────────────────────────────────────────────
    public void logWithMdc(String user, String traceId) {
        ThreadContext.put("user",    user);
        ThreadContext.put("traceId", traceId);
        ThreadContext.put("service", "spring-boot-log4j2-test");
        try {
            logger.info ("Inbound HTTP request: user={} traceId={}", user, traceId);
            logger.debug("Validating JWT token for user={}", user);
            logger.info ("Business logic executed — record saved");
            logger.warn ("Rate limit at 80% for user={}", user);
            logger.debug("MDC context propagates to all loggers in this thread");
        } finally {
            ThreadContext.clearAll();   // must always clear — thread pool reuse
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Exception logging
    // ─────────────────────────────────────────────────────────────────────
    public void logException() {
        // Single exception with message
        try {
            simulateDbFailure();
        } catch (RuntimeException e) {
            logger.error("DB call failed: {}", e.getMessage(), e);
        }

        // Chained exception — full cause chain in JSON stacktrace field
        try {
            throw new IllegalStateException("Order service unavailable",
                    new RuntimeException("HTTP client timeout",
                            new RuntimeException("Root: DNS resolution failed for order-svc.prod.svc")));
        } catch (Exception e) {
            logger.error("Chained exception — check full cause chain in JSON output", e);
        }

        // Fluent API with exception
        try {
            throw new UnsupportedOperationException("Feature not yet implemented");
        } catch (Exception e) {
            logger.atError()
                  .withMarker(SECURITY)
                  .withThrowable(e)
                  .log("Fluent ERROR with SECURITY marker and exception");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Marker-based logging
    // ─────────────────────────────────────────────────────────────────────
    public void logWithMarkers() {
        logger.info (SECURITY,    "SECURITY — JWT token validated for incoming request");
        logger.warn (AUDIT,       "AUDIT    — User accessed /admin/reports (AUDIT inherits SECURITY)");
        logger.info (PERFORMANCE, "PERF     — DB query completed in 12ms (p99: 180ms)");
        logger.error(AUDIT,       "AUDIT    — Access DENIED: user lacks ROLE_ADMIN");
        logger.atWarn()
              .withMarker(PERFORMANCE)
              .log("Fluent PERF warn — cache miss rate=42%");

        // Prove inheritance
        logger.debug("AUDIT.isInstanceOf(SECURITY) = {}", AUDIT.isInstanceOf(SECURITY));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Async logging with trace-context propagation experiments
    // ─────────────────────────────────────────────────────────────────────
    public void logAsync() {
        String traceId = "trace-" + System.currentTimeMillis();
        String spanId = "span-" + System.nanoTime();
        logAsyncWithTraceContext(traceId, spanId, true);
    }

    public CompletableFuture<Void> logAsyncWithTraceContext(String traceId, String spanId, boolean propagateContext) {
        String asyncId = "async-" + System.currentTimeMillis();
        ThreadContext.put("asyncId", asyncId);
        ThreadContext.put("trace_id", traceId);
        ThreadContext.put("span_id", spanId);

        // Snapshot MDC before handing off to async thread.
        final Map<String, String> mdcSnapshot = new HashMap<>(ThreadContext.getImmutableContext());
        ThreadContext.clearAll();

        return CompletableFuture.runAsync(() -> {
            if (propagateContext) {
                mdcSnapshot.forEach(ThreadContext::put);
            }
            try {
                logger.info("Async task STARTED on thread={} propagateContext={}",
                        Thread.currentThread().getName(), propagateContext);
                logger.debug("Async processing in progress — asyncId={}", mdcSnapshot.get("asyncId"));
                logger.info("Async trace context check trace_id={} span_id={}",
                        ThreadContext.get("trace_id"), ThreadContext.get("span_id"));
                Thread.sleep(30);
                logger.info("Async task COMPLETED — asyncId={}", mdcSnapshot.get("asyncId"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Async task INTERRUPTED", e);
            } finally {
                ThreadContext.clearAll();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Issue #2073 reproduction: ConfigurationScheduler cron race
    // ─────────────────────────────────────────────────────────────────────
    public Map<String, Object> reproduceIssue2073(int iterationsPerWorker, int workers) {
        int safeIterations = Math.max(1, Math.min(iterationsPerWorker, 2_000));
        int safeWorkers = Math.max(1, Math.min(workers, 8));

        logger.info("Issue #2073 reproduction starting iterationsPerWorker={} workers={} schedule={}",
                safeIterations, safeWorkers, ISSUE_2073_CRON_SCHEDULE);

        try {
            Files.createDirectories(ISSUE_2073_LOG_DIR);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create issue-2073 log directory", e);
        }

        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();
        ExecutorService pool = Executors.newFixedThreadPool(safeWorkers);
        CountDownLatch latch = new CountDownLatch(safeWorkers);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();

        long startedAt = System.nanoTime();
        for (int worker = 0; worker < safeWorkers; worker++) {
            final int workerId = worker;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < safeIterations && firstFailure.get() == null; i++) {
                        attempts.incrementAndGet();
                        RollingFileAppender appender = null;
                        try {
                            alignNearNextSecondBoundary();
                            appender = buildIssue2073Appender(configuration, workerId, i);
                            appender.start();
                            successes.incrementAndGet();
                        } catch (Throwable t) {
                            firstFailure.compareAndSet(null, t);
                            logger.error("Issue #2073 reproduction failed on worker={} iteration={}", workerId, i, t);
                        } finally {
                            stopQuietly(appender);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            firstFailure.compareAndSet(null, e);
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        Throwable failure = firstFailure.get();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", "2073");
        result.put("description", "ConfigurationScheduler cron race while repeatedly creating RollingFileAppender instances with a per-second schedule");
        result.put("log4jVersion", resolveLog4jVersion());
        result.put("schedule", ISSUE_2073_CRON_SCHEDULE);
        result.put("workers", safeWorkers);
        result.put("iterationsPerWorker", safeIterations);
        result.put("attempts", attempts.get());
        result.put("successfulBuilds", successes.get());
        result.put("durationMs", durationMs);
        result.put("logDir", ISSUE_2073_LOG_DIR.toAbsolutePath().toString());

        if (failure == null) {
            result.put("status", "not-reproduced");
            result.put("summary", "No exception was observed in this run against the current local Log4j version");
        } else {
            result.put("status", isIssue2073Failure(failure) ? "reproduced" : "failed-with-other-exception");
            result.put("exceptionType", failure.getClass().getName());
            result.put("message", String.valueOf(failure.getMessage()));
            result.put("stackTrace", stackTraceLines(failure, 18));
        }

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void simulateDbFailure() {
        throw new RuntimeException("Simulated: connection to postgres:5432 refused");
    }

    private RollingFileAppender buildIssue2073Appender(Configuration configuration, int workerId, int iteration) {
        String appenderId = "issue-2073-" + workerId + '-' + iteration + '-' + System.nanoTime();
        Path activeFile = ISSUE_2073_LOG_DIR.resolve(appenderId + ".log");
        Path filePattern = ISSUE_2073_LOG_DIR.resolve(appenderId + "-%d{yyyy-MM-dd-HH-mm-ss}-%i.log.gz");

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(configuration)
                .withPattern("%d{ISO8601} %-5level %logger - %msg%n")
                .build();

        TriggeringPolicy policy = CompositeTriggeringPolicy.createPolicy(
                CronTriggeringPolicy.createPolicy(configuration, "false", ISSUE_2073_CRON_SCHEDULE),
                SizeBasedTriggeringPolicy.createPolicy("1 KB")
        );

        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                .withConfig(configuration)
                .withMax("2")
                .build();

        return RollingFileAppender.newBuilder()
                .withName(appenderId)
                .withConfiguration(configuration)
                .withFileName(activeFile.toString())
                .withFilePattern(filePattern.toString())
                .withPolicy(policy)
                .withStrategy(strategy)
                .withLayout(layout)
                .withAppend(true)
                .withBufferedIo(false)
                .withImmediateFlush(true)
                .withCreateOnDemand(true)
                .withIgnoreExceptions(false)
                .build();
    }

    private void alignNearNextSecondBoundary() {
        long remainder = System.currentTimeMillis() % 1_000L;
        long target = 975L;
        if (remainder < target) {
            try {
                Thread.sleep(target - remainder);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopQuietly(RollingFileAppender appender) {
        if (appender == null) {
            return;
        }
        try {
            appender.stop();
        } catch (Exception e) {
            logger.debug("Ignoring appender stop failure for {}", appender.getName(), e);
        }
    }

    private boolean isIssue2073Failure(Throwable throwable) {
        if (!(throwable instanceof NullPointerException)) {
            return false;
        }
        return stackTraceLines(throwable, 30).stream()
                .anyMatch(line -> line.contains("ConfigurationScheduler$CronRunnable.toString")
                        || line.contains("ConfigurationScheduler.toString")
                        || line.contains("CronTriggeringPolicy.initialize"));
    }

    private List<String> stackTraceLines(Throwable throwable, int maxLines) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String[] lines = stringWriter.toString().split("\\R");
        List<String> trimmed = new ArrayList<>();
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            trimmed.add(lines[i]);
        }
        return trimmed;
    }

    private String resolveLog4jVersion() {
        Package pkg = LogManager.class.getPackage();
        return (pkg != null && pkg.getImplementationVersion() != null)
                ? pkg.getImplementationVersion()
                : "unknown";
    }

    public static class MongoAppenderLab {
        private static final Logger logger = LogManager.getLogger(MongoAppenderLab.class);

        public static void main(String[] args) {
            System.out.println("--- Starting Database Logging Lab ---");

            // We add some "Context" data. Log4j MongoDB appender will
            // automatically save these as separate fields in the DB!
            ThreadContext.put("userId", "user-" + UUID.randomUUID().toString().substring(0,8));
            ThreadContext.put("labModule", "lab-db-logging");

            logger.info("First log: Successfully connected to the Log4j Playground.");
            logger.warn("Warning log: Testing how Mongo handles levels.");

            try {
                throw new RuntimeException("Simulated Database Error");
            } catch (Exception e) {
                // This will save the full stack trace into a field in MongoDB
                logger.error("Error log: Something went wrong in the lab!", e);
            }

            ThreadContext.clearAll();
            System.out.println("--- Lab Finished: Check your MongoDB container ---");
        }
    }
}
