package org.apache.logging.bench.javaxweb;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

/**
 * The javax counterpart of {@code apps/jakarta-web}'s servlet.
 *
 * <p>Reports the same three things, because they fail the same way here: which
 * {@code LoggerContext} the request thread is bound to, whether the
 * {@code ServletContext} was bound to it, and whether {@code ${web:}} resolves.
 * A webapp that silently shares the JVM-wide context looks completely normal
 * until two applications in one container disagree about configuration.
 */
public class BenchServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(BenchServlet.class);

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/plain; charset=UTF-8");
        final PrintWriter out = resp.getWriter();

        if (req.getRequestURI().endsWith("/context")) {
            reportContext(req, out);
            return;
        }

        // Container threads are pooled, so anything left in the MDC leaks into
        // the next request this thread serves.
        ThreadContext.put("requestId", String.valueOf(System.nanoTime()));
        ThreadContext.put("path", req.getRequestURI());
        try {
            log.info("Request received on a container thread");
            log.warn("Warning from inside the request");
            log.error("Error with a throwable from inside the request",
                    new IllegalStateException("synthetic servlet failure"));

            out.println("logged 3 events on thread " + Thread.currentThread().getName());
            out.println("MDC during request: " + ThreadContext.getImmutableContext());
        } finally {
            ThreadContext.clearAll();
        }
    }

    private void reportContext(final HttpServletRequest req, final PrintWriter out) {
        final org.apache.logging.log4j.spi.LoggerContext raw = LogManager.getContext(false);
        out.println("LoggerContext class : " + raw.getClass().getName());

        if (raw instanceof LoggerContext ctx) {
            out.println("LoggerContext name  : " + ctx.getName());
            out.println("Configuration       : " + ctx.getConfiguration().getName());
            out.println("Appenders           : " + ctx.getConfiguration().getAppenders().keySet());

            // The same private key log4j-web uses to stash the ServletContext.
            final boolean bound = ctx.getObject("__SERVLET_CONTEXT__") != null;
            out.println("ServletContext bound: " + bound);
            out.println("WebLoggerContextUtils.getServletContext(): "
                    + org.apache.logging.log4j.web.WebLoggerContextUtils.getServletContext());

            if (!bound) {
                // Expected here, and worth explaining rather than looking broken.
                out.println();
                out.println("  ^ EXPECTED in this app, and a real interaction between");
                out.println("    log4j-appserver and log4j-web:");
                out.println();
                out.println("    log4j-appserver routes Tomcat's own logging into Log4j, so the");
                out.println("    container logs through Log4j while starting — before any webapp");
                out.println("    initialises. That creates a LoggerContext keyed to the parent");
                out.println("    (system) classloader. When log4j-web's SCI then asks for the");
                out.println("    webapp's context, ClassLoaderContextSelector.locateContext walks");
                out.println("    parent classloaders, finds that one, and returns it WITHOUT");
                out.println("    applying the ServletContext entry it was given — so ${web:} never");
                out.println("    resolves and the shared context is renamed to this webapp's");
                out.println("    display-name (see 'LoggerContext name' above).");
                out.println();
                out.println("    apps/jakarta-web is the control: same code, no log4j-appserver,");
                out.println("    nothing logs before Tomcat starts, and there the ServletContext");
                out.println("    binds and every ${web:} lookup resolves.");
            }

            final StrSubstitutor sub = ctx.getConfiguration().getStrSubstitutor();
            out.println();
            out.println("web lookups:");
            for (final String expr : new String[] {
                    "${web:contextPath}", "${web:servletContextName}",
                    "${web:serverInfo}", "${web:effectiveMajorVersion}",
                    "${web:rootDir}"}) {
                final String resolved = sub.replace(expr);
                out.printf("  %-32s %s%n", expr,
                        expr.equals(resolved) ? "<unresolved>" : resolved);
            }
        }

        // log4j-appserver again, from inside the container this time.
        out.println();
        out.println("JULI Log impl       : "
                + org.apache.juli.logging.LogFactory.getLog(BenchServlet.class)
                        .getClass().getName());

        out.println();
        out.println("servlet contextPath : " + req.getServletContext().getContextPath());
        out.println("server info         : " + req.getServletContext().getServerInfo());
        out.println("classloader         : "
                + BenchServlet.class.getClassLoader().getClass().getSimpleName());
    }
}
