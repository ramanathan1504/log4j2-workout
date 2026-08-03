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
./bench repro 4143 --scenario exceptions --config xml/layout-jsontemplate
```

---

## Layout

```
├── bench                    CLI — the only entry point you need
├── docs/FEATURE-MATRIX.md   the complete coverage catalog (293 plugins)
├── configs/                 the config library, shared by every app
│   ├── xml/  json/  yaml/  properties/   the same configs in all four formats
│   └── log4j1/              log4j.properties + log4j.xml for the 1.x bridge
├── apps/
│   ├── core-java/           no framework — scenarios, custom plugins
│   └── spring-boot-maven/   real Spring Boot app, HTTP-triggered
├── infra/docker-compose.yml Kafka, Mongo, Cassandra, CouchDB, Postgres, MySQL,
│                            syslog-ng, MailHog, Elasticsearch, Kibana
├── repros/                  generated reproductions, one folder per issue/PR
└── scripts/repro.sh         reproduction generator
```

**Configs are a shared library, not per-module.** Any app can load any config via
`-Dlog4j.configurationFile`, which is what makes the version × config × app
matrix work without duplicating a single XML file.

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

Three facts about 3.x that the build encodes, because they surprise people:

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