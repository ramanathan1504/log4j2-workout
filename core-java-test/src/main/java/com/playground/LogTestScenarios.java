package com.playground;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real-case Log4j 2 test scenarios — covers every major module feature.
 *
 * Scenarios:
 *   1. All log levels (TRACE → FATAL) + SLF4J bridge
 *   2. MDC / ThreadContext (traceId, userId, env)
 *   3. Markers (SECURITY, PERFORMANCE, AUDIT with parent)
 *   4. Parameterized messages + StructuredDataMessage (RFC 5424)
 *   5. Exception logging (single + chained cause)
 *   6. Fluent API (Log4j 2.4+)
 *   7. Multi-thread logging with per-thread MDC
 */
public final class LogTestScenarios {

    private static final Logger logger     = LogManager.getLogger(LogTestScenarios.class);
    private static final org.slf4j.Logger slf4j = LoggerFactory.getLogger(LogTestScenarios.class);

    // ── Markers ───────────────────────────────────────────────────────────
    private static final Marker SECURITY    = MarkerManager.getMarker("SECURITY");
    private static final Marker PERFORMANCE = MarkerManager.getMarker("PERFORMANCE");
    /** AUDIT inherits SECURITY — log filter on SECURITY will also catch AUDIT */
    private static final Marker AUDIT       = MarkerManager.getMarker("AUDIT").addParents(SECURITY);

    private LogTestScenarios() {}

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 1: All log levels + SLF4J bridge
    // ──────────────────────────────────────────────────────────────────────
    public static void testAllLevels() {
        logger.info("── Scenario 1: All Log Levels ──────────────────────");
        logger.trace("TRACE  — finest detail, disabled in prod");
        logger.debug("DEBUG  — debugging info");
        logger.info ("INFO   — normal operation");
        logger.warn ("WARN   — potential issue, still running");
        logger.error("ERROR  — something failed, needs attention");
        logger.fatal("FATAL  — critical, application may be unusable");

        // Verify SLF4J bridge routes through Log4j
        slf4j.info ("SLF4J  INFO  → routed via log4j-slf4j2-impl");
        slf4j.warn ("SLF4J  WARN  → routed via log4j-slf4j2-impl");
        slf4j.error("SLF4J  ERROR → routed via log4j-slf4j2-impl");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 2: MDC / ThreadContext
    // ──────────────────────────────────────────────────────────────────────
    public static void testMdcContext() {
        logger.info("── Scenario 2: MDC / ThreadContext ─────────────────");
        try {
            ThreadContext.put("traceId", "trace-abc-123");
            ThreadContext.put("userId",  "usr-42");
            ThreadContext.put("env",     "test");

            logger.info ("MDC populated — all fields visible in JSON output");
            logger.debug("Processing business logic for user");
            logger.warn ("MDC context: slow query detected for userId={}", ThreadContext.get("userId"));
        } finally {
            ThreadContext.clearAll(); // always clear to avoid leaking into next log
        }

        logger.info("MDC cleared — no context in this line");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 3: Markers
    // ──────────────────────────────────────────────────────────────────────
    public static void testMarkers() {
        logger.info("── Scenario 3: Markers ─────────────────────────────");
        logger.info (SECURITY,    "SECURITY marker — token validated");
        logger.warn (AUDIT,       "AUDIT marker    — privileged action attempted (inherits SECURITY)");
        logger.info (PERFORMANCE, "PERF marker     — operation took 42ms");
        logger.error(AUDIT,       "AUDIT marker    — access denied for /admin resource");

        // Check marker inheritance
        logger.debug("AUDIT.isInstanceOf(SECURITY) = {}", AUDIT.isInstanceOf(SECURITY));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 4: Parameterized & Structured messages
    // ──────────────────────────────────────────────────────────────────────
    public static void testParameterizedMessages() {
        logger.info("── Scenario 4: Parameterized & Structured Messages ─");

        // Standard {} style — toString() called only if level enabled
        logger.debug("Single param: value={}", "hello");
        logger.info ("Multi  params: user={}, action={}, statusCode={}", "alice", "login", 200);

        // Supplier (lazy) — expensive object built only if DEBUG enabled
        logger.debug("Lazy eval: result={}", (org.apache.logging.log4j.util.Supplier<Object>)
                () -> "expensive-computation-" + System.nanoTime());

        // RFC 5424 Structured Data Message
        StructuredDataMessage sdm = new StructuredDataMessage("login@42", "User login event", "audit");
        sdm.put("user",   "bob");
        sdm.put("ip",     "10.0.0.5");
        sdm.put("result", "success");
        logger.info(sdm);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 5: Exception logging
    // ──────────────────────────────────────────────────────────────────────
    public static void testExceptionLogging() {
        logger.info("── Scenario 5: Exception Logging ───────────────────");

        // Single exception
        try {
            simulateFailure("DB timeout");
        } catch (RuntimeException e) {
            logger.error("Operation failed: {}", e.getMessage(), e);
        }

        // Chained exception (cause chain)
        try {
            throw new IllegalStateException("Service unavailable",
                    new RuntimeException("Connection pool exhausted",
                            new RuntimeException("Root: network unreachable")));
        } catch (Exception e) {
            logger.error("Chained exception — inspect full cause chain", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 6: Fluent API (Log4j 2.4+)
    // ──────────────────────────────────────────────────────────────────────
    public static void testFluentApi() {
        logger.info("── Scenario 6: Fluent API (Log4j 2.4+) ────────────");

        logger.atInfo()
              .withMarker(PERFORMANCE)
              .log("Fluent INFO — method={}, duration={}ms", "testFluentApi", 5);

        logger.atWarn()
              .withThrowable(new IllegalArgumentException("bad input — fluent exception"))
              .log("Fluent WARN with exception attached");

        ThreadContext.put("requestId", "req-fluent-007");
        logger.atError()
              .withMarker(SECURITY)
              .log("Fluent ERROR — SECURITY marker + MDC requestId visible in JSON");
        ThreadContext.clearAll();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 7: Multi-thread logging with independent MDC per thread
    // ──────────────────────────────────────────────────────────────────────
    public static void testMultiThread() {
        logger.info("── Scenario 7: Multi-Thread Logging ───────────────");
        int threadCount = 4;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            // Capture parent MDC snapshot — threads do NOT inherit MDC automatically
            final Map<String, String> parentMdc = Map.of("parentTrace", "trace-parent-" + id);

            pool.submit(() -> {
                try {
                    parentMdc.forEach(ThreadContext::put);
                    ThreadContext.put("workerId", "worker-" + id);

                    for (int j = 0; j < 3; j++) {
                        logger.info("Thread={} iteration={} — workerId visible in MDC", id, j);
                    }
                    logger.debug("Thread={} complete", id);
                } finally {
                    ThreadContext.clearAll();
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Multi-thread wait interrupted", e);
        } finally {
            pool.shutdown();
        }
        logger.info("Multi-thread scenario done — {} threads finished", threadCount);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario 8: Large log event to test rollover boundary
    // ──────────────────────────────────────────────────────────────────────
    public static void testLargeEventRollover() {
        logger.info("── Scenario 8: Large Log Event Rollover ──────────────");
        // Generate a log event just under the 1KB limit
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 900; i++) {
            sb.append('A');
        }
        logger.info("Filler event (900B): {}", sb.toString());
        // Now generate a log event that will push the file over the 1KB limit
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            big.append('B');
        }
        logger.info("Trigger event (600B): {}", big.toString());
        logger.info("After rollover event");
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private static void simulateFailure(String reason) {
        throw new RuntimeException("Simulated failure: " + reason);
    }
}
