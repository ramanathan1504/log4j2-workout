# Issue drafts

Findings from this bench, written to `apache/logging-log4j2`'s bug template and
ready to file **by hand**.

Nothing here is filed. Nothing in this repository ever touches an upstream
project — no issues, no pull requests, no pushes. These are drafts for review.

Already filed, for reference:

- [#4241](https://github.com/apache/logging-log4j2/issues/4241) — `AbstractDatabaseManager` accepts writes after failed startup, and is never shut down
- [#4242](https://github.com/apache/logging-log4j2/issues/4242) — `log4j-cassandra` leaks the DataStax `Cluster`, so the JVM never exits
- [#4243](https://github.com/apache/logging-log4j2/issues/4243) — `CsvParameterLayout` NPEs on any event without parameters. **Affects 2.x and 3.x**
- [#4244](https://github.com/apache/logging-log4j2/issues/4244) — `Log4j2EventListener`'s `@ConditionalOnProperty` has no effect

## Drafts

| File | Finding | Confidence | Evidence |
|---|---|---|---|
| `csv-parameter-layout-npe.md` | ~~`CsvParameterLayout` NPEs on any event with no parameters~~ | **filed as #4243** | captured run, 2.26.1 |
| `spring-cloud-config-listener-condition.md` | ~~`@ConditionalOnProperty` has no effect~~ | **filed as #4244** | captured run, 2.26.1 |
| `appserver-web-context-entry-dropped.md` | `locateContext`'s parent walk drops the `ServletContext` entry | **duplicate of #2314** — comment there instead | captured run, 2.26.1 |
| `configuring-thread-interrupt.md` | Interrupting the configuring thread disables every appender | **Low-medium** | **no captured run** — a race, observed once |

Source quotes in all four were read back from the clone, not recalled. Line
numbers match: `CsvParameterLayout:98`, `CSVFormat:2265`,
`Log4j2EventListener:26-27`, `spring.factories:17`,
`ClassLoaderContextSelector:185,201`.

All verified against `04c93c1d33` (branch `2.x`). Re-check before filing —
`main` moves.

## Before filing

1. Re-verify against current `2.x` **and** `main`; several of these differ per line.
2. Search open and closed issues for duplicates.
3. Attach a reproduction: `./bench repro <n> --scenario <s> --config <c> --log4j <v>`.
