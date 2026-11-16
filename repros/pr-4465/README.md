# PR #4465 — JUL mutator warnings repeated on every call, without naming the caller

https://github.com/apache/logging-log4j2/pull/4465

With `java.util.logging.manager=org.apache.logging.log4j.jul.LogManager` and the default `ApiLoggerAdapter`, `ApiLogger.setLevel`, `setUseParentHandlers`, `addHandler` and `removeHandler` each logged a multi-line status WARN on every call. The fix routes them through `warnMutatorAccess`: a new `log4j2.julMutatorAccess` property (`warn-once` default, `warn`, `ignore`), one process-wide warning in the default mode, and the calling frame named in the message. The repro makes the four calls on two JUL loggers (8 calls) and counts ``Ignoring call to `j.u.l.Logger`` on stderr at `StatusLogger.level=WARN`, once per mode.

## Commands

```bash
cd log4j-pr-4465-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

The second run resolves third-party dependencies at 2.26.1 and replaces every `log4j-*` jar with the `2.27.0-SNAPSHOT` jar built from `2.x` HEAD `ea28f5a78b`, which contains this change.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | default: warnings for 8 calls | 1 | 8 | FAIL |
| 2.26.1 | default: warning names `Repro` | ≥ 1 | 0 | FAIL |
| 2.26.1 | `julMutatorAccess=warn` (control) | 8 | 8 | PASS |
| 2.26.1 | `julMutatorAccess=ignore` | 0 | 8 (property unknown) | FAIL |
| 2.26.1 | `getLevel()` after `setLevel(FINE)` (control) | FINE | FINE | PASS |
| 2.x HEAD ea28f5a78b | default: warnings for 8 calls | 1 | 1 | PASS |
| 2.x HEAD ea28f5a78b | default: warning names `Repro` | ≥ 1 | 1 (`from `Repro.main(Repro.java:11)``) | PASS |
| 2.x HEAD ea28f5a78b | `julMutatorAccess=warn` (control) | 8 | 8 | PASS |
| 2.x HEAD ea28f5a78b | `julMutatorAccess=ignore` | 0 | 0 | PASS |
| 2.x HEAD ea28f5a78b | `getLevel()` after `setLevel(FINE)` (control) | FINE | FINE | PASS |

Notes: the merged message prints the full `StackTraceElement` (`Repro.main(Repro.java:11)`), not only the class name the reviewed head used.
