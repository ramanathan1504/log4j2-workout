package org.apache.logging.bench.spring;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.bench.Scenario;
import org.apache.logging.bench.scenario.ExceptionScenario;
import org.apache.logging.bench.scenario.LookupScenario;
import org.apache.logging.bench.scenario.MessageScenario;
import org.apache.logging.bench.scenario.RolloverScenario;
import org.apache.logging.bench.scenario.ThreadContextScenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives the same scenarios as the core-java bench, but from inside a live
 * servlet request. Worth having separately: request threads come from a pool
 * (so MDC leaks show up here and not in a single-threaded main), and the
 * configuration is loaded by Spring's Log4j integration rather than directly.
 */
@RestController
@RequestMapping("/bench")
public class BenchController {

    private static final Logger log = LogManager.getLogger(BenchController.class);

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();

    public BenchController() {
        for (final Scenario s : new Scenario[] {
                new MessageScenario(),
                new LookupScenario(),
                new ThreadContextScenario(),
                new ExceptionScenario(),
                new RolloverScenario()
        }) {
            scenarios.put(s.name(), s);
        }
    }

    @GetMapping
    public Map<String, String> index() {
        final Map<String, String> index = new LinkedHashMap<>();
        scenarios.forEach((name, s) -> index.put(name, s.describes()));
        return index;
    }

    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable final String name) {
        final Scenario scenario = scenarios.get(name);
        if (scenario == null) {
            return ResponseEntity.notFound().build();
        }

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", name);
        result.put("describes", scenario.describes());

        final long started = System.nanoTime();
        try {
            scenario.run();
            result.put("status", "ok");
        } catch (final Exception e) {
            // Report rather than rethrow: the thrown exception is often the thing
            // under investigation, and a 500 page would hide the detail.
            log.error("Scenario {} failed", name, e);
            result.put("status", "failed");
            result.put("exception", e.getClass().getName());
            result.put("message", String.valueOf(e.getMessage()));
        }
        result.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        return ResponseEntity.ok(result);
    }

    /** Which configuration actually won, and which appenders it produced. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Map<String, Object> info = new LinkedHashMap<>();
        info.put("configurationName", ctx.getConfiguration().getName());
        info.put("configurationSource", String.valueOf(ctx.getConfiguration().getConfigurationSource()));
        info.put("appenders", ctx.getConfiguration().getAppenders().keySet());
        info.put("log4jApiVersion", versionOf("org.apache.logging.log4j.LogManager"));
        info.put("log4jCoreVersion", versionOf("org.apache.logging.log4j.core.LoggerContext"));
        return info;
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
}
