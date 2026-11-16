# PR #4116 — Properties config reports a spurious `invalid element or attribute "onMismatch"` for case-variant filter attributes

https://github.com/apache/logging-log4j2/pull/4116

## Mechanism

`PropertiesConfigurationBuilder.createFilter` reads `onMatch`/`onMismatch` by their canonical spelling only.
For `onMisMatch` (the spelling in Hive's configuration, #2791) it passes `null`, the old
`DefaultComponentBuilder.put` stored that `null` as an attribute, and the leftover `onMisMatch=ACCEPT` was added as
a second attribute. Plugin injection matches names case-insensitively, consumes one of the two, and
`checkForRemainingAttributes()` reports the other. The PR removes an attribute instead of storing a `null`.

## Commands

```bash
cd log4j-pr-4116-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2x-head-ea28f5a78b
```

`src/main/resources/log4j2-test.properties` is the PR's test config with a `File` appender in place of `List` and
logger level `INFO`: a `MarkerFilter` on `FULL_PLAN` with `onMatch = DENY` and `onMisMatch = ACCEPT`. The app logs one
marked and one unmarked event; `run.sh` greps the StatusLogger output (`status = WARN`).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | filter built, `onMatch` / `onMismatch` | MarkerFilter, DENY / ACCEPT | MarkerFilter, DENY / ACCEPT | PASS |
| 2.26.1 | `app.log` content | `[unmarked-event]` | `[unmarked-event]` | PASS |
| 2.26.1 | `invalid element or attribute` status messages | 0 | 1: `ERROR MarkerFilter contains an invalid element or attribute "onMismatch"` | FAIL |
| 2.x HEAD ea28f5a78b | filter built, `onMatch` / `onMismatch` | MarkerFilter, DENY / ACCEPT | MarkerFilter, DENY / ACCEPT | PASS |
| 2.x HEAD ea28f5a78b | `app.log` content | `[unmarked-event]` | `[unmarked-event]` | PASS |
| 2.x HEAD ea28f5a78b | `invalid element or attribute` status messages | 0 | 0 | PASS |

## Notes

- On 2.26.1 the filter still behaves correctly: the value that survives injection is `ACCEPT`. The user-visible
  defect is the misleading ERROR naming a correctly-spelled attribute, not dropped or kept events.
