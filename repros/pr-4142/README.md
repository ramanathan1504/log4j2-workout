# PR #4142 — Fix properties configuration support for per-logger properties

https://github.com/apache/logging-log4j2/pull/4142

In the properties format, `PropertiesConfigurationBuilder.createLogger()` and `createRootLogger()` returned the logger
component without calling `processRemainingProperties()`, so nested components under `logger.<id>.*` and
`rootLogger.*` other than appender refs and filters were never added. A `logger.<id>.property.*` (or
`rootLogger.property.*`) `Property` was dropped without any StatusLogger message, and the value never reached the event's
context data. The fix removes the consumed `levelAndRefs` key and hands the rest to `processRemainingProperties()`, as
the appender and filter paths already did.

## Command

```bash
cd log4j-pr-4142-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`src/main/resources/log4j2.properties` declares `logger.db.property` (`subsystem=Database`) on logger `app.db` and
`rootLogger.property` (`region=eu-west`); the pattern prints `%X{subsystem}` and `%X{region}`. `Repro.java` logs one
event per logger and reads `app.log` back. StatusLogger runs at WARN and its lines are counted.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `logger.db.property` | `subsystem=Database` | `app.db\|db-event\|subsystem=\|region=` | FAIL |
| 2.26.1 | `rootLogger.property` | `region=eu-west` | `app.web\|root-event\|subsystem=\|region=` | FAIL |
| 2.x HEAD ea28f5a78b | `logger.db.property` | `subsystem=Database` | `app.db\|db-event\|subsystem=Database\|region=` | PASS |
| 2.x HEAD ea28f5a78b | `rootLogger.property` | `region=eu-west` | `app.web\|root-event\|subsystem=\|region=eu-west` | PASS |

Notes: 2.26.1 emits no StatusLogger line at WARN for the dropped properties (0 lines on both runs); the loss is silent.
