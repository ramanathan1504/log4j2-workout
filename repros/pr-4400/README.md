# PR #4400 — `%d` printed wrong seconds and milliseconds for instants before the epoch

https://github.com/apache/logging-log4j2/pull/4400

## Mechanism

`MutableInstant.initFromEpochMilli` used truncating division, so a negative `epochMilli` produced a negative `nanoOfSecond`, and `InstantPatternDynamicFormatter` computed the cached epoch minute and the seconds field with `/ 60` and `% 60`, which go wrong below zero. The PR uses `Math.floorDiv` / `Math.floorMod` in all three places. `Repro.java` formats `Log4jLogEvent`s with fixed `timeMillis` through `PatternLayout` `%d{yyyy-MM-dd'T'HH:mm:ss.SSS}{UTC}` and compares with `java.time.DateTimeFormatter`.

## Commands

```bash
cd log4j-pr-4400-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `-1500` ms | `1969-12-31T23:59:58.500` | `1969-12-31T23:59:0/.+00` | FAIL |
| 2.26.1 | `-61001` ms | `1969-12-31T23:58:58.999` | `1969-12-31T23:58:0/.00/` | FAIL |
| 2.26.1 | `-86399750` ms | `1969-12-31T00:00:00.250` | `1969-12-31T00:00:+'.)+0` | FAIL |
| 2.26.1 | `-1` ms (first event formatted on the thread) | `1969-12-31T23:59:59.999` | empty string | FAIL |
| 2.26.1 | control `1500` ms | `1970-01-01T00:00:01.500` | `1970-01-01T00:00:01.500` | PASS |
| 2.x HEAD ea28f5a78b | `-1500` ms | `1969-12-31T23:59:58.500` | `1969-12-31T23:59:58.500` | PASS |
| 2.x HEAD ea28f5a78b | `-61001` ms | `1969-12-31T23:58:58.999` | `1969-12-31T23:58:58.999` | PASS |
| 2.x HEAD ea28f5a78b | `-86399750` ms | `1969-12-31T00:00:00.250` | `1969-12-31T00:00:00.250` | PASS |
| 2.x HEAD ea28f5a78b | `-1` ms (first event formatted on the thread) | `1969-12-31T23:59:59.999` | empty string | FAIL |
| 2.x HEAD ea28f5a78b | control `1500` ms | `1970-01-01T00:00:01.500` | `1970-01-01T00:00:01.500` | PASS |

## Notes

The `-1` ms case is a separate defect the PR does not touch: `InstantPatternThreadLocalCachedFormatter.createEpochInstantAndBuffer()` seeds each thread's cache with `{-1L, new StringBuilder()}`, so the first instant on a thread whose cache key is `-1` (epoch millisecond `-1` for the millisecond cache, any instant in `[-1000, -1]` ms for the second-precision prefix cache) matches the sentinel and appends the empty buffer. Every other pre-epoch value is fixed.
