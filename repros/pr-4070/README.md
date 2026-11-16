# PR #4070 — `${web:*}` lookups do not resolve with a composite `log4jConfiguration`

https://github.com/apache/logging-log4j2/pull/4070

With a comma-separated `log4jConfiguration`, `Log4jWebInitializerImpl` called `Configurator.initialize(name, loader,
uris, createExternalEntry(servletContext))`; that path never stored the `ServletContext` where `WebLookup` looks for
it, so `${web:servletContextName}` and every other `${web:*}` stayed unresolved (#2351). The PR (`c685326e6d`) builds
the composite configuration itself and stores the `ServletContext` with `LoggerContext.putObject` under
`org.apache.logging.log4j.web.servletContext`, separate from the legacy external context, and
`WebLoggerContextUtils.getServletContext()` falls back to `getExternalContext()`. It also deprecates the
external-context API; that half has no runtime effect.

## Commands

```bash
cd log4j-pr-4070-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Each run starts two JVMs. `Repro` hands a stub `ServletContext` (a `java.lang.reflect.Proxy`, name `CompositeTest`) to
the public `Log4jServletContextListener.contextInitialized`, with `log4jConfiguration` set to `base.xml` (single) or
`base.xml,override.xml` (composite). It checks the configuration's `StrSubstitutor`, `WebLoggerContextUtils.getServletContext()`,
the `File` appender's `$${web:servletContextName}` output, and the same lookup after `setExternalContext(<other object>)`,
the Spring-style collision rgoers raised in review.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | single: `${web:servletContextName}` | `CompositeTest` | `CompositeTest` | PASS |
| 2.26.1 | single: after `setExternalContext(other)` | found | found, `CompositeTest` | PASS |
| 2.26.1 | composite (`CompositeConfiguration`): `${web:servletContextName}` | `CompositeTest` | `${web:servletContextName}` | FAIL |
| 2.26.1 | composite: `getServletContext()` | non-null | null | FAIL |
| 2.26.1 | composite: logged line | `ctx=CompositeTest` | `ctx=${web:servletContextName}` | FAIL |
| 2.x HEAD ea28f5a78b | single: `${web:servletContextName}` | `CompositeTest` | `CompositeTest` | PASS |
| 2.x HEAD ea28f5a78b | single: after `setExternalContext(other)` | found | found, `CompositeTest` | PASS |
| 2.x HEAD ea28f5a78b | composite (`CompositeConfiguration`): `${web:servletContextName}` | `CompositeTest` | `CompositeTest` | PASS |
| 2.x HEAD ea28f5a78b | composite: `getServletContext()` | non-null | found | PASS |
| 2.x HEAD ea28f5a78b | composite: after `setExternalContext(other)` | found | found, `CompositeTest` | PASS |

Notes: only `log4j-web` (javax) was run; the `log4j-jakarta-web` half of the PR was not exercised.
