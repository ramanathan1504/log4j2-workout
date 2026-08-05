# Issues raised upstream

`apache/logging-log4j2`. All found with this bench.

## Filed

### [#4241](https://github.com/apache/logging-log4j2/issues/4241) — `AbstractDatabaseManager` accepts writes after a failed startup, and is never shut down

The same `isRunning()` flag is ignored on the write path and honoured on the
shutdown path — backwards in both directions.

`write()` does not check it, so a manager whose `startupInternal()` threw keeps
receiving events and NPEs per event on state it never assigned, burying the one
status line that named the real cause. `shutdown()` *does* check it, so that
manager is never cleaned up and leaks whatever startup built before throwing.

**Affects 2.x and 3.x.** Not specific to any subclass — the shape of every
`AbstractDatabaseManager` whose startup fails, so JDBC, JPA and NoSQL alike.

Source: `AbstractDatabaseManager:227-241, 297-303`.

---

### [#4242](https://github.com/apache/logging-log4j2/issues/4242) — `log4j-cassandra` leaks the DataStax `Cluster`, so the JVM never exits

Concrete case of #4241. When `CassandraManager.startupInternal()` fails,
`shutdownInternal()` never runs, so `cluster.close()` never runs. The DataStax
3.x `Cluster` holds **non-daemon** threads, so the JVM cannot exit.

`LogManager.shutdown()` reports `all resources released: true` having released
nothing. A thread dump 8s after the run finished showed five surviving threads,
each carrying the configured `clusterName`.

**2.x only** — `log4j-cassandra` has no 3.x release.

---

### [#4243](https://github.com/apache/logging-log4j2/issues/4243) — `CsvParameterLayout` throws NPE on any event without parameters

`toSerializable` passes `Message.getParameters()` straight to
`CSVFormat.printRecord` with no null check. It is null for `SimpleMessage` —
so every plain `logger.info("text")` throws. Only `IOException` is caught, so
the NPE escapes to the appender: one per event, no output, JVM still exits 0.

A parameterised call works, so a layout that passes testing can fail on the
first plain-text message in production.

**Affects 2.x and 3.x:**

- 2.x — `log4j-core/.../core/layout/CsvParameterLayout.java:98`
- 3.x — `log4j-csv/.../csv/layout/CsvParameterLayout.java:104`

The 3.x copy moved module and gained a recycler; the null check is still absent.

---

## Drafted, not filed

In `docs/issue-drafts/`. Written to the project's bug template, each with a
reproduction.

| Draft | Evidence | State |
|---|---|---|
| `@ConditionalOnProperty` has no effect on `Log4j2EventListener` | captured run, 2.26.1 | ready to file |
| `locateContext` drops the `ServletContext` entry | captured both sides | decide defect vs intended trade-off first |
| Interrupting the configuring thread disables every appender | **none — a race, seen once** | hold until reproducible on demand |

## Before filing anything further

1. Re-verify against current `2.x` **and** `main` — several of these differ per line, and #4243 turned out to affect both when the draft assumed one.
2. Search open and closed issues for duplicates.
3. Attach a reproduction: `./bench repro <n> --scenario <s> --config <c> --log4j <v>`.
