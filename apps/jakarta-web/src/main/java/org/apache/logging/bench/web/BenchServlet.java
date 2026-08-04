package org.apache.logging.bench.web;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

/**
 * Logs from a real request thread and reports which {@code LoggerContext} that
 * thread is bound to.
 *
 * <p>The context question is the interesting one. Outside a servlet container
 * every logger shares one JVM-wide context; inside one, {@code log4j-jakarta-web}
 * gives each webapp its own, so two applications in the same container can hold
 * different configurations. Bugs here show up as one webapp's configuration
 * leaking into another's.
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

        // Request-scoped MDC, cleared in a finally — container threads are pooled,
        // so anything left behind leaks into the next request served by this thread.
        ThreadContext.put("requestId", String.valueOf(System.nanoTime()));
        ThreadContext.put("path", req.getRequestURI());
        ThreadContext.put("remote", String.valueOf(req.getRemoteAddr()));
        try {
            log.info("Request received on a container thread");
            log.debug("Debug from inside the request");
            log.warn("Warning from inside the request");
            log.error("Error with a throwable from inside the request",
                    new IllegalStateException("synthetic servlet failure"));

            out.println("logged 4 events on thread " + Thread.currentThread().getName());
            out.println("MDC during request: " + ThreadContext.getImmutableContext());
        } finally {
            ThreadContext.clearAll();
        }
    }

    /** Tomcat's own classloader toString spans several lines; this keeps the
     *  report one line per fact. */
    private static String describe(final ClassLoader loader) {
        return loader == null
                ? "<bootstrap>"
                : loader.getClass().getSimpleName() + "@"
                        + Integer.toHexString(System.identityHashCode(loader));
    }

    private void reportContext(final HttpServletRequest req, final PrintWriter out) {
        final org.apache.logging.log4j.spi.LoggerContext raw = LogManager.getContext(false);
        out.println("LoggerContext class : " + raw.getClass().getName());

        if (raw instanceof LoggerContext ctx) {
            out.println("LoggerContext name  : " + ctx.getName());
            out.println("Configuration       : " + ctx.getConfiguration().getName());
            out.println("Appenders           : " + ctx.getConfiguration().getAppenders().keySet());
            out.println("Config source       : " + ctx.getConfiguration().getConfigurationSource());

            // ${web:} resolves only when a ServletContext is available — which is
            // precisely what distinguishes this app from the others.
            //
            // Report the two preconditions separately, because they fail
            // identically at the lookup site (the expression comes back
            // verbatim) but mean completely different things: a missing
            // ServletContext binding is a log4j-jakarta-web initialisation
            // problem, whereas a missing "web" entry in the Interpolator is a
            // plugin-discovery problem.
            out.println("ServletContext bound to LoggerContext : "
                    + (ctx.getObject("__SERVLET_CONTEXT__") != null));
            out.println("WebLoggerContextUtils.getServletContext() : "
                    + org.apache.logging.log4j.web.WebLoggerContextUtils.getServletContext());

            final StrSubstitutor sub = ctx.getConfiguration().getStrSubstitutor();
            out.println("StrLookup                            : "
                    + sub.getVariableResolver().getClass().getName());
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

        // Every LoggerContext alive in this JVM. In a container the interesting
        // failure is not "logging broke" but "the wrong context answered", so
        // list them rather than trusting the one this thread happens to get.
        // Identity, not just names. A container bug here looks like "the right
        // name, the wrong object".
        final Object attr = req.getServletContext()
                .getAttribute(org.apache.logging.log4j.web.Log4jWebSupport.CONTEXT_ATTRIBUTE);
        out.println();
        out.printf("this servlet's classloader   : %s%n", describe(BenchServlet.class.getClassLoader()));
        out.printf("ServletContext classloader   : %s%n", describe(req.getServletContext().getClassLoader()));
        out.printf("LoggerContext resolved here  : %s@%x%n",
                raw.getClass().getSimpleName(), System.identityHashCode(raw));
        out.printf("LoggerContext on ServletCtx  : %s%n",
                attr == null ? "<absent>"
                        : attr.getClass().getSimpleName() + "@"
                                + Integer.toHexString(System.identityHashCode(attr)));

        out.println();
        out.println("all LoggerContexts in this JVM:");
        final org.apache.logging.log4j.spi.LoggerContextFactory factory = LogManager.getFactory();
        if (factory instanceof org.apache.logging.log4j.core.impl.Log4jContextFactory f) {
            for (final LoggerContext each : f.getSelector().getLoggerContexts()) {
                out.printf("  %-24s servletContext=%s config=%s%n",
                        each.getName(),
                        each.getObject("__SERVLET_CONTEXT__") != null,
                        each.getConfiguration().getName());
            }
        }

        out.println();
        out.println("servlet contextPath : " + req.getServletContext().getContextPath());
        out.println("server info         : " + req.getServletContext().getServerInfo());
    }
}
