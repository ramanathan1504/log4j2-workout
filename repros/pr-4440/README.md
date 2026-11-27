# PR #4440 — reproduction

https://github.com/apache/logging-log4j2/pull/4440 — *Fix CronTriggeringPolicy with evaluateOnStartup=true overwriting logs* (issues #4439, #4449)

**Status: run 2026-11-14 on 2.26.1, PR head `28dbbb2bd9`, and PR head with an inclusive `lastRollForFile`.**
Built by hand: `oss run --pack ~/apache/log4j2-workout repro 4440` failed with
`scenario source not found: …/apps/core-java/…/ExceptionScenario.java` (oss 4.10.6).

## Run

```bash
log4j-pr-4440-repro/run.sh 2.26.1
log4j-pr-4440-repro/run.sh 2.27.0-SNAPSHOT pr-head-28dbbb2bd9   # after ./mvnw -pl log4j-core -am install -DskipTests at the PR head
log4j-pr-4440-repro/run.sh 2.27.0-SNAPSHOT pr-suggestion        # same, with getPrevFireTime(new Date(fileTime + 1)) and the guard removed
```

`RollingFile` with `CronTriggeringPolicy schedule="0 * * * * ?" evaluateOnStartup="true"`, pattern `app.log.%d{yyyy-MM-dd-HH-mm}`.
The app logs `run=<id> seq=<n>` every 250 ms; the oracle counts unique lines across `app.log*` against what was logged.

- **S1 (#4439):** run A from :50 for 20 s (spans fire F), restart 3 s later in the same minute, run B for 62 s (spans F+60).
- **S2 (#4449):** run A from :50 for 20 s (spans F; the scheduled roll creates `app.log` at F), down over F+60, run B from F+68 for 62 s (spans F+120).

## Results (macOS, APFS birth time)

| Version | S1 lost | S2 lost |
|---|---:|---:|
| 2.26.1 | **40** | **40** |
| PR head `28dbbb2bd9` | 0 | **38** |
| PR head + inclusive `lastRollForFile` | 0 | 0 |
| PR head `0b5cd23786` (2026-11-17, inclusive fix applied) | 0 | 0 |

Raw: `output/<version>/result.txt`.

## Mechanism

`RollingFileManager.initialFileTime` rounds the file time to the second (#3872), so a file created by the scheduled roll at F+few ms has file time F.
`CronExpression.getPrevFireTime(F)` is strict and returns F−1 period.
At the PR head the startup roll archives under `lastRollForFile`, so in S2 the file created at 11:25:00 was archived as `app.log.2026-11-14-11-24`,
the name the 11:25 scheduled roll had already used; the 38 lines of run A before 11:25 were overwritten.
With `getPrevFireTime(new Date(fileTime + 1))` the file maps to its own period (11-30 in the run), and the PR's extra `fileTime != lastRegularRoll.getTime()` check is no longer needed.
