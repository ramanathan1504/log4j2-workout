import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.ContextAnchor;
import org.apache.logging.log4j.web.Log4jServletContextListener;
import org.apache.logging.log4j.web.Log4jWebSupport;
import org.apache.logging.log4j.web.WebLoggerContextUtils;

public class Repro {
    static final String CONTEXT_NAME = "CompositeTest";

    public static void main(String[] args) {
        String mode = System.getProperty("mode");
        String location = mode.equals("composite") ? "base.xml,override.xml" : "base.xml";
        ServletContext servletContext = servletContext(location);
        Log4jServletContextListener listener = new Log4jServletContextListener();
        listener.contextInitialized(new ServletContextEvent(servletContext));
        LoggerContext context = ContextAnchor.THREAD_CONTEXT.get();
        Configuration configuration = context.getConfiguration();
        String configurationType = configuration.getClass().getSimpleName();
        String resolved = configuration.getStrSubstitutor().replace("${web:servletContextName}");
        boolean servletContextFound = WebLoggerContextUtils.getServletContext() != null;
        context.getLogger("repro").info("event");
        context.setExternalContext("spring-environment-placeholder");
        boolean foundAfterOverwrite = WebLoggerContextUtils.getServletContext() != null;
        String resolvedAfterOverwrite = configuration.getStrSubstitutor().replace("${web:servletContextName}");
        listener.contextDestroyed(new ServletContextEvent(servletContext));
        String prefix = "mode=" + mode + " ";
        System.out.println("INFO " + prefix + "log4jConfiguration=" + location + " configuration=" + configurationType);
        System.out.println((CONTEXT_NAME.equals(resolved) ? "PASS" : "FAIL") + " " + prefix + "${web:servletContextName} -> " + resolved);
        System.out.println((servletContextFound ? "PASS" : "FAIL") + " " + prefix + "WebLoggerContextUtils.getServletContext() found=" + servletContextFound);
        System.out.println((foundAfterOverwrite && CONTEXT_NAME.equals(resolvedAfterOverwrite) ? "PASS" : "FAIL") + " " + prefix
                + "after setExternalContext(other) found=" + foundAfterOverwrite + " lookup -> " + resolvedAfterOverwrite);
    }

    static ServletContext servletContext(String location) {
        Map<String, Object> attributes = new HashMap<>();
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put(Log4jWebSupport.LOG4J_CONFIG_LOCATION, location);
        return (ServletContext) Proxy.newProxyInstance(
                Repro.class.getClassLoader(), new Class<?>[] {ServletContext.class}, (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getServletContextName":
                            return CONTEXT_NAME;
                        case "getContextPath":
                            return "/composite-test";
                        case "getInitParameter":
                            return initParameters.get((String) methodArgs[0]);
                        case "getInitParameterNames":
                            return Collections.enumeration(initParameters.keySet());
                        case "getAttribute":
                            return attributes.get((String) methodArgs[0]);
                        case "setAttribute":
                            attributes.put((String) methodArgs[0], methodArgs[1]);
                            return null;
                        case "removeAttribute":
                            attributes.remove((String) methodArgs[0]);
                            return null;
                        case "getResource":
                            return Repro.class.getClassLoader().getResource((String) methodArgs[0]);
                        case "getClassLoader":
                            return Repro.class.getClassLoader();
                        case "getMajorVersion":
                            return 4;
                        case "getMinorVersion":
                            return 0;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == methodArgs[0];
                        case "toString":
                            return "StubServletContext";
                        default:
                            return null;
                    }
                });
    }
}
