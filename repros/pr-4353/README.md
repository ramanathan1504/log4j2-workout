# PR #4353 — Preserve Log4j 1 lookups when converting a properties configuration

https://github.com/apache/logging-log4j2/pull/4353

`Log4j1ConfigurationConverter` used the runtime `Log4j1ConfigurationParser`, which substitutes `${name}` against the converting JVM's system properties (`OptionConverter.substVars` / `findAndSubst`). The converted XML therefore captured the converter host's values, and file-level properties were copied with the Log4j 1 `${name}` syntax, which Log4j 2 does not read as a system property. The fix adds a package-private `Log4j1ConfigurationParser(boolean preserveLookups)` used only by the converter, which rewrites every `${name}` to `${sys:name}`, including `log4j.threshold`. The repro converts `input/log4j.properties` with the converter's `main` (`-Drepro.dir=<converter-host> -Drepro.threshold=DEBUG`), then runs an application on the converted XML with `-Drepro.dir=<deploy-host> -Drepro.threshold=INFO`, and checks where the log file lands.

## Commands

```bash
cd log4j-pr-4353-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `c4b80d72a7`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `File=${repro.dir}/app.log` | `fileName="${sys:repro.dir}/app.log"` | `fileName="…/output/2.26.1/converter-host/app.log"` | FAIL |
| 2.26.1 | property `app.dir=${repro.dir}/data` | `${sys:repro.dir}/data` | `${repro.dir}/data` (copied raw) | FAIL |
| 2.26.1 | `log4j.threshold=${repro.threshold}` | `level="${sys:repro.threshold}"` | `level="DEBUG"` | FAIL |
| 2.26.1 | converted config run on the deploy host | writes `deploy-host/app.log` | `deploy-host` empty, wrote `converter-host/app.log` | FAIL |
| 2.x HEAD ea28f5a78b | `File=${repro.dir}/app.log` | `fileName="${sys:repro.dir}/app.log"` | `fileName="${sys:repro.dir}/app.log"` | PASS |
| 2.x HEAD ea28f5a78b | property `app.dir=${repro.dir}/data` | `${sys:repro.dir}/data` | `${sys:repro.dir}/data` | PASS |
| 2.x HEAD ea28f5a78b | `log4j.threshold=${repro.threshold}` | `level="${sys:repro.threshold}"` | `level="${sys:repro.threshold}"` | PASS |
| 2.x HEAD ea28f5a78b | converted config run on the deploy host | writes `deploy-host/app.log` | `deploy-host/app.log` = `written-by-converted-config` | PASS |

The converted XML of each run is kept in `output/<label>/converted.xml`.
