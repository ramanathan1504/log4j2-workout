package org.apache.logging.bench.scenario;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.bench.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Throwable rendering — the area that generates most of Log4j's real bug reports.
 * Covers {@code %ex}, {@code %xEx}, {@code %rEx} and the JsonTemplateLayout
 * {@code exception}/{@code exceptionRootCause} resolvers.
 *
 * <p>The last three cases are pathological on purpose. They are the shapes behind
 * the open issues around {@code ThrowableProxy} / {@code ThrowableStackTraceRenderer}:
 * throwables whose {@code equals}/{@code hashCode} misbehave defeat the renderer's
 * identity-keyed caching and its common-frame arithmetic.
 */
public final class ExceptionScenario implements Scenario {

    private static final Logger log = LogManager.getLogger(ExceptionScenario.class);

    @Override
    public String name() {
        return "exceptions";
    }

    @Override
    public String describes() {
        return "Throwable rendering: nested causes, suppressed, circular, deep stacks, colliding equals, mutating hashCode";
    }

    @Override
    public void run() {
        simple();
        nestedCauses();
        suppressed();
        circular();
        deepStack();
        collidingEquals();
        mutatingHashCode();
    }

    private void simple() {
        log.error("Plain throwable, no cause", new IllegalArgumentException("bad input"));
    }

    private void nestedCauses() {
        final Throwable root = new IOException("disk unavailable");
        final Throwable mid = new IllegalStateException("repository unreachable", root);
        final Throwable top = new RuntimeException("checkout failed", mid);
        // %xEx prints the full chain; %rEx prints root-cause-first; %ex just the top.
        log.error("Three-deep cause chain — exercises common-frame elision ('... N more')", top);
    }

    private void suppressed() {
        final Exception primary = new IllegalStateException("primary failure");
        primary.addSuppressed(new IOException("suppressed: close() failed"));
        primary.addSuppressed(new IllegalArgumentException("suppressed: rollback() failed"));
        log.error("Throwable carrying two suppressed exceptions", primary);
    }

    private void circular() {
        final Exception a = new RuntimeException("A");
        final Exception b = new RuntimeException("B", a);
        // a.initCause(b) would throw; build the cycle the way real code accidentally does
        try {
            a.initCause(b);
        } catch (final IllegalStateException expected) {
            // initCause refuses once a cause is set — fall back to a self-referential shape
        }
        log.error("Cause chain that revisits an earlier throwable", b);
    }

    private void deepStack() {
        try {
            recurse(120);
        } catch (final Exception e) {
            log.error("Deep stack (120 frames) — stresses common-frame counting", e);
        }
    }

    private void recurse(final int depth) {
        if (depth == 0) {
            throw new IllegalStateException("bottom of a 120-frame stack");
        }
        recurse(depth - 1);
    }

    /**
     * Two distinct throwables that report themselves equal. Any renderer using a
     * {@code HashMap} keyed by throwable will conflate them.
     */
    private void collidingEquals() {
        final RuntimeException inner = new CollidingException("collision", null);
        final RuntimeException outer = new CollidingException("collision", inner);
        log.error("Cause chain whose links claim to be equal to each other", outer);
    }

    /**
     * A throwable whose {@code hashCode()} changes on every call, violating the
     * contract. A {@code HashMap} stores it in one bucket and looks it up in
     * another, so the lookup returns null — the shape behind the reported NPE in
     * {@code ThrowableStackTraceRenderer}. An {@code IdentityHashMap} is immune.
     */
    private void mutatingHashCode() {
        final RuntimeException unstable = new MutatingHashCodeException("hashCode changes on every call");
        log.error("Throwable with an unstable hashCode", unstable);
    }

    /** Distinct instances, equal by message. */
    static final class CollidingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CollidingException(final String message, final Throwable cause) {
            super(message, cause);
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof CollidingException other
                    && Objects.equals(getMessage(), other.getMessage());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getMessage());
        }
    }

    /** Never returns the same hash twice. */
    static final class MutatingHashCodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger counter = new AtomicInteger();

        MutatingHashCodeException(final String message) {
            super(message);
        }

        @Override
        public int hashCode() {
            return counter.incrementAndGet();
        }
    }
}
