# PR #4165 — `maxRandomDelay` on rolling appenders delays the asynchronous rollover action chain

https://github.com/apache/logging-log4j2/pull/4165

`RollingFileManager` replaces its unbounded `ThreadPoolExecutor` with a single-thread `ScheduledThreadPoolExecutor` and schedules the asynchronous action chain (compression) after `ThreadLocalRandom.nextLong(maxRandomDelay * 1000 + 1)` ms. The attribute is new on `RollingFile` and `RollingRandomAccessFile`; the unreleased `maxCompressionDelaySeconds` on `DefaultRolloverStrategy` (#4071) is removed. Per the note, this reworks #4071 along @ppkarwasz's suggestion to delay the chain rather than add a delay action.

`Repro.java` drives two `RollingFile` appenders (size 1 KB, `.gz` pattern), one with `maxRandomDelay="2"` and a control without, rolls six times each and measures the time from the triggering event until the `.gz` appears. A last rollover is followed immediately by `LoggerContext.stop()` to check that a pending delayed compression is not lost. Every archive is checked with `gzip -t`.

## Commands

```bash
cd log4j-pr-4165-repro
./run.sh 2.26.0 2.26.0
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.0 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | `maxRandomDelay="2"` delays (ms) | some > 250, all ≤ 2000 | `[7, 4, 9, 1, 1, 2]`; StatusLogger `ERROR RollingFile contains an invalid element or attribute "maxRandomDelay"` | FAIL |
| 2.26.0 | control, no attribute | < 250 | `[1, 1, 1, 1, 1, 1]` | PASS |
| 2.26.0 | stop right after rollover | 7 `.gz`, none left raw | 7 `.gz`, 0 raw, stop 2 ms | PASS |
| 2.x HEAD ea28f5a78b | `maxRandomDelay="2"` delays (ms) | some > 250, all ≤ 2000 | `[902, 1371, 1040, 1322, 969, 1928]` | PASS |
| 2.x HEAD ea28f5a78b | control, no attribute | < 250 | `[6, 2, 3, 9, 2, 6]` | PASS |
| 2.x HEAD ea28f5a78b | stop right after rollover | 7 `.gz`, none left raw | 7 `.gz`, 0 raw, stop 335 ms | PASS |

All 13 archives per run pass `gzip -t` (deleted afterwards to keep the output small).

Notes: `stop()` waits for a pending delayed action (335 ms here, up to `maxRandomDelay` seconds), so shutdown can now take up to that long. Delays are random; one run, not a distribution.
