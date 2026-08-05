# Issue drafts

Findings from this bench, written to `apache/logging-log4j2`'s bug template and
ready to file **by hand**.

Nothing here is filed. Nothing in this repository ever touches an upstream
project — no issues, no pull requests, no pushes. These are drafts for review.

Already filed, for reference:

- [#4241](https://github.com/apache/logging-log4j2/issues/4241) — `AbstractDatabaseManager` accepts writes after failed startup, and is never shut down
- [#4242](https://github.com/apache/logging-log4j2/issues/4242) — `log4j-cassandra` leaks the DataStax `Cluster`, so the JVM never exits

## Drafts

| File | Finding | Confidence |
|---|---|---|
| `csv-parameter-layout-npe.md` | `CsvParameterLayout` NPEs on any event with no parameters | **High** — source-verified, trivially reproducible |
| `spring-cloud-config-listener-condition.md` | `@ConditionalOnProperty` has no effect on `Log4j2EventListener` | **High** — source-verified, mechanism is unambiguous |
| `appserver-web-context-entry-dropped.md` | `locateContext`'s parent walk drops the `ServletContext` entry | **Medium** — source-verified; whether it is a defect or the intended trade-off is a maintainer's call |
| `configuring-thread-interrupt.md` | Interrupting the configuring thread disables every appender | **Medium** — reproduced here, but the boundary between this and ordinary interrupt semantics needs a second opinion |

All verified against `04c93c1d33` (branch `2.x`). Re-check before filing —
`main` moves.

## Before filing

1. Re-verify against current `2.x` **and** `main`; several of these differ per line.
2. Search open and closed issues for duplicates.
3. Attach a reproduction: `./bench repro <n> --scenario <s> --config <c> --log4j <v>`.
