# PR #4224 — `RootLogger.Builder` gains the `withFilter(Filter)` alias

https://github.com/apache/logging-log4j2/pull/4224

`LoggerConfig.Builder` has had a deprecated `withFilter(Filter)` alias next to the misspelled `withtFilter(Filter)`, but `LoggerConfig.RootLogger.Builder` only had `withtFilter`. The PR adds the matching alias, delegating to `setFilter`. It is an API addition: code that calls `RootLogger.newRootBuilder().withFilter(...)` does not compile, or fails with `NoSuchMethodError`, on 2.26.1. Fixes #3369.

## Commands

```bash
cd log4j-pr-4224-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`Repro.java` looks the method up reflectively (so one source compiles against both), invokes it with a `ThresholdFilter`, builds, and checks `getFilter()` is the same instance. `LoggerConfig.Builder` is the control.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `RootLogger.Builder.withFilter(Filter)` | exists, sets filter | `NoSuchMethodException` | FAIL |
| 2.26.1 | `LoggerConfig.Builder.withFilter(Filter)` (control) | sets filter | `getFilter()==filter` true | PASS |
| 2.x HEAD ea28f5a78b | `RootLogger.Builder.withFilter(Filter)` | exists, sets filter | `getFilter()==filter` true | PASS |
| 2.x HEAD ea28f5a78b | `LoggerConfig.Builder.withFilter(Filter)` (control) | sets filter | `getFilter()==filter` true | PASS |
