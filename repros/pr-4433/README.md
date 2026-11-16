# PR #4433 — restore `LoggerConfig(String, Level, boolean)` on 3.x

https://github.com/apache/logging-log4j2/pull/4433

Commit `35c0f02012` removed the config-less `LoggerConfig(String, Level, boolean)` constructor on `main`, which Spring Boot's `LevelSetLoggerConfig` calls from `Log4J2LoggingSystem.setLogLevel`. The PR restores it as `@Deprecated`, taking the configuration of the `LoggerContext` for `LoggerConfig`'s class loader. The repro does what Spring Boot does, through reflection so it compiles against 3.0.0-beta3: construct `LoggerConfig("com.example", DEBUG, true)`, `addLogger`, `updateLoggers`, then log at DEBUG and read the file back.

## Commands

```bash
cd log4j-pr-4433-repro
./run.sh 3.0.0-beta3
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-main main-HEAD-2bb673f650
```

`log4j-api` 2.24.3; `-Dlog4j.configuration.location`; core, plugins and kit swapped from the jars directory (listed in `result.txt`).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | `LoggerConfig(String, Level, boolean)` | present | `NoSuchMethodException` | FAIL |
| 3.0.0-beta3 | DEBUG `com.example` after `addLogger` | written | absent | FAIL |
| 3.0.0-beta3 | control: no DEBUG before, INFO `other` written | false / true | false / true | PASS |
| main HEAD 2bb673f650 | `LoggerConfig(String, Level, boolean)` | present | present | PASS |
| main HEAD 2bb673f650 | DEBUG `com.example` after `addLogger` | written | written | PASS |
| main HEAD 2bb673f650 | control | false / true | false / true | PASS |
