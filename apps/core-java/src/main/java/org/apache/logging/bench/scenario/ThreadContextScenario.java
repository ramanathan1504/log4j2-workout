package org.apache.logging.bench.scenario;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.bench.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;

/**
 * MDC/NDC (ThreadContext) and markers, including the two things that actually
 * break in production: context leaking across pooled threads, and inherited
 * marker hierarchies changing which filters match.
 * Feature matrix §3, §4 ({@code %X}, {@code %x}, {@code %marker}).
 */
public final class ThreadContextScenario implements Scenario {

    private static final Logger log = LogManager.getLogger(ThreadContextScenario.class);

    private static final Marker SECURITY = MarkerManager.getMarker("SECURITY");
    // AUDIT is a child of SECURITY: a MarkerFilter on SECURITY also matches AUDIT.
    private static final Marker AUDIT = MarkerManager.getMarker("AUDIT").addParents(SECURITY);
    private static final Marker PERF = MarkerManager.getMarker("PERF");

    @Override
    public String name() {
        return "context";
    }

    @Override
    public String describes() {
        return "ThreadContext MDC/NDC, marker hierarchies, and context propagation across a thread pool";
    }

    @Override
    public void run() throws InterruptedException {
        // ── MDC: map context, rendered by %X and by the threadContextData resolver
        ThreadContext.put("traceId", "6f1c9a2b4d8e");
        ThreadContext.put("userId", "alice");
        ThreadContext.put("requestId", "req-0001");
        try {
            log.info("MDC populated — %X should carry traceId, userId, requestId");

            // ── NDC: stack context, rendered by %x
            ThreadContext.push("http-request");
            ThreadContext.push("checkout");
            try {
                ThreadContext.push("payment-gateway");
                try {
                    log.info("NDC depth {} — %x should show the full stack", ThreadContext.getDepth());
                } finally {
                    ThreadContext.pop();
                }
                log.info("NDC after pop — payment-gateway is gone");
            } finally {
                ThreadContext.pop();
                ThreadContext.pop();
            }

            // ── Markers, including inheritance
            log.info(SECURITY, "SECURITY marker — matched by a MarkerFilter on SECURITY");
            log.info(AUDIT, "AUDIT marker — ALSO matched by a filter on SECURITY, because AUDIT's parent is SECURITY");
            log.info(PERF, "PERF marker — not matched by a SECURITY filter");
            log.info("No marker at all");

            // ── Propagation across a pool: the copy must be taken on the calling
            // thread and re-applied inside the task, and cleared in a finally.
            propagateAcrossPool();
        } finally {
            ThreadContext.clearAll();
        }

        log.info("ThreadContext cleared — %X and %x are now empty");
    }

    private void propagateAcrossPool() throws InterruptedException {
        final Map<String, String> caller = ThreadContext.getImmutableContext();
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 4; i++) {
                final int task = i;
                pool.submit(() -> {
                    ThreadContext.putAll(caller);
                    ThreadContext.put("task", String.valueOf(task));
                    try {
                        log.info("Pooled task {} — inherited traceId from the caller", task);
                    } finally {
                        // Without this the next task on this thread inherits stale
                        // MDC. This is the single most common MDC bug in real apps.
                        ThreadContext.clearAll();
                    }
                });
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
