package com.springtest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MicroMeterController {

    private static final org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(MicroMeterController.class);

    @GetMapping("/micrometer-test")
    public String micrometerTest() {
        slf4jLogger.info("This is a Micrometer log statement!");
        return "Check the console to see where Micrometer puts the Trace ID!";
    }

    // ---------------------------------------------------------
    // TEST 1: The "Future" Native Way (Zero Garbage)
    // ---------------------------------------------------------
    @GetMapping("/micrometer-benchmark-native")
    public String micrometerBenchmarkNative() {
        int iterations = 1_000_000;
        System.out.println(">>> STARTING MICROMETER NATIVE BENCHMARK <<<");

        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 1. Force clear the MDC to simulate Micrometer turning off MDC injection
            org.apache.logging.log4j.ThreadContext.clearMap();

            // 2. Log the statement. (Your SPI will grab the Trace ID natively!)
            slf4jLogger.info("Benchmarking Micrometer Native Logging");
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        return "Micrometer Native Benchmark Complete (" + iterations + " logs): " + durationMs + " ms";
    }

    // ---------------------------------------------------------
    // TEST 2: The "Old" MDC Way (Heavy Garbage)
    // ---------------------------------------------------------
    @GetMapping("/micrometer-benchmark-mdc")
    public String micrometerBenchmarkMdc() {
        int iterations = 1_000_000;
        System.out.println(">>> STARTING MICROMETER MDC BENCHMARK <<<");

        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // We DO NOT clear the map here!
            // Micrometer will naturally push the Trace ID to the MDC,
            // forcing Log4j to clone the map on every loop.
            slf4jLogger.info("Benchmarking Micrometer MDC Logging");
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        return "Micrometer MDC Benchmark Complete (" + iterations + " logs): " + durationMs + " ms";
    }
}