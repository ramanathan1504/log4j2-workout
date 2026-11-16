# PR #4414 — `LoggerNameLevelRewritePolicy` crosses the logger-name hierarchy boundary

https://github.com/apache/logging-log4j2/pull/4414

`LoggerNameLevelRewritePolicy.rewrite()` matched `logger` with a plain `startsWith`, so a policy for `com.foo` also re-levelled `com.foobar.*` and `com.food`. The fix accepts only the exact name or a `.`-separated descendant (an empty name or one ending in `.` still matches as a prefix). `log4j2.xml` routes Root through a `Rewrite` appender with `logger="com.foo"` mapping INFO to WARN; each of four loggers logs one INFO event and the level written to the file is read back.

## Commands

```bash
cd log4j-pr-4414-repro
./run.sh 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `com.foo` (control) | WARN | WARN | PASS |
| 2.26.1 | `com.foo.Service` (control) | WARN | WARN | PASS |
| 2.26.1 | `com.foobar.Service` | INFO | **WARN** | FAIL |
| 2.26.1 | `com.food` | INFO | **WARN** | FAIL |
| 2.x HEAD ea28f5a78b | `com.foo` (control) | WARN | WARN | PASS |
| 2.x HEAD ea28f5a78b | `com.foo.Service` (control) | WARN | WARN | PASS |
| 2.x HEAD ea28f5a78b | `com.foobar.Service` | INFO | INFO | PASS |
| 2.x HEAD ea28f5a78b | `com.food` | INFO | INFO | PASS |
