# Issue #2351 repro summary

## Title
Missing servlet context in `web` lookup when using composite configuration

## Workspace under test
- Module: `log4j2-web-test`
- Servlet container: Apache Tomcat 11.0.18
- Runtime: Homebrew OpenJDK 25.0.2 on macOS
- Log4j artifacts: local `2.26.0-SNAPSHOT` from `~/.m2`
- Web integration artifact: `log4j-jakarta-web`

## Configuration under test
`WEB-INF/web.xml` keeps the canonical startup-managed repro path:

- `log4jConfiguration = log4j2-foo.xml,log4j2-bar.xml`

The application exposes two pages:

- `index.jsp` - chooser page
- `composite.jsp` - uses the web-app startup configuration from `web.xml`
- `single.jsp` - control case using an isolated single-file LoggerContext with `log4j2-single.xml`

Both pages append one line to:

- `${catalina.base}/logs/log4j2-web-test-repro.log`

On the Homebrew Tomcat install used here, that resolves to:

- `/opt/homebrew/opt/tomcat/libexec/logs/log4j2-web-test-repro.log`

## Exact repro steps
1. Build the web module against the locally installed Log4j snapshot.
2. Deploy `log4j2-web-test.war` to Tomcat 11.
3. Start or restart Tomcat.
4. Open the chooser page:
   - `http://localhost:8080/log4j2-web-test/`
5. Open the composite repro page:
   - `http://localhost:8080/log4j2-web-test/composite.jsp`
6. Inspect `log4j2-web-test-repro.log`.
7. Open the single-config control page:
   - `http://localhost:8080/log4j2-web-test/single.jsp`
8. Inspect the same log file again.

## Expected behavior
The servlet context name reported by the JSP / Servlet API should match the value resolved by `${web:servletContextName}` in both cases:

- composite startup configuration
- single-file control configuration

## Observed behavior
### Composite startup configuration (`composite.jsp`)
The page reports the expected servlet context name:

- `Log4j2 Composite Lookup Test`

Observed log line:

```text
COMPOSITE-CTX=${web:servletContextName}  Request URI=/log4j2-web-test/composite.jsp expectedServletContextName=Log4j2 Composite Lookup Test activeLog4jConfiguration=log4j2-foo.xml,log4j2-bar.xml
```

Result:
- `${web:servletContextName}` remains unresolved in the composite startup path.

### Single-file control (`single.jsp`)
The page reports the same expected servlet context name:

- `Log4j2 Composite Lookup Test`

Observed log line:

```text
SINGLE-CTX=Log4j2 Composite Lookup Test  Request URI=/log4j2-web-test/single.jsp expectedServletContextName=Log4j2 Composite Lookup Test activeLog4jConfiguration=log4j2-single.xml
```

Result:
- `${web:servletContextName}` resolves correctly in the single-file control path.

## Conclusion
In this environment, the problem is reproducible:

- single-file configuration resolves the servlet context correctly
- composite configuration leaves the `web` lookup unresolved

Only the configuration shape changes between the two comparisons; the expected servlet context name and request-driven logging path remain the same.

## Follow-up verification after local fix (2026-03-19)
After applying a local source fix and rebuilding Log4j jars as `2.26.0-SNAPSHOT`, the same repro flow was repeated with no application changes.

### Composite startup configuration (`composite.jsp`) after fix
Observed log line:

```text
COMPOSITE-CTX=Log4j2 Composite Lookup Test  Request URI=/log4j2-web-test/composite.jsp expectedServletContextName=Log4j2 Composite Lookup Test activeLog4jConfiguration=log4j2-foo.xml,log4j2-bar.xml
```

Result:
- `${web:servletContextName}` resolves correctly in composite startup mode.

### Single-file control (`single.jsp`) after fix
Observed log line:

```text
SINGLE-CTX=Log4j2 Composite Lookup Test  Request URI=/log4j2-web-test/single.jsp expectedServletContextName=Log4j2 Composite Lookup Test activeLog4jConfiguration=log4j2-single.xml
```

Result:
- Single-file control remains correct.

### Updated outcome
With the locally fixed `2.26.0-SNAPSHOT` jars, both composite and single modes now resolve the servlet context consistently in this test application.

