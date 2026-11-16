# PR #4256 — Plugin annotation processors print a `Note:` on every compile

https://github.com/apache/logging-log4j2/pull/4256

`PluginProcessor` and `GraalVmProcessor` report each descriptor they write as a `Diagnostic.Kind.NOTE`, filtered by the `log4j.plugin.processor.minAllowedMessageKind` option whose default was `NOTE`, so every project compiling a `@Plugin` got two notes per compile (#4225). The PR moves the default to `ERROR`, and reports an unrecognized option value through the `Messager` directly instead of through the filter that depends on that value. `Repro` compiles a one-class `@Plugin` lookup in-process with `javax.tools` and processor discovery from the classpath, and counts the diagnostics whose text names either processor.

## Commands

```bash
cd log4j-pr-4256-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

JBR 17.0.14; every case passes `-Alog4j.graalvm.groupId=repro -Alog4j.graalvm.artifactId=repro`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | no option | 0 notes | **2 notes** | FAIL |
| 2.26.1 | control: `minAllowedMessageKind=NOTE` | 2 notes | 2 notes | PASS |
| 2.26.1 | typo `minAllowedMessageKind=WARNINGS` | 0 notes, 2 warnings | **2 notes, 1 warning** (PluginProcessor only, "using default `NOTE`") | FAIL |
| 2.x HEAD ea28f5a78b | no option | 0 notes | 0 notes | PASS |
| 2.x HEAD ea28f5a78b | control: `minAllowedMessageKind=NOTE` | 2 notes | 2 notes | PASS |
| 2.x HEAD ea28f5a78b | typo `minAllowedMessageKind=WARNINGS` | 0 notes, 2 warnings | 0 notes, 2 warnings, both "using default `ERROR`" | PASS |

Notes: the 2.x HEAD messages also carry a `[Log4j]` prefix that 2.26.1 lacks. The GraalVmProcessor typo warning is absent on 2.26.1; whether that comes from this PR or another change on 2.x was not isolated.
