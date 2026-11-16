# PR #4372 — `OnStartupTriggeringPolicy` rolled a file the running JVM had created

https://github.com/apache/logging-log4j2/pull/4372

## Mechanism

Since #3872 (2.25.2 / 2.26.0) `RollingFileManager.initialFileTime` rounds the file time to the nearest second, but `OnStartupTriggeringPolicy.initialize` compared it with the unrounded `JVM_START_TIME`. A file created in the same second as the JVM start, below the half-second, gets a time earlier than the start. `initialize` runs again when a second `LoggerContext` picks up the same manager (`AbstractManager.getManager` → `updateData` → `setTriggeringPolicy`), so that context rolls the file this JVM just wrote (FLINK-40833 in the note). The merged fix rounds both sides through `isFileOlderThanStartup`. `Repro.java` starts `LoggerContext` "first" on a fresh directory, logs one line, starts "second" on the same configuration (`OnStartupTriggeringPolicy` with the default `minSize`, `filePattern app-%i.log`), and checks whether `app-1.log` exists. The outcome depends on where the JVM start falls in its second, so `run.sh` runs 30 fresh JVMs and counts. On this machine (macOS, JDK 17) `creationTime()` comes back truncated to the second, so the condition holds whenever the file is created in the JVM's start second.

## Commands

```bash
cd log4j-pr-4372-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt`, and `runs.txt` with the start time, the file time and the outcome of every run.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: runs where the rounded file time is before the JVM start | ≥ 1 | 7/30 | PASS |
| 2.26.1 | second `LoggerContext` rolls the file | 0/30 | 7/30, each `app-1.log` holding `written by the first context` | FAIL |
| 2.x HEAD ea28f5a78b | control: runs where the rounded file time is before the JVM start | ≥ 1 | 10/30 | PASS |
| 2.x HEAD ea28f5a78b | second `LoggerContext` rolls the file | 0/30 | 0/30 | PASS |

## Notes

On 2.26.1 the number of rolls equalled the number of exposed runs exactly (7/7). A first attempt with `minSize="0"` was dropped: it showed one extra roll on 2.26.1 outside the condition, which is the empty-file rename path and not this bug.
