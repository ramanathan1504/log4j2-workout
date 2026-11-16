# PR #4458 — `${java:…}` throws for an unknown key instead of returning null

https://github.com/apache/logging-log4j2/pull/4458

`JavaLookup.lookup` ended in `default: throw new IllegalArgumentException(key)`. `StrSubstitutor.resolveVariable` catches it, logs `ERROR Resolver failed to lookup java:<key>` with a stack trace, and returns null, so `Interpolator` never reaches its fallback to the configuration `<Properties>` map. The fix returns null like every other lookup. The repro uses `PatternLayout pattern="%m|${java:build}|${java:nosuchkey:-dflt}|${java:version}%n"` with `<Property name="build">fromProperties</Property>`, reads the written line, and counts the status errors on stderr at `StatusLogger.level=WARN`.

## Commands

```bash
cd log4j-pr-4458-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

The second run resolves third-party dependencies at 2.26.1 and replaces every `log4j-*` jar with the `2.27.0-SNAPSHOT` jar built from `2.x` HEAD `ea28f5a78b`, which contains this change.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `${java:build}` falls back to `<Properties>` | fromProperties | `${java:build}` (unresolved) | FAIL |
| 2.26.1 | `${java:nosuchkey:-dflt}` | dflt | dflt | PASS |
| 2.26.1 | `${java:version}` (control) | Java version … | Java version 17.0.14 | PASS |
| 2.26.1 | status `Resolver failed to lookup java:` | 0 | 3 | FAIL |
| 2.x HEAD ea28f5a78b | `${java:build}` falls back to `<Properties>` | fromProperties | fromProperties | PASS |
| 2.x HEAD ea28f5a78b | `${java:nosuchkey:-dflt}` | dflt | dflt | PASS |
| 2.x HEAD ea28f5a78b | `${java:version}` (control) | Java version … | Java version 17.0.14 | PASS |
| 2.x HEAD ea28f5a78b | status `Resolver failed to lookup java:` | 0 | 0 | PASS |

Notes: the `:-dflt` case passes on both: the default value is applied even after the caught exception, so the user-visible baseline damage is the skipped `<Properties>` fallback and the ERROR with stack trace per evaluation.
