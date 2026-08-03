package com.springtest.controller;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class OtelTestController {
    private static final Logger logger = org.apache.logging.log4j.LogManager.getLogger(OtelTestController.class);

    @GetMapping("/test-otel-tracing")
    public String testTracing() {
        // 1. Create a valid W3C standard SpanContext manually (simulating a tracer context)
        final SpanContext mockSpanContext = SpanContext.create(
                "4bf92f3577b34da6a3ce929d0e0e4736", // 16-byte W3C Trace ID
                "00f067aa0ba902b7",                 // 8-byte W3C Span ID
                TraceFlags.getSampled(),            // Trace Flags (01 - Sampled)
                TraceState.getDefault()             // Trace State
        );

        // 2. Wrap it in an active OpenTelemetry Span
        final Span mockSpan = Span.wrap(mockSpanContext);

        // 3. Make the span current (this registers it natively in OTel's ThreadLocal context)
        try (Scope scope = mockSpan.makeCurrent()) {

            // 4. Log message (our SPI will natively extract the values from OTel's active context)
            logger.info("This is a native OpenTelemetry tracing log statement!");

            return "Real OTel tracing log generated successfully!";
        }
    }

    @GetMapping("/benchmark")
    public String benchmark() {
        int iterations = 50_000;

        // ---------------------------------------------------------
        // TEST 1: The Old 2.26.0 Way (ThreadContext / MDC Map)
        // ---------------------------------------------------------
        long mdcStartTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // OTel simulates pushing to MDC
            org.apache.logging.log4j.ThreadContext.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736");
            org.apache.logging.log4j.ThreadContext.put("span_id", "00f067aa0ba902b7");

            logger.info("Benchmarking MDC logging");

            org.apache.logging.log4j.ThreadContext.clearMap();
        }
        long mdcEndTime = System.nanoTime();
        long mdcDurationMs = (mdcEndTime - mdcStartTime) / 50_000;

        // ---------------------------------------------------------
        // TEST 2: The New Native Way (Our TraceContextProvider)
        // ---------------------------------------------------------
        // Setup mock OTel context natively
        io.opentelemetry.api.trace.SpanContext mockSpanContext = io.opentelemetry.api.trace.SpanContext.create(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7",
                io.opentelemetry.api.trace.TraceFlags.getSampled(), io.opentelemetry.api.trace.TraceState.getDefault());
        io.opentelemetry.api.trace.Span mockSpan = io.opentelemetry.api.trace.Span.wrap(mockSpanContext);

        long nativeStartTime = System.nanoTime();
        try (io.opentelemetry.context.Scope scope = mockSpan.makeCurrent()) {
            for (int i = 0; i < iterations; i++) {
                // Native log - Zero ThreadContext interaction!
                logger.info("Benchmarking Native logging");
            }
        }
        long nativeEndTime = System.nanoTime();
        long nativeDurationMs = (nativeEndTime - nativeStartTime) / 50_000;

        // ---------------------------------------------------------
        // RESULTS
        // ---------------------------------------------------------
        return String.format(
                "Benchmark Complete (50,000 logs):<br>" +
                        "Legacy MDC Time: %d ms<br>" +
                        "New Native Time: %d ms<br>" +
                        "Native is %d ms faster!",
                mdcDurationMs, nativeDurationMs, (mdcDurationMs - nativeDurationMs)
        );
    }
    @GetMapping("/benchmark-massive")
    public String benchmarkMassive() {
        int iterations = 10_000_000; // 1 Million Logs!

        System.out.println(">>> STARTING MASSIVE BENCHMARK: " + iterations + " iterations <<<");
        long startTime = System.nanoTime();

        for (int i = 1; i <= iterations; i++) {
            logger.info("Massive benchmark log statement number: {}", i);

            // Visual progress indicator every 100,000 logs
            if (i % 100_000 == 0) {
                System.out.println(">>> Progress: " + i + " logs processed...");
            }
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 10_000_000;

        System.out.println(">>> FINISHED IN " + durationMs + " ms <<<");
        System.out.println("Massive Benchmark Complete (" + iterations + " logs): " + durationMs + " ms");
        return "Massive Benchmark Complete (" + iterations + " logs): " + durationMs + " ms";
    }
}
