package org.apache.logging.bench.web;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.startup.Tomcat;

/**
 * Boots embedded Tomcat so the servlet integration can be exercised without an
 * external container. Feature matrix §13.
 *
 * <p>What this covers that the other apps cannot:
 * <ul>
 *   <li>{@code Log4jServletContainerInitializer} starting a <em>per-webapp</em>
 *       {@code LoggerContext}, rather than the single JVM-wide one</li>
 *   <li>{@code Log4jServletFilter} binding that context to each request thread</li>
 *   <li>the {@code ${web:}} lookup, which resolves only inside a ServletContext</li>
 *   <li>orderly shutdown — a webapp undeploy must stop its context without
 *       taking down logging for the rest of the JVM</li>
 * </ul>
 *
 * <pre>
 *   ./bench run jakarta-web --config xml/layout-jsontemplate
 *   curl http://localhost:8082/bench/log
 *   curl http://localhost:8082/bench/context
 * </pre>
 *
 * <p>With {@code -Dbench.selfTest=true} the app drives those endpoints itself
 * and exits, which is what lets it appear in a matrix sweep — see
 * {@link #selfTest()}. {@code ./bench} passes that flag by default; unset it
 * with {@code BENCH_WEB_SELFTEST=0} to get the interactive server back.
 */
public final class WebBench {

    private static final int PORT = Integer.getInteger("bench.web.port", 8082);

    /** Deliberately not "/" — the root context path is the empty string, and a
     *  webapp mounted there makes ${web:contextPath} indistinguishable from an
     *  unresolved lookup. */
    private static final String CONTEXT_PATH = "/bench";

    private static final String BENCH_SERVLET = "org.apache.logging.bench.web.BenchServlet";

    public static void main(final String[] args) throws Exception {
        final Path base = Files.createTempDirectory("log4j-bench-tomcat");
        final Path docBase = Files.createDirectories(base.resolve("webapp"));
        deployAppClasses(docBase);

        final Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(PORT);
        tomcat.getConnector();

        // addWebapp, not addContext. addContext builds a bare StandardContext with
        // no ContextConfig, so nothing ever scans for ServletContainerInitializers
        // — log4j-jakarta-web's SCI would never run, the webapp would silently
        // share the JVM-wide LoggerContext, and every ${web:} lookup would come
        // back unresolved. That is the opposite of what this app exists to test.
        //
        // A non-empty context path, so ${web:contextPath} has something to resolve
        // and the per-webapp context is distinguishable from the JVM-wide one.
        final org.apache.catalina.Context ctx =
                tomcat.addWebapp(CONTEXT_PATH, docBase.toAbsolutePath().toString());

        // The webapp classloader delegates to the system one, where log4j-core,
        // log4j-jakarta-web and Tomcat itself live — the bench runs from a flat
        // -cp, not a WEB-INF/lib. Only this module's own classes are deployed
        // into the webapp (see deployAppClasses), which is what gives the
        // servlet a webapp classloader while Log4j stays shared, the same shape
        // as a container with Log4j in its common/ directory.
        ctx.setParentClassLoader(WebBench.class.getClassLoader());

        // By class name, and from WEB-INF/classes — not `new BenchServlet()`.
        // Instantiating it here would load the class on the system classloader
        // and initialise its static Logger before Tomcat even starts, creating a
        // LoggerContext keyed to the system loader. The SCI would then find that
        // context by walking parent loaders, and ClassLoaderContextSelector's
        // parent-walk returns it without applying the ServletContext entry — so
        // the webapp silently inherits the JVM-wide context, ${web:} never
        // resolves, and the shared context gets renamed to the webapp's path.
        // Deploying the class into the webapp is both more faithful and the
        // thing that makes the per-webapp context real.
        Tomcat.addServlet(ctx, "bench", BENCH_SERVLET);
        ctx.addServletMappingDecoded("/log", "bench");
        ctx.addServletMappingDecoded("/context", "bench");

        tomcat.start();

        System.out.println("Jakarta web bench listening on http://localhost:" + PORT + CONTEXT_PATH);
        System.out.println("  GET " + CONTEXT_PATH + "/log      run a batch of log events on a request thread");
        System.out.println("  GET " + CONTEXT_PATH + "/context  report the LoggerContext bound to this webapp");
        System.out.println("  config        "
                + System.getProperty("log4j.configurationFile", "<default>"));
        System.out.println();

        final AtomicBoolean stopped = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(tomcat, base, stopped)));

        if (Boolean.getBoolean("bench.selfTest")) {
            final int status = selfTest();
            // Through the container's own shutdown, not straight out of main:
            // stopping the webapp is what fires Log4jServletContextListener,
            // which stops the webapp's LoggerContext and flushes its appenders.
            // Exiting without it can leave the last events in a buffer, so a
            // cell would pass with an empty log file underneath it.
            shutdown(tomcat, base, stopped);
            System.exit(status);
        }

        System.out.println("Ctrl-C to stop.");
        tomcat.getServer().await();
    }

    /**
     * Drives the endpoints over real HTTP and reports whether each said what it
     * must, so the app can finish a matrix cell instead of serving forever.
     *
     * <p>Without this the app cannot appear in a sweep at all: {@code main}
     * ends in {@code Server.await()}, so a bounded cell burns the whole timeout
     * and then FAILs — 300 seconds spent to learn nothing. It exercises the
     * servlet stack rather than skipping it, which is the point of having a web
     * app in the bench.
     *
     * <p>Status codes alone would be too weak an assertion. Log4j catches
     * appender exceptions and reports them through {@code StatusLogger}, so a
     * webapp whose logging is entirely broken still answers 200 — the same trap
     * as a clean exit proving nothing. The response bodies are checked instead.
     *
     * @return 0 when every check held, 1 otherwise — the cell's exit status
     */
    private static int selfTest() {
        System.out.println("self-test: driving the endpoints over HTTP");
        // Not try-with-resources: HttpClient only became AutoCloseable in Java
        // 21, and this module is compiled at release 17.
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
        int status = 0;
        try {
            status = Math.max(status,
                    expect(get(client, "/log"), "logged 4 events on thread"));

            final String context = get(client, "/context");
            // The per-webapp LoggerContext, which is the whole reason this
            // module exists. This app is the CONTROL for the log4j-appserver
            // finding in FEATURE-MATRIX §17: nothing logs before Tomcat starts
            // here, so the ServletContext must bind and every ${web:} lookup
            // must resolve. apps/javax-web deliberately does not assert this —
            // there it is known-broken, and asserting it would turn an upstream
            // bug into a red cell on every sweep.
            status = Math.max(status,
                    expect(context, "ServletContext bound to LoggerContext : true"));
            status = Math.max(status, expectResolved(context, "${web:contextPath}"));
            status = Math.max(status, expectResolved(context, "${web:servletContextName}"));
        } catch (final Exception e) {
            System.err.println("  self-test failed: " + e);
            return 1;
        }
        return status;
    }

    private static String get(final HttpClient client, final String path) throws Exception {
        final HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + CONTEXT_PATH + path))
                        .timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.printf("  GET %-10s -> %d%n", CONTEXT_PATH + path, res.statusCode());
        if (res.statusCode() >= 400) {
            throw new IllegalStateException(path + " returned " + res.statusCode());
        }
        return res.body();
    }

    private static int expect(final String body, final String marker) {
        final boolean ok = body.contains(marker);
        System.out.printf("  %-4s %s%n", ok ? "ok" : "FAIL", marker);
        return ok ? 0 : 1;
    }

    /**
     * Checks a {@code ${web:}} lookup came back with a value.
     *
     * <p>The servlet prints the expression and its result on one line, writing
     * {@code <unresolved>} when the substitutor handed the expression straight
     * back — which is how a missing {@code ServletContext} binding shows up at
     * the lookup site.
     */
    private static int expectResolved(final String body, final String expr) {
        for (final String line : body.lines().toList()) {
            final String trimmed = line.trim();
            if (trimmed.startsWith(expr)) {
                final String value = trimmed.substring(expr.length()).trim();
                final boolean ok = !value.isEmpty() && !"<unresolved>".equals(value);
                System.out.printf("  %-4s %s -> %s%n",
                        ok ? "ok" : "FAIL", expr, value.isEmpty() ? "<empty>" : value);
                return ok ? 0 : 1;
            }
        }
        System.out.printf("  FAIL %s was not reported at all%n", expr);
        return 1;
    }

    /**
     * Stops the container exactly once, whether the self-test finished or Ctrl-C
     * did. Both paths run it, and Tomcat's {@code stop} on an already-stopped
     * server throws — which the hook would then report as a failed shutdown on
     * every self-test run.
     */
    private static void shutdown(final Tomcat tomcat, final Path base, final AtomicBoolean stopped) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        try {
            tomcat.stop();
            tomcat.destroy();
            deleteRecursively(base.toFile());
        } catch (final Exception e) {
            System.err.println("shutdown failed: " + e);
        }
    }

    /**
     * Copies this module's own classes into {@code WEB-INF/classes} so the
     * webapp classloader owns them, exactly as an exploded war would.
     *
     * <p>Without this the servlet would be loaded by the system classloader and
     * would resolve the JVM-wide {@code LoggerContext} rather than its webapp's
     * one — which is precisely the arrangement this app exists to distinguish.
     * log4j-core deliberately stays on the parent (system) classpath: that is
     * the container-shared-lib layout, and keeping it there is what makes the
     * per-webapp context worth asserting about.
     */
    private static void deployAppClasses(final Path docBase) throws Exception {
        final Path source = Path.of(WebBench.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        if (!Files.isDirectory(source)) {
            System.err.println("warning: classes are not an exploded directory (" + source
                    + "); the servlet will load from the system classpath and the"
                    + " per-webapp LoggerContext assertion will not hold");
            return;
        }
        // A real web.xml, mainly for <display-name>. Setting it with
        // Context.setDisplayName is silently undone: ContextConfig overwrites the
        // display name from the parsed web.xml during CONFIGURE_START, so with no
        // descriptor it ends up null and ${web:servletContextName} resolves to
        // nothing. The descriptor is also what gives the webapp a declared
        // Servlet version rather than an inferred one.
        Files.writeString(Files.createDirectories(docBase.resolve("WEB-INF")).resolve("web.xml"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
                         version="6.0">
                  <display-name>log4j-bench</display-name>
                </web-app>
                """);

        final Path target = Files.createDirectories(docBase.resolve("WEB-INF/classes"));
        try (var paths = Files.walk(source)) {
            for (final Path path : paths.toList()) {
                final Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private WebBench() {}
}
