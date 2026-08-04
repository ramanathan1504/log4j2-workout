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

---

## Coverage

`docs/FEATURE-MATRIX.md` is the authoritative catalog — 293 plugins across 59
modules, extracted from the source clone rather than from documentation, with a
gap list in §16 tracking what the bench does not yet exercise.