package org.apache.logging.bench.java8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFormatMessage;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.message.ThreadDumpMessage;

/**
 * The bench, restricted to Java 8.
 *
 * <p>Log4j 2 supports Java 8 and a large share of upstream bug reports come from
 * applications still running it, but every other module here is compiled at
 * release 17 and cannot even load on a Java 8 JVM. This one exists so the oldest
 * supported JDK is a real column in the matrix rather than an assumption.
 *
 * <p><strong>Java 8 source only.</strong> No {@code var}, no text blocks, no
 * pattern matching, and none of {@code List.of}, {@code Map.of},
 * {@code String.repeat}, {@code Files.readString} or {@code Stream.toList} —
 * all of which are Java 9 or later and would compile here only to fail on the
 * JVM this module exists to test. {@code maven.compiler.release=8} enforces it.
 *
 * <pre>
 *   ./bench run java8-baseline --java 8 --config xml/baseline-console
 *   ./bench matrix --apps java8-baseline --javas 8,17,21,22
 * </pre>
 */
public final class Java8Bench {

    private static final Logger log = LogManager.getLogger(Java8Bench.class);

    private static final Marker AUDIT = MarkerManager.getMarker("AUDIT");
    private static final Marker SECURITY = MarkerManager.getMarker("SECURITY").addParents(AUDIT);

    public static void main(final String[] args) {
        banner();

        messages();
        context();
        exceptions();
        levels();

        // Same reason as the core-java bench: RollingFileManager's async
        // executor threads are non-daemon, so a compressing rollover would
        // otherwise leave the JVM unable to start shutting down.
        LogManager.shutdown();
        System.out.println();
        System.out.println("Java 8 baseline bench complete.");
    }

    private static void banner() {
        System.out.println("Log4j bench — Java 8 baseline");
        System.out.println("  log4j-api    " + versionOf("org.apache.logging.log4j.LogManager"));
        System.out.println("  log4j-core   " + versionOf("org.apache.logging.log4j.core.LoggerContext"));
        System.out.println("  java         " + System.getProperty("java.version")
                + "  (class file target: 8)");

        String configLocation = System.getProperty("log4j.configurationFile");
        if (configLocation == null) {
            configLocation = System.getProperty("log4j.configuration.location");
        }
        System.out.println("  config       "
                + (configLocation == null ? "<none set>" : configLocation));

        // What Log4j actually loaded, not what it was asked to load. A config
        // skipped for a missing dependency looks exactly like one applied.
        try {
            final org.apache.logging.log4j.core.LoggerContext ctx =
                    (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            final org.apache.logging.log4j.core.config.Configuration cfg = ctx.getConfiguration();
            System.out.println("  loaded       " + cfg.getName()
                    + "  appenders=" + cfg.getAppenders().keySet());
        } catch (final LinkageError e) {
            System.out.println("  loaded       <not a log4j-core context: " + e + ">");
        } catch (final ClassCastException e) {
            System.out.println("  loaded       <not a log4j-core context: " + e + ">");
        }
        System.out.println();
    }

    private static String versionOf(final String className) {
        try {
            final Package pkg = Class.forName(className).getPackage();
            final String version = pkg == null ? null : pkg.getImplementationVersion();
            return version == null ? "<unknown — running from classes, not a jar>" : version;
        } catch (final ClassNotFoundException e) {
            return "<absent from classpath>";
        }
    }

    /** Every Message type that exists on the 2.x Java 8 baseline. */
    private static void messages() {
        System.out.println("──── messages");

        // The cast is required, not decorative: SimpleMessage implements both
        // Message and CharSequence, and Logger overloads info() for each, so the
        // unqualified call does not compile at all. Same for MapMessage below.
        log.info((Message) new SimpleMessage("SimpleMessage — no formatting at all"));
        log.info(new ParameterizedMessage("ParameterizedMessage — user {} order {}",
                new Object[] {"alice", Integer.valueOf(4711)}));
        log.info("Brace form — user {} order {}", "alice", Integer.valueOf(4711));
        log.info(new StringFormattedMessage("StringFormattedMessage — %s scored %.2f",
                "bob", Double.valueOf(93.5)));
        log.info(new MessageFormatMessage("MessageFormatMessage — {0} at {1,number,#}",
                "carol", Integer.valueOf(42)));
        log.info(new FormattedMessage("FormattedMessage — %s then {}", "dave"));
        log.info(new ObjectMessage(Arrays.asList("ObjectMessage", "over", "a", "List")));

        final Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("event", "checkout");
        fields.put("basket", "17");
        log.info(new StringMapMessage(fields));

        // Declared as Message, not MapMessage: a wildcard MapMessage<?,?> makes
        // log.info() ambiguous, since MapMessage satisfies both the Message and
        // the CharSequence overload.
        final Message map = new StringMapMessage(new HashMap<String, String>(fields));
        log.info(map);

        final StructuredDataMessage sd =
                new StructuredDataMessage("order-4711", "order placed", "audit");
        sd.put("customer", "alice");
        sd.put("total", "20.00");
        log.info(SECURITY, sd);

        log.info(new ThreadDumpMessage("ThreadDumpMessage — every live thread"));

        // Java 8 lambdas as suppliers: the argument is only evaluated if the
        // level is enabled, which is the whole point of the form.
        log.debug("Supplier form — computed only when DEBUG is on: {}",
                new org.apache.logging.log4j.util.Supplier<String>() {
                    @Override
                    public String get() {
                        return expensive();
                    }
                });
        log.debug(() -> "Lambda form — " + expensive());

        // Flow tracing
        log.traceEntry("entering with {}", "a-parameter");
        log.traceExit("a-result");
    }

    private static String expensive() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append("chunk").append(i).append(' ');
        }
        return sb.toString().trim();
    }

    /** MDC, NDC and marker hierarchies. */
    private static void context() {
        System.out.println("──── context");

        ThreadContext.put("traceId", "java8-0001");
        ThreadContext.put("tenant", "acme-corp");
        ThreadContext.put("userId", "alice");
        ThreadContext.push("outer");
        ThreadContext.push("inner");
        try {
            log.info("MDC and NDC set — %X and %x should render them");
            log.info(SECURITY, "SECURITY marker, whose parent is AUDIT");
            log.info(AUDIT, "AUDIT marker directly");
            System.out.println("  MDC          " + ThreadContext.getImmutableContext());
            System.out.println("  NDC depth    " + ThreadContext.getDepth());
            System.out.println("  SECURITY isInstanceOf(AUDIT)  " + SECURITY.isInstanceOf(AUDIT));
        } finally {
            ThreadContext.clearAll();
        }
    }

    /** The throwable shapes that break renderers. */
    private static void exceptions() {
        System.out.println("──── exceptions");

        log.error("Plain throwable", new IllegalStateException("synthetic failure"));

        final Exception root = new java.io.IOException("disk went away");
        final Exception middle = new RuntimeException("while saving order", root);
        log.error("Nested causes", new IllegalStateException("checkout failed", middle));

        final Exception suppressing = new RuntimeException("primary failure");
        suppressing.addSuppressed(new IllegalArgumentException("suppressed one"));
        suppressing.addSuppressed(new IllegalStateException("suppressed two"));
        log.error("Suppressed exceptions", suppressing);

        // A cycle. Renderers that walk getCause() without tracking what they
        // have already seen loop here until the stack runs out.
        final Exception a = new RuntimeException("cycle-a");
        final Exception b = new RuntimeException("cycle-b", a);
        try {
            a.initCause(b);
        } catch (final IllegalStateException expected) {
            // initCause refuses once a cause is set; the constructor form above
            // already made b -> a, so this only ever completes the loop on JVMs
            // that permit it. Either way the event below is still worth logging.
        }
        log.error("Circular cause chain", b);

        final List<String> deep = new ArrayList<String>();
        deep.add("recursion");
        log.error("Deep stack", deepStack(60));
    }

    private static Exception deepStack(final int depth) {
        if (depth <= 0) {
            return new IllegalStateException("bottom of a deep stack");
        }
        return deepStack(depth - 1);
    }

    /** Level ordering and the isEnabled guards. */
    private static void levels() {
        System.out.println("──── levels");

        log.trace("TRACE");
        log.debug("DEBUG");
        log.info("INFO");
        log.warn("WARN");
        log.error("ERROR");
        log.fatal("FATAL");

        System.out.println("  isTraceEnabled  " + log.isTraceEnabled());
        System.out.println("  isDebugEnabled  " + log.isDebugEnabled());
        System.out.println("  isInfoEnabled   " + log.isInfoEnabled());
        System.out.println("  level           " + log.getLevel());
    }

    private Java8Bench() {}
}
