# PR #4199 — inactive `ConfigurationFactory` stops default discovery from falling back

https://github.com/apache/logging-log4j2/pull/4199

During default resource discovery, `ConfigurationFactory.Factory.getConfiguration(ctx, isTest, name)` walks the factories in order (properties, YAML, JSON, XML). The first factory whose `log4j2.<ext>` resource exists wins. On 2.26.1 that holds even when the factory is inactive: it logs `Found configuration file … for the inactive …` and returns the factory's `null`, so the XML factory later in the same pass is never asked. The fix skips inactive factories in every lookup loop, closes the found source and continues, and keeps the diagnostic. The app-visible trigger is a classpath with `log4j2.json` and `log4j2.xml` but no `jackson-databind`. Fixes #3896.

## Commands

```bash
cd log4j-pr-4199-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`log4j2.xml` (name `FromXml`) sends root INFO to a File appender in `output/<label>/app.log`. `run.sh` refuses to run if `jackson-databind` is on the classpath.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | configuration used | `FromXml` (XmlConfiguration) | `Default@…` (DefaultConfiguration), WARN `No Log4j 2 configuration file found` | FAIL |
| 2.26.1 | INFO event in `app.log` | 1 line | 0 (file not created) | FAIL |
| 2.26.1 | inactive-JSON diagnostic | reported | reported | PASS |
| 2.x HEAD ea28f5a78b | configuration used | `FromXml` | `FromXml` (XmlConfiguration) | PASS |
| 2.x HEAD ea28f5a78b | INFO event in `app.log` | 1 line | 1 line | PASS |
| 2.x HEAD ea28f5a78b | inactive-JSON diagnostic | reported | reported (`…missing dependency or a disabled feature.`) | PASS |

Notes: the Log4j 1.x factories' dynamic activation half of the PR is not covered here.
