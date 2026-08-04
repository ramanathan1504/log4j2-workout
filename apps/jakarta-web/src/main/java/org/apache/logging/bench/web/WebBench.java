package org.apache.logging.bench.web;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
