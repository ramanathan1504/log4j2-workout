# PR #4419 — `Configurator.setAllLevels` re-levels sibling loggers; logger mutators ignore `putIfAbsent`

https://github.com/apache/logging-log4j2/pull/4419

`Configurator.setAllLevels(parent, level)` picked child `LoggerConfig`s with `startsWith`, so `setAllLevels("com.foo", TRACE)` also changed `com.foobar` and `com.foobar.child`; the fix adds `isChildLogger` with the dot boundary. The same PR makes `AbstractConfiguration.addLoggerAppender` / `addLoggerFilter` / `setLoggerAdditive` honour the `putIfAbsent` result (#3155): when the key already holds a `LoggerConfig` with a different name, the new config was dropped and the appender went nowhere. Case 5 registers `LoggerConfig("app")` under key `app.service`, then calls `addLoggerAppender` with a `WriterAppender` and logs one event.

## Commands

```bash
cd log4j-pr-4419-repro
./run.sh 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `com.foo` after `setAllLevels(com.foo,TRACE)` | TRACE | TRACE | PASS |
| 2.26.1 | `com.foo.child` | TRACE | TRACE | PASS |
| 2.26.1 | sibling `com.foobar` (was WARN) | WARN | **TRACE** | FAIL |
| 2.26.1 | sibling `com.foobar.child` (was ERROR) | ERROR | **TRACE** | FAIL |
| 2.26.1 | `addLoggerAppender` on mismatched key | event delivered | lost, appenders=[] | FAIL |
| 2.x HEAD ea28f5a78b | `com.foo` | TRACE | TRACE | PASS |
| 2.x HEAD ea28f5a78b | `com.foo.child` | TRACE | TRACE | PASS |
| 2.x HEAD ea28f5a78b | sibling `com.foobar` | WARN | WARN | PASS |
| 2.x HEAD ea28f5a78b | sibling `com.foobar.child` | ERROR | ERROR | PASS |
| 2.x HEAD ea28f5a78b | `addLoggerAppender` on mismatched key | event delivered | delivered, appenders=[SINK] | PASS |
