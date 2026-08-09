# PR #4246 — reproduction, run by hand

https://github.com/apache/logging-log4j2/pull/4246 · fixes issue #4241 (yours)

Two things to demonstrate, and they pull in opposite directions:

- **A.** the bug the PR fixes — a manager whose startup failed keeps taking writes
- **B.** the regression the fix introduces — `ignoreExceptions="false"` stops working

Needs **no database service**. Everything below runs against the `jpa` app.

---

## Why JPA, and not Cassandra or NoSQL

| Manager | `startupInternal` can fail? | `writeInternal` guards `!isRunning()`? |
|---|---|---|
| `CassandraManager` | ✅ `cluster.connect()` | ❌ **none — this is the NPE in #4241** |
| `JpaDatabaseManager` | ✅ `Persistence.createEntityManagerFactory` | ✅ throws `AppenderLoggingException` |
| `NoSqlDatabaseManager` | ❌ empty method | ✅ throws `AppenderLoggingException` |
| `JdbcDatabaseManager` | ❌ empty method | n/a |

Cassandra shows **A** but needs a live node (and the `cassandra-init` compose
service, not `cassandra` — the bare node stores nothing silently).

JPA is the only manager that fails startup for real with nothing external down,
**and** already handles the aftermath correctly. So it shows **B** — which is the
half worth arguing about on the PR, because the Cassandra half is not in dispute.

---

## Config

`configs/xml/repro-jpa-failed-startup.xml`. Two attributes carry the whole repro:

```xml
<JPA name="Jpa"
     persistenceUnitName="benchLoggingDoesNotExist"   <!-- not in persistence.xml -->
     entityClassName="org.apache.logging.bench.jpa.BenchLogEntity"
     bufferSize="0"
     ignoreExceptions="false"/>                        <!-- the point of the repro -->
```

`bufferSize="0"` matters: above 0 the appender batches and `write()` takes the
`buffer(event)` branch instead of `writeThrough`, which is not the path under test.

---

## B — the regression. Run this one.

### B1. Baseline, against a release. Do this first.

`--install` overwrites `2.27.0-SNAPSHOT`, so a baseline taken afterwards measures
the PR twice.

```bash
cd ~/apache/log4j2-workout
./bench run jpa --config xml/repro-jpa-failed-startup --log4j 2.26.1 messages
```

**Expect:** the run fails loudly. `AppenderLoggingException: Cannot write logging
event; JPA manager not connected to the database.` propagates out of the logging
call on the application thread, because `ignoreExceptions="false"`.

If it exits 0 and looks clean, do not conclude the config is wrong — raise the
level before assuming anything:

```bash
BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' \
  ./bench run jpa --config xml/repro-jpa-failed-startup --log4j 2.26.1 messages
```

Keep the output:

```bash
./bench run jpa --config xml/repro-jpa-failed-startup --log4j 2.26.1 messages \
  > repros/pr-4246/output/2.26.1-baseline.log 2>&1
```

### B2. Install the PR

Your clone has a staged change to `AsyncTraceContextBenchmark.java`, so stash
before switching branches:

```bash
cd ~/apache/logging-log4j2
git stash push -m "pre-4246" log4j-perf-test/src/main/java/org/apache/logging/log4j/perf/jmh/AsyncTraceContextBenchmark.java

cd ~/apache/log4j2-workout
./bench pr 4246 --checkout --install        # publishes the PR as 2.27.0-SNAPSHOT
```

### B3. After

```bash
./bench run jpa --config xml/repro-jpa-failed-startup messages \
  > repros/pr-4246/output/pr-4246-after.log 2>&1
```

**Expect:** no `AppenderLoggingException`. The run completes. One
`StatusLogger` line — *"JpaDatabaseManager Jpa is not running; skipping database
write until startup succeeds"* — and then nothing, for the life of the process.

**That difference is the finding.** A user who wrote `ignoreExceptions="false"`
asked to be told when logging to the database fails, and after this PR they are
not told.

### B4. Restore

```bash
cd ~/apache/logging-log4j2
git switch 2.x && git stash pop
mvn install -DskipTests      # put the real 2.27.0-SNAPSHOT back
```

That last step is easy to skip and expensive to forget — every later `./bench`
run defaulting to `2.27.0-SNAPSHOT` silently tests PR #4246 until you do.

---

## A — the underlying bug, if you want it too

Same config, but drop `ignoreExceptions="false"` to see what an ordinary user
gets, and compare Cassandra's behaviour:

```bash
docker compose -f infra/compose.yaml up -d cassandra-init      # NOT `cassandra`
./bench run nosql --config xml/appender-nosql --log4j 2.26.1
```

Then kill the node mid-run so `cluster.connect()` fails on reconfiguration.
`CassandraManager.writeInternal` has no `!isRunning()` guard, so it walks
`columnMappings` and dereferences a `preparedStatement` that `startupInternal`
never assigned — one NPE per event, each burying the original cause.

Your existing captures of this: `docs/evidence/nosql-cassandra-startup.log`,
`docs/evidence/nosql-hang-threads.txt`.

---

## What to conclude

If B reproduces, the PR should be asked to drop the `write()` change entirely and
instead add the missing guard to `CassandraManager.writeInternal` — three lines,
one module, no base-class contract change, and #4241 still closes.

Full reasoning and a paste-ready comment:
[`docs/pr-reviews/4246-database-manager-failed-startup.md`](../../docs/pr-reviews/4246-database-manager-failed-startup.md)
