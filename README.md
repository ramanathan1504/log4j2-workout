# Log4j Bench

A complete, real-application bench for Apache Log4j — every appender, layout,
filter, lookup, message type and configuration format, runnable across the 1.x
bridge, every 2.x line, and 3.x.

Built for maintainer work: when an issue or PR arrives, run it here against a
real application on every affected version, then emit a standalone reproduction
zip to attach to the issue.

```bash
./bench list                                          # what exists
./bench run core-java --config xml/layout-pattern-full # run scenarios
./bench matrix --scenario exceptions                   # same test, every version
./bench matrix --apps core-java,db --javas 17,21       # slice any axis
./bench coverage                                       # what is reached, what is not
./bench repro 4143 --scenario exceptions --config xml/layout-jsontemplate
```

---

## Start here

**`docs/HANDOVER.md`** is the operator's manual — everything needed to use this
repository without asking anyone. Read it once end to end, then use it as
reference. It covers the daily commands, reviewing an issue, producing a
reproduction, what needs Docker, the git workflow, and a collected list of the
traps that cost time.

`docs/BY-HAND.md` is the step-by-step playbook for the two jobs this bench exists
for, kept separate: reviewing a pull request, and reproducing then fixing an
issue. Start there when you have a specific PR or issue number in front of you.
`docs/PR-REVIEW.md` is the other half of the first job: how to judge whether a
contributor's pull request should be merged at all, and how to follow it after
you comment. `docs/pr-reviews/` holds the reviews already written, and
`./bench followup` says what has moved on them since.

`docs/GH-COMMANDS.md` is the `gh` reference that goes with it, so the whole loop
— read the report, run it, file the finding, watch CI — stays in the terminal.

`docs/FEATURE-MATRIX.md` is the catalogue: what is covered, what is not, and §17,
the findings — 56 Log4j behaviours, each traced to source rather than inferred.

`docs/ISSUES.md` lists what was raised upstream. `docs/GAPS.md` lists everything
open — coverage, drafts, samples, infrastructure — with why and what closing each
would take. `docs/issue-drafts/` holds findings written up to Apache's bug
template, ready to file by hand. Two are filed already (#4241, #4242); four are drafted and not.
Nothing here ever touches an upstream project.

---

## Setup

**Prerequisites**

| Need | Why |
|---|---|
| JDK 17 (and ideally 8, 21, 22) | The JDK axis. `installed_javas()` discovers whatever `/usr/libexec/java_home -V` reports, so extra JDKs widen the matrix and missing ones simply narrow it. JDK 8 exists to test the oldest line Log4j 2 supports. |
| Maven 3.9+ | Every app but one. |
| Gradle | `spring-boot-gradle` only — the same app built the other way, to catch differences Maven's dependency resolution hides. |
| Docker | Only for `apps/network` and `apps/nosql`. Everything else embeds its infrastructure (H2, GreenMail, Tomcat, Artemis, an in-process JNDI provider, a Spring Cloud Config server) — `apps/db` runs on embedded H2, whatever its POM also carries drivers for. |

**Clone and enable the hooks**

```bash
git clone git@github.com:ramanathan1504/log4j2-workout.git
cd log4j2-workout
git config core.hooksPath .githooks    # required: see Contributing
./bench list                           # verify it runs
```

The `core.hooksPath` line is per-clone — git will not set it for you. Skipping it
costs nothing but a slower failure: the server rejects a direct push to a
protected branch anyway, just after a round trip instead of instantly.

**Verify a real run**

```bash
./bench run core-java --config xml/baseline-console
```

Log4j catches appender exceptions, reports them through `StatusLogger`, and lets
the JVM exit 0 — so a clean exit proves nothing on its own. Every app here
asserts on an outcome instead: rows read back, mail consumed, a queue drained.
When something stores or sends less than expected, raise the status level before
assuming the configuration is wrong:

```bash
BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' \
  ./bench run nosql --config xml/appender-nosql
```

---

## Status

Ready for maintainer work: investigate an issue or PR here, then emit a
standalone reproduction to attach to it.

**Verified working**

| | |
|---|---|
| Module reach (2.x) | 41 of 41 shippable modules on some app's classpath (`./bench coverage` recomputes it) |
| Module reach (3.x) | 21 of 22 shippable modules on a classpath. The exception is `log4j-plugin-processor`: `@Plugin` moved package between the lines, so plugin sources cannot compile against both and `apps/custom-plugins` is 2.x-only. Catalogued in `FEATURE-MATRIX` §19 |
| Config formats | every config in XML, JSON, YAML and properties, plus both Log4j 1.x formats |
| Axes | 19 app targets · 73 configs · JDK 8/17/21/22 · 2.24.1 → 3.0.0-SNAPSHOT. **8 of the 19 apps run on 3.x** — eleven are 2.x-only (`APPS_2X_ONLY` in `bench`), either because their Log4j module has no 3.x release (`log4j-1.2-api`, `log4j-jakarta-web`, `log4j-spring-boot`, `log4j-jpa`, the JUL/JCL/SLF4J bridges, SMTP, JMS) or because the sources cannot compile against both lines (`custom-plugins`) |
| Pattern converters | all 41 |
| Layouts | every layout Log4j ships except `SerializedLayout`, which is deprecated and refuses to build without `log4j2.enableSerialization` |
| Appenders needing infrastructure | JDBC, JPA, SMTP, JMS, JNDI, Kafka, Syslog, Socket, HTTP, JeroMQ, MongoDB, CouchDB, Cassandra — all verified by outcome against real services |
| Reproduction zips | generated, extracted outside the repo and run standalone |
| CI | ~200 cells in 13 minutes on every pull request into `development` |

**Known open**

- `jakarta-web` and `javax-web` skip in matrix sweeps. Both serve until
  interrupted, so they cannot finish a cell. The fix is a self-test like
  `SelfTestRunner` in `apps/spring-boot-maven`; nobody has written it. Drive them
  by hand meanwhile.
- No full `--all` sweep has been recorded. It is ~37,000 cells, measured at about
  six hours with `--scenario` and roughly a week without. The CI slice is the
  regression gate; a full sweep is worth running once as a baseline, and then
  only against a specific Log4j change.

**Filed upstream from findings here**

- [apache/logging-log4j2#4241](https://github.com/apache/logging-log4j2/issues/4241) —
  `AbstractDatabaseManager` keeps accepting writes after a failed startup, and is
  never shut down. Affects 2.x and 3.x.
- [apache/logging-log4j2#4242](https://github.com/apache/logging-log4j2/issues/4242) —
  `log4j-cassandra` leaks the DataStax `Cluster` on failed startup, so the JVM
  never exits. 2.x only.

`docs/FEATURE-MATRIX.md` §17 holds the full findings table — around 25 Log4j
behaviours, each traced to source rather than inferred.

---

## Layout

```
├── bench                    CLI — the only entry point you need
├── docs/FEATURE-MATRIX.md   the complete coverage catalog (293 plugins)
├── configs/                 the config library, shared by every app
│   ├── xml/  json/  yaml/  properties/   the same configs in all four formats
│   ├── log4j1/              log4j.properties + log4j.xml for the 1.x bridge
│   └── templates/           JsonTemplateLayout event templates
├── apps/
│   ├── core-java/           no framework — scenarios, custom plugins
│   ├── java8-baseline/      Java 8 source, for the oldest supported JDK
│   ├── spring-boot-maven/   real Spring Boot app, HTTP-triggered
│   ├── spring-boot-gradle/  the same app, built by Gradle
│   ├── jakarta-web/         servlet container, per-webapp LoggerContext
│   ├── log4j1-bridge/       1.x API on 2.x core via log4j-1.2-api
│   ├── bridges-in/          SLF4J 1.7, JUL, JCL, JPL, iostreams → Log4j
│   ├── bridges-out/         Log4j API → SLF4J → Logback
│   ├── bridges-to-jul/      Log4j API → java.util.logging
│   ├── custom-plugins/      plugin authoring, via log4j-plugin-processor
│   ├── jpa/                 JPA appender on EclipseLink + embedded H2
│   ├── smtp/                SMTP appender against embedded GreenMail
│   ├── javax-web/           log4j-web + appserver + taglib on Tomcat 9
│   ├── jdbc-jndi/           JDBC DataSource resolved through in-process JNDI
│   ├── jms/                 JMS appender against embedded ActiveMQ Artemis
│   ├── spring-cloud-config/ config-server-driven reload, server embedded
│   ├── network/            Syslog, Socket, Http, Kafka, JeroMQ, SMTP over sockets
│   └── db/                  JDBC / Mongo / Cassandra / CouchDB appenders
├── infra/docker-compose.yml Kafka, Mongo, Cassandra, CouchDB, Postgres, MySQL,
│                            syslog-ng, MailHog, Elasticsearch, Kibana
├── repros/                  generated reproductions, one folder per issue/PR
└── scripts/repro.sh         reproduction generator
```

**Three apps are 2.x-only** — `log4j1-bridge`, `jakarta-web` and
`spring-boot-gradle` — because `log4j-1.2-api`, `log4j-jakarta-web` and
`log4j-spring-boot` have no 3.x release. `./bench matrix` reports them as SKIP
on 3.x rather than FAIL, and the Maven build drops them from the reactor.

**Configs are a shared library, not per-module.** Any app can load any config via
`-Dlog4j.configurationFile`, which is what makes the version × config × app
matrix work without duplicating a single XML file.

Every config exists in all four Log4j 2 formats under the same name, so the
format is just a directory and switching between them is a one-word change:

```bash
./bench run core-java --config xml/filter-all
./bench run core-java --config properties/filter-all
```

The extension is inferred from the directory; a bare name (`--config filter-all`)
means the XML one. The mirrors are not mechanical translations — where a format
genuinely cannot express what the XML does, the file says so and takes the
nearest honest route. See `configs/properties/README.md` and
`configs/json/README.md`, and `docs/FEATURE-MATRIX.md` §17 for the full list of
what building them turned up.

**One config is not in all four formats.** `arbiters` has no properties version,
because that format has no arbiter support whatsoever and the nearest spelling
throws a fatal `ConfigurationException` rather than degrading. It exists in XML,
JSON and YAML.

Format support is not uniform across the version axis. XML, JSON and YAML load
on both 2.x and 3.x; **the properties format is 2.x-only**, because 3.x dropped
`PropertiesConfigurationFactory` and replaced it with a Jackson java-properties
reader that uses entirely different keys. A properties config on 3.x falls back
to the default configuration without complaint.

**Composite configuration.** Comma-separate two or more configs and Log4j merges
them, later files overriding earlier ones:

```bash
./bench run core-java --config xml/baseline-console,xml/custom-levels
```

Each element resolves independently, so the short names still work. The merged
configuration takes its name from the last file, which is the quickest way to
tell a merge happened at all.

The 1.x formats live in `configs/log4j1/` and need the bridge's factory, which
is off by default. `./bench` recognises the directory and passes
`-Dlog4j1.compatibility=true` plus `-Dlog4j.configuration` (the 1.x property
name, which takes a URL) automatically.

---

## The version axis

`./bench` resolves a real classpath per version and forks a JVM — it never runs
inside Maven's own classpath, so what executes is exactly what a repro zip ships.

| Selector | Log4j |
|---|---|
| *(default)* | `2.27.0-SNAPSHOT` — your local `2.x` build |
| `--log4j 2.24.1` | last line before the `ThrowableStackTraceRenderer` rewrite |
| `--log4j 2.25.4` | the line Apache Spark branch-4.2 ships |
| `--log4j 2.26.1` / `2.27.0` | current releases |
| `--log4j 3.0.0-SNAPSHOT` | your local `main` build |

Four facts about 3.x that the bench encodes, because they surprise people:

0. **3.x reads a different system property for the config location.** It is
   `log4j.configuration.location`; `log4j.configurationFile` is not read at all.
   Nothing fails when you pass the 2.x name — Log4j quietly uses
   `DefaultConfiguration` — so the run looks fine and tests nothing. `./bench`
   picks the right property per version, and the banner prints the one actually
   set alongside the configuration Log4j really loaded.

1. **3.x pins `log4j-api` to `2.24.3`.** The API is versioned separately; there is
   no `log4j-api:3.0.0-SNAPSHOT`.
2. **Modules were split out of `log4j-core`**: `log4j-async-logger`,
   `log4j-compress`, `log4j-csv`, `log4j-script`, `log4j-jdbc`,
   `log4j-config-{jackson,properties,yaml}`.
3. **Modules that do not exist on 3.x at all**: `log4j-1.2-api`, `log4j-jcl`,
   `log4j-web`, `log4j-cassandra`, `log4j-jpa`, `log4j-spring-boot`.

`./bench` handles all three by passing `-Plog4j-3x -Dlog4j3=true` for 3.x versions.

To (re)build the local snapshots:

```bash
cd ~/apache/logging-log4j2 && mvn install -DskipTests          # 2.27.0-SNAPSHOT
cd ~/apache/log4j-main     && mvn install -DskipTests          # 3.0.0-SNAPSHOT
```

---

## The four axes

`matrix` sweeps app × config × JDK × Log4j version. Every axis defaults to a
single value except the Log4j one, because the full cross product is thousands
of forked JVMs:

```bash
./bench matrix                                   # every Log4j version, one app/config/JDK
./bench matrix --apps core-java,db --javas 17,21 # widen the axes you care about
./bench matrix --all                             # every valid cell — hours
```

**Most of the cross product is invalid, and that is reported rather than run.**
A cell is skipped with its reason when the app has no 3.x release path, the JDK
is older than the app's class file target, Log4j 3 is paired with a JDK below
17, or a properties config is paired with 3.x. A skip is information; a failure
you have to explain away is not.

The JDK axis is discovered from `/usr/libexec/java_home`, so it reflects what is
installed. Only `java8-baseline` is compiled at release 8 — every other module
targets 17 and is skipped on older JDKs rather than failing to load.

`./bench coverage` answers the other question: which Log4j modules are on some
app's classpath at all, and which axis cells have actually been run. It reads
the module list from your source clone, so it stays honest as the clone moves.

---

## Generating a reproduction

```bash
./bench repro 4143 \
  --scenario exceptions \
  --config xml/layout-jsontemplate \
  --log4j 2.24.1 --log4j 2.25.4 --log4j 2.26.1 --log4j 2.27.0-SNAPSHOT
```

Produces `repros/issue-4143/`:

- **`log4j-issue-4143-repro.zip`** — a standalone Maven project with no parent
  POM and no reference to this workspace. Its dependencies are derived from the
  config it ships, so a JsonTemplateLayout repro correctly pulls in
  `log4j-layout-template-json` instead of silently falling back to the default
  configuration and reproducing nothing.
- **`README.md`** — a filled-in verification matrix, ready to paste into the issue.
- **`output/<version>.log`** — the full run against each version.

Pass `--pr` for a pull request instead of an issue.

**Failure detection.** Log4j catches appender exceptions and reports them through
`StatusLogger`, so the JVM still exits 0 while the bug has plainly occurred.
The matrix scans for that as well as for a non-zero exit — checking only the exit
code would mark almost every layout bug as PASS.

One config breaks that rule deliberately: `appender-composite` cannot demonstrate
`Failover` without a primary that fails, and a failing appender reports itself.
It emits exactly three errors, all naming `BrokenPrimary`, and its header says so.
Judge that config by whether `logs/composite/failover.log` filled up.

The bench itself is never mutated; generation writes only to `repros/`, so there
is nothing to revert afterwards.

---

## Scenarios

| Scenario | Covers |
|---|---|
| `messages` | every `Message` type, lambda/`Supplier` forms, flow tracing |
| `lookups` | all 18 built-in lookups, resolved through the live `StrSubstitutor` |
| `context` | MDC/NDC, marker hierarchies, propagation across a thread pool |
| `exceptions` | nested causes, suppressed, circular, deep stacks, colliding `equals`, mutating `hashCode` |
| `rollover` | drives rollovers, then reports rolled vs compressed files on disk |
| `programmatic` | `ConfigurationBuilder` — a full config built in code, no file |

---

## Backing services

Nothing starts automatically. Bring up only what the appender under test needs:

```bash
docker compose -f infra/docker-compose.yml up -d kafka
docker compose -f infra/docker-compose.yml up -d mongodb postgres
docker compose -f infra/docker-compose.yml --profile observability up -d
```

**Cassandra needs `cassandra-init`, not just `cassandra`:**

```bash
docker compose -f infra/docker-compose.yml up -d mongodb couchdb cassandra-init
```

The appender cannot create its own keyspace. Its DataStax driver pulls in Netty,
whose `InternalLoggerFactory` acquires a Log4j logger *while the driver is
initialising* — which configures Log4j, which starts the Cassandra appender,
which connects to a keyspace no in-JVM bootstrap has reached yet. `cassandra-init`
applies `infra/cql/cassandra-init.cql` once the node is healthy, before anything
touches the driver. Details in `docs/site` → Findings.

---

## Coverage

`docs/FEATURE-MATRIX.md` is the authoritative catalog — 293 plugins across 59
modules, extracted from the source clone rather than from documentation, with a
gap list in §16 tracking what the bench does not yet exercise.

---

## What the matrix will not run, and why

`./bench matrix` prunes its cross product before running anything. A cell that
cannot pass is reported as `SKIP` with the reason, never as a failure — a
failing cell that could never have passed is noise, and worse, it buries the
failures that matter. One 698-cell sweep produced 98 failures, none of them
Log4j defects; all were the matrix asking questions with no meaning.

The rules, all in `bench`:

| Rule | What it prunes |
|---|---|
| `min_java_for` | Everything is compiled at release 17 except `java8-baseline`, so java8 cells skip for every other app. |
| `min_log4j_for` | An app whose Log4j module is younger than the version under test. `jms` needs 2.25.0+, because `log4j-jakarta-jms` did not exist before it. |
| `is_2x_only` | Eleven apps have no 3.x release path — `log4j-1.2-api`, `log4j-jakarta-web` and friends were never published for 3.x. |
| `INTERACTIVE_APPS` | Empty. Every server app drives its own endpoints under `-Dbench.selfTest=true` and exits, so none are pruned. Kept for the next app that cannot. |
| `requires_config_for` | An app that asserts on a specific appender. `db` checks rows reached a JDBC appender, so `db` under `baseline-console` is meaningless. |
| `requires_app_for` | The mirror: a config whose destinations only one app provides. `appender-network` posts to listeners `apps/network` opens in-process; anything else gets `Connection refused`. |

`appender-nosql` is deliberately not pinned to an app: Mongo, CouchDB and
Cassandra run in containers, so any app can write to them. Only in-process
infrastructure creates that coupling.

### Sweep knobs

```bash
./bench matrix --all --scenario messages     # one scenario per cell, not all seven
./bench matrix --all --reuse-builds          # reuse the cached classpath per (app, version)
BENCH_CELL_TIMEOUT=600 ./bench matrix --all  # per-cell wall clock, default 300s
BENCH_SPRING_SELFTEST=0 ./bench run spring-boot-maven   # interactive server, not the self-test
```

**`--scenario` is the one that matters for a full sweep.** Without it every cell
runs all seven scenarios, and `rollover` writes 2000 lines — paired with a JDBC
appender that is 2000 inserts per cell. It is the difference between a sweep
taking hours and taking a week.

**Never set `BENCH_REUSE_BUILDS=1` while editing app sources.** It reuses a
cached classpath and will run stale classes, which is exactly what the
always-build default exists to prevent.

Every cell is bounded. A non-terminating app fails with a stated cause rather
than stalling the sweep — a sweep once sat on one for two hours looking like
slow progress.

### Continuous integration

`.github/workflows/bench.yml` runs a slice of the matrix on every pull request
into `development`: all four Log4j 2 configuration formats plus both 1.x
formats, JDK 8/17/21, Log4j 2.24.1 and 2.26.1, across the thirteen apps that
need no external infrastructure. About 200 cells in 13 minutes.

It is a regression gate, not a discovery tool. A full sweep re-proves things
that rarely change; this catches the things that do.

Two details worth knowing:

* **It does not run on `development` → `main` syncs.** Nothing reaches `main`
  without passing here first, so a second run would re-prove the same commits.
  `workflow_dispatch` still runs it on demand.
* **It skips paths that cannot change the bench** — `**.md`, `docs/**`, `.githooks/**`, `.gitignore`, `.gitattributes`, `infra/output/**`.** If it is ever made a
  *required* status check, a skipped run never reports and the merge blocks
  forever — the fix then is a lightweight always-pass job, not removing the
  filter.

`apps/network` and the NoSQL half of `apps/db` are excluded: they need Kafka,
Mongo, CouchDB and Cassandra containers, and mocking them would test the mock.
Those stay verified locally against real services.

---

## Contributing

`main` and `development` both carry GitHub branch protection: a pull request is
required, `enforce_admins` is on, force pushes and branch deletion are disabled,
and linear history is required. A direct push is refused by the server with
`GH006` even under `--no-verify`.

Changes flow one way:

```
feature branch  ->  PR  ->  development  ->  PR  ->  main
```

```bash
git switch development && git pull
git switch -c my-change
# ... work, commit ...
git push -u origin my-change
gh pr create --base development
```

`.githooks/pre-push` refuses a direct push to either protected branch locally,
so the mistake costs a second rather than a round trip. It is a convenience, not
the control — bypassing it only moves the rejection to the server.

**Merge commits are rejected.** Linear history is required, so use squash or
rebase:

```bash
gh pr merge --squash --delete-branch   # feature -> development
gh pr merge --rebase                   # development -> main, keeps the commits
```

`required_approving_review_count` is 0 on purpose: at 1 a solo maintainer cannot
approve their own pull request and the branch deadlocks. Direct pushes are still
refused; you just do not need a second person to merge.

**Never set `BENCH_REUSE_BUILDS=1` while editing app sources.** It reuses a
cached classpath and will happily run stale classes — precisely the failure the
always-build default exists to prevent. It is for sweeps, where nothing between
cells changes the sources.
