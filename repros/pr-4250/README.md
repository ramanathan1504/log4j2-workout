# PR #4250 — NPE in `PluginElementVisitor` on an unresolved child element

https://github.com/apache/logging-log4j2/pull/4250

`PluginElementVisitor` walks a node's children when injecting array-typed `@PluginElement`s and calls `child.getType().getElementName()`. A child whose plugin type could not be resolved (here `appender.Stdout.layout.type = ThisLayoutDoesNotExist`) has a `null` type, so the visitor throws an NPE, `ConsoleAppender` is never built, and the root logger loses its only appender. The fix skips such children with an ERROR `Ignoring unresolved element … in …`, so the appender is built with its default layout. Fixes #4248.

## Commands

```bash
cd log4j-pr-4250-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`src/main/resources/unresolved-layout.properties` is the PR's test configuration, with `status = WARN` and root at INFO. The oracle greps the StatusLogger output and checks the appender built and the line printed.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | NPE during configuration | none | `NullPointerException … "childType" is null` (2 hits) | FAIL |
| 2.26.1 | `StdOut` appender built | yes | absent; `Unable to locate appender "StdOut"` | FAIL |
| 2.26.1 | INFO line on stdout | 1 | 0 | FAIL |
| 2.x HEAD ea28f5a78b | NPE during configuration | none | 0 | PASS |
| 2.x HEAD ea28f5a78b | `StdOut` appender built | yes | yes, default `PatternLayout` | PASS |
| 2.x HEAD ea28f5a78b | INFO line on stdout | 1 | 1 | PASS |

Notes: the fixed build also logs `Ignoring unresolved element ThisLayoutDoesNotExist in Console.` and `appender Console has no parameter that matches element ThisLayoutDoesNotExist`, so the unresolved layout is still reported twice at ERROR.
