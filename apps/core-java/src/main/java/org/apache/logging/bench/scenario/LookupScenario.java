package org.apache.logging.bench.scenario;

import java.util.List;

import org.apache.logging.bench.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

/**
 * Resolves all 18 built-in lookups through the live configuration's
 * {@link StrSubstitutor}, which is the same path a config file takes.
 * Feature matrix §9.
 *
 * <p>Several are environment-dependent and legitimately resolve to nothing:
 * {@code ${docker:}} outside a container, {@code ${web:}} outside a servlet
 * container, {@code ${spring:}} outside Spring. Those print as unresolved and
 * that is the correct result, not a failure.
 */
public final class LookupScenario implements Scenario {

    private static final Logger log = LogManager.getLogger(LookupScenario.class);

    private static final List<String> EXPRESSIONS = List.of(
            "${java:version}",
            "${java:runtime}",
            "${java:vm}",
            "${java:os}",
            "${java:hw}",
            "${java:locale}",
            "${sys:user.name}",
            "${sys:java.io.tmpdir}",
            "${env:HOME}",
            "${env:PATH}",
            "${date:yyyy-MM-dd}",
            "${date:HH:mm:ss}",
            "${ctx:traceId}",
            "${ctx:tenant}",
            "${map:key}",
            "${main:0}",
            "${marker:name}",
            "${lower:MIXED-Case-Text}",
            "${upper:mixed-case-text}",
            "${log4j:configLocation}",
            "${log4j:configParentLocation}",
            "${docker:containerId}",
            "${docker:imageName}",
            "${web:contextPath}",
            "${spring:spring.application.name}",
            "${jndi:java:comp/env/appName}",
            "${bundle:BenchMessages:greeting}",
            "${sd:type}",
            "${event:Level}");

    @Override
    public String name() {
        return "lookups";
    }

    @Override
    public String describes() {
        return "All 18 built-in lookups: bundle ctx date docker env event java jndi log4j lower main map marker sd spring sys upper web";
    }

    @Override
    public void run() {
        ThreadContext.put("traceId", "trace-abc-123");
        ThreadContext.put("tenant", "acme-corp");
        try {
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final StrSubstitutor substitutor = ctx.getConfiguration().getStrSubstitutor();

            log.info("Resolving {} lookup expressions", EXPRESSIONS.size());
            for (final String expression : EXPRESSIONS) {
                final String resolved = substitutor.replace(expression);
                final boolean unresolved = expression.equals(resolved);
                // Pad here rather than in the pattern: Log4j placeholders are
                // plain {} and carry no format specifiers.
                log.info("  {} -> {}",
                        String.format("%-46s", expression),
                        unresolved ? "<unresolved — expected outside its runtime>" : resolved);
            }

            // ${jndi:} is disabled by default since 2.17.0 and requires log4j-jndi
            // plus an explicit opt-in system property. Unresolved here is correct.
            log.info("Note: the jndi lookup stays unresolved unless log4j2.enableJndiLookup=true");
        } finally {
            ThreadContext.clearAll();
        }
    }
}
