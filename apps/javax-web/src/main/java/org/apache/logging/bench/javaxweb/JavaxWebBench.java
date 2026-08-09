package org.apache.logging.bench.javaxweb;

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
 * log4j-web, log4j-appserver and log4j-taglib on embedded Tomcat 9.
 * Feature matrix §12, §13.
 *
 * <p>The javax counterpart of {@code apps/jakarta-web}, and deliberately a
 * separate module: these three are built against {@code javax.servlet} and
 * cannot share a classpath with the jakarta ones.
 *
 * <p>Each of the three is verified differently, because each fails differently:
 * <ul>
 *   <li><strong>log4j-web</strong> — the servlet reports which
 *       {@code LoggerContext} it is bound to and whether {@code ${web:}}
 *       resolves, the same assertions as the jakarta app.</li>
 *   <li><strong>log4j-appserver</strong> — Tomcat's own internal logging goes
 *       through JULI, and this module registers a {@code TomcatLogger} through
 *       {@code META-INF/services/org.apache.juli.logging.Log}. So the check is
 *       what class JULI hands back, not whether anything was logged.</li>
 *   <li><strong>log4j-taglib</strong> — JSP tags do nothing without a JSP
 *       engine, so a real page is compiled and requested.</li>
 * </ul>
 *
 * <pre>
 *   ./bench run javax-web --config xml/appender-servlet
 *   curl http://localhost:8083/bench/context
 *   curl http://localhost:8083/bench/index.jsp
 * </pre>
 *
 * <p>With {@code -Dbench.selfTest=true} the app drives those endpoints itself
 * and exits, which is what lets it appear in a matrix sweep — see
 * {@link #selfTest()}. {@code ./bench} passes that flag by default; unset it
 * with {@code BENCH_WEB_SELFTEST=0} to get the interactive server back.
 */
public final class JavaxWebBench {

    private static final int PORT = Integer.getInteger("bench.javaxweb.port", 8083);

    private static final String CONTEXT_PATH = "/bench";

    private static final String BENCH_SERVLET = "org.apache.logging.bench.javaxweb.BenchServlet";

    public static void main(final String[] args) throws Exception {
        // Before Tomcat starts: which JULI Log implementation is registered.
        // Reading it here rather than after start means the answer is not
        // confused by Tomcat having already cached a factory.
        reportContainerLogging();

        final Path base = Files.createTempDirectory("log4j-bench-tomcat9");
        final Path docBase = Files.createDirectories(base.resolve("webapp"));
        deployWebapp(docBase);

        final Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(PORT);
        tomcat.getConnector();

        // addWebapp, not addContext — addContext installs no ContextConfig, so
        // nothing scans for ServletContainerInitializers and log4j-web's SCI
        // never runs. Same trap as the jakarta app.
        final org.apache.catalina.Context ctx =
                tomcat.addWebapp(CONTEXT_PATH, docBase.toAbsolutePath().toString());
        ctx.setParentClassLoader(JavaxWebBench.class.getClassLoader());

        Tomcat.addServlet(ctx, "bench", BENCH_SERVLET);
        ctx.addServletMappingDecoded("/log", "bench");
        ctx.addServletMappingDecoded("/context", "bench");

        tomcat.start();

        System.out.println();
        System.out.println("Javax web bench listening on http://localhost:" + PORT + CONTEXT_PATH);
        System.out.println("  GET " + CONTEXT_PATH + "/context   LoggerContext, ${web:} lookups, JULI binding");
        System.out.println("  GET " + CONTEXT_PATH + "/log       log events on a request thread");
        System.out.println("  GET " + CONTEXT_PATH + "/index.jsp the log4j-taglib tags, compiled and run");
        System.out.println("  config        "
                + System.getProperty("log4j.configurationFile", "<default>"));
        System.out.println();

        final AtomicBoolean stopped = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(tomcat, base, stopped)));

        if (Boolean.getBoolean("bench.selfTest")) {
            final int status = selfTest();
            // Through the container's own shutdown, not straight out of main:
            // stopping the webapp is what fires log4j-web's context listener,
            // which stops the webapp's LoggerContext and flushes its appenders
            // — the ServletAppender in particular writes through
            // ServletContext.log(), which is gone once Tomcat has been killed.
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
     * and then FAILs — 300 seconds spent to learn nothing.
     *
     * <p>What it asserts is narrower than the jakarta app's self-test, and
     * deliberately so. The {@code ${web:}} lookups do <em>not</em> resolve here
     * and the {@code ServletContext} does not bind — that is the known
     * log4j-appserver interaction in FEATURE-MATRIX §17, a duplicate of
     * upstream #2314. Asserting on it would paint every sweep red for a bug
     * that is already filed, and asserting the <em>broken</em> state would turn
     * an upstream fix into a failure. So the binding is reported and the checks
     * are on what must hold regardless: the servlet answers and logs, JULI is
     * routed into Log4j, and the taglib page compiles and renders.
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
                    expect(get(client, "/log"), "logged 3 events on thread"));

            final String context = get(client, "/context");
            // log4j-appserver's entire surface: it is selected by ServiceLoader,
            // so the class JULI hands back is the only unambiguous answer. If
            // this is Tomcat's own DirectJDK14Log the module is not doing
            // anything and the app is testing nothing.
            status = Math.max(status,
                    expect(context, "JULI Log impl       : org.apache.logging.log4j.appserver"));

            // Reported, not asserted — see the note above.
            report(context, "ServletContext bound: ");
            report(context, "${web:contextPath}");

            // log4j-taglib: the tags are nothing without a JSP engine, so the
            // page must actually compile through Jasper and run. A taglib
            // failure surfaces as a 500 from the compile, which get() rejects.
            status = Math.max(status,
                    expect(get(client, "/index.jsp"),
                            "tags rendered: setLogger, info, warn, error, debug, ifEnabled, entry, exit"));
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
        System.out.printf("  GET %-16s -> %d%n", CONTEXT_PATH + path, res.statusCode());
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
     * Prints what a line of the report currently says, without judging it. Used
     * for the facts this app cannot assert on — see {@link #selfTest()}.
     */
    private static void report(final String body, final String prefix) {
        for (final String line : body.lines().toList()) {
            final String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                System.out.printf("  --   %s%n", trimmed);
                return;
            }
        }
        System.out.printf("  --   %s was not reported at all%n", prefix.trim());
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
     * log4j-appserver's whole surface: whether Tomcat's internal logging is
     * routed into Log4j.
     *
     * <p>It is selected by ServiceLoader, so there is no configuration to check
     * and no message to grep for — a container logging through its own JULI
     * defaults looks much like one logging through Log4j. The class name is the
     * only unambiguous answer.
     */
    private static void reportContainerLogging() {
        System.out.println("Log4j bench — javax web (Tomcat 9)");
        final org.apache.juli.logging.Log juli =
                org.apache.juli.logging.LogFactory.getLog(JavaxWebBench.class);
        System.out.println("  JULI Log impl   " + juli.getClass().getName());
        System.out.println("                  "
                + (juli.getClass().getName().startsWith("org.apache.logging.log4j.appserver")
                        ? "log4j-appserver is routing Tomcat's own logging into Log4j"
                        : "NOT log4j-appserver — Tomcat is using its own JULI logging"));
        juli.info("A message logged through Tomcat's JULI API");
    }

    /**
     * Writes the webapp: this module's classes into WEB-INF/classes, a web.xml,
     * and the JSP that exercises the tag library.
     *
     * <p>The classes must belong to the webapp classloader, or the servlet
     * resolves the JVM-wide LoggerContext instead of the webapp's one and the
     * per-webapp assertion is meaningless — see apps/jakarta-web, where the same
     * thing had to be fixed.
     */
    private static void deployWebapp(final Path docBase) throws Exception {
        final Path webInf = Files.createDirectories(docBase.resolve("WEB-INF"));

        Files.writeString(webInf.resolve("web.xml"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
                                             http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
                         version="4.0">
                  <display-name>log4j-bench-javax</display-name>
                </web-app>
                """);

        // A webapp-scoped Log4j configuration, which is the ONLY place
        // ServletAppender can live: it needs a ServletContext, so it cannot be
        // built by the JVM-wide LoggerContext that -Dlog4j.configurationFile
        // creates before Tomcat starts. log4j-web's initialiser reads this when
        // no system property overrides it.
        Files.writeString(webInf.resolve("classes-log4j2.xml"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="WARN" name="javax-web-webapp">
                  <Appenders>
                    <!-- No %d: ServletContext.log() supplies its own timestamp,
                         and a layout with one produces two per line. -->
                    <Servlet name="Servlet" logThrowables="true">
                      <PatternLayout pattern="[log4j] %-5level %logger{1.} %notEmpty{%X }- %msg%n"/>
                    </Servlet>
                  </Appenders>
                  <Loggers>
                    <Root level="INFO">
                      <AppenderRef ref="Servlet"/>
                    </Root>
                  </Loggers>
                </Configuration>
                """);

        // The JSP is what makes log4j-taglib real: the tags are compiled into
        // the page by Jasper and run on a request thread.
        Files.writeString(docBase.resolve("index.jsp"),
                """
                <%@ page contentType="text/plain; charset=UTF-8" %>
                <%@ taglib uri="http://logging.apache.org/log4j/tld/log" prefix="log" %>
                <%-- setLogger binds a logger for the rest of the page, so the
                     individual tags below need no logger attribute. --%>
                <log:setLogger logger="org.apache.logging.bench.jsp"/>
                log4j-taglib
                ============
                <log:info message="INFO through the taglib"/>
                <log:warn message="WARN through the taglib"/>
                <log:error message="ERROR through the taglib"/>
                <log:debug message="DEBUG through the taglib"/>
                <%-- ifEnabled is the tag equivalent of isDebugEnabled(): the body
                     is not evaluated at all when the level is off. --%>
                <log:ifEnabled level="info">
                info was enabled, so this line was rendered
                </log:ifEnabled>
                <%-- entry/exit emit the same FlowMessages as Logger.traceEntry. --%>
                <log:entry/>
                <log:exit/>
                tags rendered: setLogger, info, warn, error, debug, ifEnabled, entry, exit
                """);

        final Path source = Path.of(JavaxWebBench.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        if (!Files.isDirectory(source)) {
            System.err.println("warning: classes are not an exploded directory (" + source
                    + "); the servlet will load from the system classpath and the"
                    + " per-webapp LoggerContext assertion will not hold");
            return;
        }
        final Path target = Files.createDirectories(webInf.resolve("classes"));
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

    private JavaxWebBench() {}
}
