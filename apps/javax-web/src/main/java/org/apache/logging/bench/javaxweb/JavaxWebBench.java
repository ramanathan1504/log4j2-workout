package org.apache.logging.bench.javaxweb;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
        System.out.println("Ctrl-C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
                deleteRecursively(base.toFile());
            } catch (final Exception e) {
                System.err.println("shutdown failed: " + e);
            }
        }));

        tomcat.getServer().await();
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
