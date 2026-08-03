# Issue #2073 Reproduction Test Report

## 1) Scope and Goal

This document captures a standalone reproduction test for the reported NullPointerException in Log4j `ConfigurationScheduler.scheduleWithCron(...)` / `CronRunnable` during frequent cron scheduling.

Original report characteristics:

- RollingFileAppender created repeatedly
- Cron schedule: `0/1 0/1 * 1/1 * ? *` (every second)
- Random NPE path includes:
  - `ConfigurationScheduler$CronRunnable.toString(...)`
  - `ConfigurationScheduler.toString(...)`
  - `CronTriggeringPolicy.initialize(...)`

Goal of this run:

- Execute a standalone stress harness against two Log4j versions (`2.17.2`, `2.26.0`) using explicit runtime classpaths.
- Detect whether the Issue #2073 style NPE reproduces.

## 2) Harness Used

- Class: `com.playground.Issue2073Standalone`
- Source: `core-java-test/src/main/java/com/playground/Issue2073Standalone.java`

Important harness behavior:

- Uses cron schedule `0/1 0/1 * 1/1 * ? *`
- Creates many `RollingFileAppender` instances under concurrent workers
- Starts/stops appenders in loops
- Captures first failure, if any
- Classifies failure as `reproduced` only when stack trace matches Issue #2073 signature

## 3) Environment and Inputs

Verified from executed runs:

- Workspace: `/Users/ramanathan/apache/log4j2-workout`
- Java executable used:
  - `/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home/bin/java`
- Stress inputs:
  - `iterationsPerWorker=300`
  - `workers=4`
  - total planned attempts per run: `1200`

Compared Log4j versions (runtime classpath selection):

- `org.apache.logging.log4j:log4j-core:2.17.2`
- `org.apache.logging.log4j:log4j-api:2.17.2`
- `org.apache.logging.log4j:log4j-core:2.26.0`
- `org.apache.logging.log4j:log4j-api:2.26.0`

## 4) Commands Executed

```bash
cd /Users/ramanathan/apache/log4j2-workout
mvn -pl core-java-test -am -DskipTests compile -Dlog4j.version=2.26.0

JAVA=/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home/bin/java

CP=core-java-test/target/classes:/Users/ramanathan/.m2/repository/org/apache/logging/log4j/log4j-core/2.17.2/log4j-core-2.17.2.jar:/Users/ramanathan/.m2/repository/org/apache/logging/log4j/log4j-api/2.17.2/log4j-api-2.17.2.jar
$JAVA -cp "$CP" com.playground.Issue2073Standalone 300 4 > /tmp/issue2073-2.17.2.out 2>&1

CP2=core-java-test/target/classes:/Users/ramanathan/.m2/repository/org/apache/logging/log4j/log4j-core/2.26.0/log4j-core-2.26.0.jar:/Users/ramanathan/.m2/repository/org/apache/logging/log4j/log4j-api/2.26.0/log4j-api-2.26.0.jar
$JAVA -cp "$CP2" com.playground.Issue2073Standalone 300 4 > /tmp/issue2073-2.26.0.out 2>&1
```

Exception scan command:

```bash
grep -n "NullPointerException\|Exception" /tmp/issue2073-2.17.2.out /tmp/issue2073-2.26.0.out || true
```

## 5) Raw Outputs

### 5.1 Log4j 2.17.2 (`/tmp/issue2073-2.17.2.out`)

```text
issue=2073
log4jVersion=2.17.2
schedule=0/1 0/1 * 1/1 * ? *
workers=4
iterationsPerWorker=300
attempts=1200
successfulBuilds=1200
durationMs=25376
logDir=/Users/ramanathan/apache/log4j2-workout/logs/issue-2073-standalone
status=not-reproduced
```

### 5.2 Log4j 2.26.0 (`/tmp/issue2073-2.26.0.out`)

```text
issue=2073
log4jVersion=2.26.0
schedule=0/1 0/1 * 1/1 * ? *
workers=4
iterationsPerWorker=300
attempts=1200
successfulBuilds=1200
durationMs=33524
logDir=/Users/ramanathan/apache/log4j2-workout/logs/issue-2073-standalone
status=not-reproduced
```

### 5.3 Exception Scan Result

- `grep` found no `NullPointerException` and no `Exception` strings in either output file.

## 6) Result Summary

- Issue #2073 NPE was **not reproduced** in this test window.
- Both versions completed all planned attempts successfully:
  - `2.17.2`: `1200/1200`
  - `2.26.0`: `1200/1200`

## 7) Comparison Table

| Metric            |         2.17.2 |         2.26.0 |
|-------------------|---------------:|---------------:|
| Attempts          |           1200 |           1200 |
| Successful builds |           1200 |           1200 |
| Failures          |              0 |              0 |
| Status            | not-reproduced | not-reproduced |
| Duration (ms)     |          25376 |          33524 |

## 8) Notes and Limits

- This run used macOS + JBR 17 in the current workspace.
- Original incident mentions Ubuntu 22.04 + Temurin 11.
- The run is high-frequency and concurrent, but bounded (`300 x 4`) rather than an infinite loop.
- Since the issue is random by nature, non-reproduction in one window does not prove impossibility.

## 9) Recommended Next Verification (Optional)

For higher confidence, execute additional stress profiles:

```bash
# same classpath strategy, higher stress
$JAVA -cp "$CP"  com.playground.Issue2073Standalone 1000 8 > /tmp/issue2073-2.17.2-hi.out 2>&1
$JAVA -cp "$CP2" com.playground.Issue2073Standalone 1000 8 > /tmp/issue2073-2.26.0-hi.out 2>&1

$JAVA -cp "$CP"  com.playground.Issue2073Standalone 2000 8 > /tmp/issue2073-2.17.2-xhi.out 2>&1
$JAVA -cp "$CP2" com.playground.Issue2073Standalone 2000 8 > /tmp/issue2073-2.26.0-xhi.out 2>&1
```

If needed, mirror original environment exactly (Ubuntu 22.04 + Temurin 11) and repeat the same matrix.

