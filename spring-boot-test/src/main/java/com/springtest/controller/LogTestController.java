//package com.springtest.controller;
//
//import com.springtest.service.LogService;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.apache.logging.log4j.ThreadContext;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * REST controller — each endpoint triggers a distinct Log4j 2 test scenario.
// *
// * Quick test all at once:
// *   curl http://localhost:8081/api/logs/all
// *
// * Individual scenarios:
// *   curl http://localhost:8081/api/logs/all-levels
// *   curl "http://localhost:8081/api/logs/mdc?user=alice&traceId=trace-001"
// *   curl http://localhost:8081/api/logs/exception
// *   curl http://localhost:8081/api/logs/markers
// *   curl http://localhost:8081/api/logs/async
// *   curl "http://localhost:8081/api/logs/async-trace?propagateContext=false"
// *   curl http://localhost:8081/api/logs/info
// */
//@RestController
//@RequestMapping("/api/logs")
//public class LogTestController {
//
//    private static final Logger logger = LogManager.getLogger(LogTestController.class);
//
//    private final LogService logService;
//
//    public LogTestController(LogService logService) {
//        this.logService = logService;
//    }
//
//    // ── All log levels + SLF4J bridge ─────────────────────────────────────
//    @GetMapping("/all-levels")
//    public ResponseEntity<Map<String, Object>> allLevels() {
//        String requestId = UUID.randomUUID().toString();
//        logger.info("→ /all-levels — requestId={}", requestId);
//        logService.logAllLevels(requestId);
//        return ok("all-levels", requestId);
//    }
//
//    // ── MDC / ThreadContext ────────────────────────────────────────────────
//    @GetMapping("/mdc")
//    public ResponseEntity<Map<String, Object>> mdc(
//            @RequestParam(defaultValue = "test-user") String user,
//            @RequestParam(defaultValue = "trace-001") String traceId) {
//
//        // Controller-level MDC — visible in all log lines for this request
//        ThreadContext.put("requestId", UUID.randomUUID().toString());
//        try {
//            logger.info("→ /mdc — user={} traceId={}", user, traceId);
//            logService.logWithMdc(user, traceId);
//        } finally {
//            ThreadContext.clearAll();
//        }
//
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("status",  "done");
//        body.put("scenario","mdc");
//        body.put("user",    user);
//        body.put("traceId", traceId);
//        return ResponseEntity.ok(body);
//    }
//
//    // ── Exception logging ─────────────────────────────────────────────────
//    @GetMapping("/exception")
//    public ResponseEntity<Map<String, Object>> exception() {
//        logger.info("→ /exception");
//        logService.logException();
//        return ok("exception", null);
//    }
//
//    // ── Marker-based logging ──────────────────────────────────────────────
//    @GetMapping("/markers")
//    public ResponseEntity<Map<String, Object>> markers() {
//        logger.info("→ /markers");
//        logService.logWithMarkers();
//        return ok("markers", null);
//    }
//
//    // ── Async logging ─────────────────────────────────────────────────────
//    @GetMapping("/async")
//    public ResponseEntity<Map<String, Object>> async() {
//        logger.info("→ /async — task queued, response returns immediately");
//        logService.logAsync();
//        return ok("async", null);
//    }
//
//    @GetMapping("/async-trace")
//    public ResponseEntity<Map<String, Object>> asyncTrace(
//            @RequestParam(defaultValue = "true") boolean propagateContext,
//            @RequestParam(required = false) String traceId,
//            @RequestParam(required = false) String spanId) {
//
//        String effectiveTraceId = (traceId == null || traceId.isBlank())
//                ? "trace-" + UUID.randomUUID().toString().replace("-", "")
//                : traceId;
//        String effectiveSpanId = (spanId == null || spanId.isBlank())
//                ? "span-" + UUID.randomUUID().toString().replace("-", "")
//                : spanId;
//
//        logger.info("→ /async-trace — propagateContext={} traceId={} spanId={}",
//                propagateContext, effectiveTraceId, effectiveSpanId);
//
//        // Join so the HTTP response aligns with the corresponding async log lines.
//        logService.logAsyncWithTraceContext(effectiveTraceId, effectiveSpanId, propagateContext).join();
//
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("status", "done");
//        body.put("scenario", "async-trace");
//        body.put("propagateContext", propagateContext);
//        body.put("traceId", effectiveTraceId);
//        body.put("spanId", effectiveSpanId);
//        return ResponseEntity.ok(body);
//    }
//
//    // ── Info endpoint (Log4j version + Java version) ──────────────────────
//    @GetMapping("/info")
//    public ResponseEntity<Map<String, Object>> info() {
//        Package pkg = LogManager.class.getPackage();
//        String log4jVersion = (pkg != null && pkg.getImplementationVersion() != null)
//                ? pkg.getImplementationVersion() : "unknown";
//
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("log4jVersion",  log4jVersion);
//        body.put("javaVersion",   System.getProperty("java.version"));
//        body.put("log4jConfig",   System.getProperty("log4j.configurationFile", "log4j2.xml (default)"));
//        body.put("springProfile", System.getProperty("spring.profiles.active", "default"));
//        return ResponseEntity.ok(body);
//    }
//
//    @GetMapping("/issue-2073")
//    public ResponseEntity<Map<String, Object>> issue2073(
//            @RequestParam(defaultValue = "300") int iterationsPerWorker,
//            @RequestParam(defaultValue = "4") int workers) {
//
//        logger.info("→ /issue-2073 — iterationsPerWorker={} workers={}", iterationsPerWorker, workers);
//        Map<String, Object> result = logService.reproduceIssue2073(iterationsPerWorker, workers);
//        return ResponseEntity.ok(result);
//    }
//
//    // ── Run ALL scenarios at once ─────────────────────────────────────────
//    @GetMapping("/all")
//    public ResponseEntity<Map<String, Object>> all() {
//        String requestId = UUID.randomUUID().toString();
//        logger.info("→ /all — running every scenario requestId={}", requestId);
//
//        logService.logAllLevels(requestId);
//        logService.logWithMdc("bulk-user", requestId);
//        logService.logException();
//        logService.logWithMarkers();
//        logService.logAsync();
//
//        logger.info("← /all — all scenarios dispatched requestId={}", requestId);
//        return ok("all", requestId);
//    }
//
//    // ── Helpers ───────────────────────────────────────────────────────────
//    private ResponseEntity<Map<String, Object>> ok(String scenario, String requestId) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("status",   "done");
//        body.put("scenario", scenario);
//        if (requestId != null) body.put("requestId", requestId);
//        return ResponseEntity.ok(body);
//    }
//}
//
