# PR #4340 — Compute AsyncLogger location when the caller passes null

https://github.com/apache/logging-log4j2/pull/4340

`AsyncLogger.log(Level, Marker, String fqcn, StackTraceElement location, Message, Throwable)` handed the `location` argument straight to the translator. The `LogBuilder` path (`logger.atInfo().log(...)` without `withLocation()`) passes `null`, so with `includeLocation="true"` the event had no source, while `logger.info(...)` goes through `logMessage` and computes it. The fix falls back to `calcLocationIfRequested(fqcn)` when `location` is null. The repro runs with `AsyncLoggerContextSelector` (disruptor 3.4.4), root `includeLocation="true"`, and a `File` appender with `%m|%C|%M|%L`.

## Commands

```bash
cd log4j-pr-4340-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `4d49b758e7`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `atInfo().log(...)` | `Repro.main` | `class=? method= line=` | FAIL |
| 2.26.1 | control: `info(...)` | `Repro.main` | `class=Repro method=main line=13` | PASS |
| 2.26.1 | control: `atInfo().withLocation().log(...)` | `Repro.main` | `class=Repro method=main line=14` | PASS |
| 2.x HEAD ea28f5a78b | `atInfo().log(...)` | `Repro.main` | `class=Repro method=main line=12` | PASS |
| 2.x HEAD ea28f5a78b | control: `info(...)` | `Repro.main` | `class=Repro method=main line=13` | PASS |
| 2.x HEAD ea28f5a78b | control: `atInfo().withLocation().log(...)` | `Repro.main` | `class=Repro method=main line=14` | PASS |

The logger implementation is `org.apache.logging.log4j.core.async.AsyncLogger` in both runs.
