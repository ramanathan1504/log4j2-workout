# logging-log4j-samples — gap analysis

Against `~/apache/logging-log4j-samples` at `7f9e5a1` (main), Log4j `2.25.2`.

**Nothing here is pushed anywhere.** Every proposed sample is built in this
folder, in the upstream module layout, so it can be tested by hand and offered
upstream one at a time.

---

## What the samples repo already has

| Module | Covers |
|---|---|
| `log4j-samples-android` | Log4j Core on Android |
| `log4j-samples-aspectj` | AspectJ weaving |
| `log4j-samples-asynclogger` | async loggers |
| `log4j-samples-configuration` | a custom `ConfigurationFactory` |
| `log4j-samples-flume-common/-embedded/-remote` | Apache Flume |
| `log4j-samples-graalvm` | GraalVM native image |
| `log4j-samples-gradle-metadata` | Gradle module metadata / strict classpath |
| `log4j-samples-jlink` | JLink custom JRE |
| `log4j-samples-loggerProperties` | a custom lookup |
| `log4j-samples-parser` | parsing log output |
| `log4j-server` | the socket server |
| `log4j-nashorn-test` | Nashorn scripting |
| `log4j-spring-cloud-config-sample-*` | Spring Cloud Config |

The set skews toward **deployment and integration** — Android, GraalVM, JLink,
Gradle, Flume. That is a reasonable bias: those are the things people cannot work
out from the manual.

## What it does not have

The gap is the opposite category: **the features people misconfigure**. Every one
below is something the manual documents but which fails in a way the manual does
not describe, and each already has working, verified code in this bench.

Priority is by how often the failure is silent — a misconfiguration that logs
nothing and exits 0 costs far more than one that throws.

### Priority 1 — silent failures

| Proposed module | Why it matters | Bench source |
|---|---|---|
| `log4j-samples-custom-plugins` | Plugin authoring end to end: appender, layout, lookup, filter, converter, plus `log4j-plugin-processor` writing `Log4j2Plugins.dat`. Without the descriptor Log4j falls back to package scanning, which works in an IDE and finds nothing in a shaded jar — the single most common "my plugin isn't picked up" report. Also shows that `@Plugin` moved package in 3.x, so plugin sources are not portable across lines | `apps/custom-plugins` |
| `log4j-samples-rolling-file` | Rollover policies, strategies and the `Delete` action. `IfAccumulatedFileCount` is stateful, so sibling conditions are order-sensitive and a counter placed before the glob silently keeps one file fewer. Also that `RollingFileManager`'s compressing executor is non-daemon, so a short-lived app hangs on exit unless it calls `LogManager.shutdown()` | `apps/core-java`, `configs/xml/rollover-*` |
| `log4j-samples-jdbc-appender` | `ColumnMapping` vs `Column`, and all three connection sources. The JNDI `DataSource` resolves its name while the appender is built, so the binding must exist before the *first logger anywhere in the JVM* — a `private static final Logger` is already too late, and the error names the JNDI name rather than the ordering | `apps/db`, `apps/jdbc-jndi` |
| `log4j-samples-json-template-layout` | A custom event template, the built-in ECS/GELF/Logstash templates, and resolver configuration. Currently no sample at all for the layout most users reach for | `configs/*/layout-jsontemplate`, `configs/templates/` |

### Priority 2 — routinely misunderstood

| Proposed module | Why it matters | Bench source |
|---|---|---|
| `log4j-samples-bridges` | Routing SLF4J, JUL, JCL and `java.util.logging` **into** Log4j, and `log4j-to-slf4j` **out** of it. Includes the trap that under `log4j-to-jul` `ThreadContext` is a no-op — `JULProvider` registers `NoOpThreadContextMap`, so a `put` is discarded and code reading back its own trace id gets null | `apps/bridges-in`, `apps/bridges-out`, `apps/bridges-to-jul` |
| `log4j-samples-web` | `log4j-web` in a servlet container: the per-webapp `LoggerContext` and `${web:}`. Includes that `log4j-appserver` and `log4j-web` together defeat it — appserver routes container logging through Log4j before any webapp initialises, so `ClassLoaderContextSelector`'s parent walk returns a context without the `ServletContext` entry | `apps/jakarta-web`, `apps/javax-web` |
| `log4j-samples-migration-1x` | Migrating from Log4j 1.x via `log4j-1.2-api`: both 1.x config formats, what the bridge does and does not translate | `apps/log4j1-bridge`, `configs/log4j1/` |
| `log4j-samples-filters` | The sixteen filters at all four scopes — context, logger, appender, appender-ref — and how `onMatch`/`onMismatch` compose. Scope is what people get wrong | `configs/*/filter-all` |
| `log4j-samples-thread-context` | MDC/NDC, and propagation across a thread pool, which does not happen by default | `apps/core-java` `context` scenario |

### Priority 3 — worth having, lower urgency

| Proposed module | Why |
|---|---|
| `log4j-samples-arbiters` | `SystemPropertyArbiter`, `EnvironmentArbiter`, `ClassArbiter`, `Select`, `SpringProfile`. Note the properties format supports no arbiters at all, and fails with a misleading `No name attribute provided for Appender` |
| `log4j-samples-garbage-free` | Garbage-free mode: what it requires and which layouts break it |
| `log4j-samples-network-appenders` | Syslog, Socket, HTTP, Kafka, JeroMQ against real destinations. Needs containers, so it may not suit this repo |
| `log4j-samples-smtp` | `SmtpAppender`, including that `log4j-jakarta-smtp` contains no appender — it swaps the mail implementation via `ServiceLoader`, with nothing naming which one won |

---

## Suggested order for upstream PRs

One module per PR, smallest first, so the first one settles conventions:

1. `log4j-samples-custom-plugins` — self-contained, no infrastructure, highest FAQ value
2. `log4j-samples-json-template-layout` — self-contained
3. `log4j-samples-thread-context` — self-contained
4. `log4j-samples-rolling-file` — writes to a temp directory only
5. `log4j-samples-filters`
6. `log4j-samples-bridges`
7. `log4j-samples-jdbc-appender` — needs H2, which the samples repo does not currently use
8. `log4j-samples-web` — needs an embedded servlet container
9. `log4j-samples-migration-1x`

## House conventions to match

Taken from the existing modules, not guessed:

- `log4j-samples-<name>/` with `pom.xml`, `README.adoc`, sources under
  `org.apache.logging.log4j.samples.<name>`, and a `package-info.java`
- Parent is `org.apache.logging.log4j.samples:log4j-samples:${revision}`;
  dependencies carry **no versions**, they come from the parent
- Test dependencies are `assertj-core` and `junit-jupiter-api`
- **Samples are tested.** Every module has a JUnit test that asserts the
  behaviour, rather than a `main` that prints. Any sample offered upstream must
  do the same
- ASF licence header: `~`-prefixed inside `pom.xml`, `////` block in `README.adoc`
- Add an `xref` entry to the root `README.adoc` in the same style
- A new module must be listed in the root `pom.xml` `<modules>`

---

## Progress

**Seven modules built, tested and green** against the real samples parent
(`logging-parent:12.1.1`, Log4j 2.25.2) — **21 tests**.

| Module | Tests | The quiet failure it demonstrates |
|---|:--:|---|
| `custom-plugins` | 2 | descriptor missing → package-scan fallback: works in an IDE, finds nothing in a shaded jar |
| `json-template-layout` | 4 | an unresolvable `eventTemplateUri` is not an error — valid JSON in a shape nobody asked for |
| `thread-context` | 4 | context does not cross an executor; an unset key renders empty, not null |
| `rolling-file` | 2 | `max` caps `%i`, not retention; `Delete` siblings are order-sensitive |
| `filters` | 3 | the same filter at appender, appender-ref and logger scope admits three different sets |
| `bridges` | 4 | `java.util.logging.manager` cannot be set from Java; too late is silent, not an error |
| `arbiters` | 2 | the branch not taken is never built, so a typo in it is never reported |

### Not built, and why

Each needs a dependency the samples parent does not manage. Adding a hard-coded
version would break the convention every existing module follows, so these need
a decision from upstream before they can be offered.

| Module | Blocked on |
|---|---|
| `jdbc-appender` | a JDBC driver — H2 is not in the parent's `dependencyManagement` |
| `smtp` | GreenMail, likewise unmanaged; `jakarta.mail` would also be needed |
| `network-appenders` | Kafka, syslog and an SMTP sink as containers — probably unsuitable for this repository at all |
| `web` | feasible: `tomcat-embed-core` and `jetty-servlet` **are** managed. Not built here only because a servlet container sample is substantially larger than the others |
| `migration-1x` | feasible: `log4j-1.2-api` is in `log4j-bom`. Scaffolded and removed rather than left half-built |
| `garbage-free` | feasible: needs no extra dependency. Same |

### Verifying a module before offering it

The samples clone is only ever used as a scratch build area, never committed to:

```bash
cp -r log4j-samples/<module> ~/apache/logging-log4j-samples/
# add <module> to <modules> in that repo's pom.xml
cd ~/apache/logging-log4j-samples
./mvnw --projects <module> --also-make test
# then
rm -rf <module> && git checkout -- pom.xml
```

### Bugs caught by building against the real parent

Seven, all of which would have reached a reviewer. This is the argument for
building every module in a scratch checkout before offering it.

- **A plugin's `category` must be `Node.CATEGORY` (`"Core"`), not the element
  type.** `category = Appender.ELEMENT_TYPE` compiles, writes a descriptor entry
  under `appender/counting`, and is then never found. The only symptom is an
  unknown element.
- **`log4j-core`'s `test-jar` is not managed by the parent**, so `ListAppender`
  is unavailable. These samples assert against files on disk instead — closer to
  real use anyway.
- **`commons-logging` is not managed either.** `log4j-jcl` brings it
  transitively; declaring it explicitly needs a hard-coded version that no other
  module has.
- **Substring collisions make assertions lie.** `"audited"` / `"not audited"`
  meant `noneMatch(contains("audited"))` matched the negative case. The filters
  were right; the test was not. Sample messages must share no substring.
- **Rollover completion needs `LogManager.shutdown()`, not a sleep.**
  Compression runs on a background executor; reading early is a race that passes
  locally and fails in CI.
- **`java.util.logging.manager` cannot be set from Java.** An earlier draft set
  it in `@BeforeAll` and the JUL test failed — surefire has already logged
  through JUL by then. It is now a surefire `systemPropertyVariables` entry, i.e.
  a JVM argument, which is what a real deployment needs too. The test failing is
  the fortunate case: in production the bridge is simply inert with no error.
- **`${revision}` must survive shell templating.** Generating poms from a heredoc
  escaped it to `\${revision}`, which Maven cannot resolve.
