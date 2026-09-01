# Log4j issue #4275 reproduction

https://github.com/apache/logging-log4j2/issues/4275

> AsyncLogger ignores `Thread.setName()` changes despite the UNCACHED strategy since 2.25.0

## Reproduction

```bash
unzip log4j-issue-4275-repro.zip
cd log4j-issue-4275-repro
./run.sh 2.24.1                  # the last good version
./run.sh 2.26.1                  # or any other
./run.sh 2.26.1 CACHED           # any ThreadNameCachingStrategy
./run.sh 2.26.1 UNCACHED false   # and with thread-locals disabled
```

The project is standalone — `log4j-api`, `log4j-core` and `disruptor`, no
parent POM and no reference to the bench it came from. `run.sh` forks a real
JVM (`exec:exec`), so the system properties are set before Log4j initialises
rather than after Maven has already started.

## Verification matrix

`log4j2.asyncLoggerThreadNameStrategy=UNCACHED`, full async
(`AsyncLoggerContextSelector`), pattern `[%t] %m%n`:

| Log4j version | Result | Second event's `%t` |
|---|:---:|---|
| `2.24.1` | ✅ PASS | `second-job` |
| `2.25.4` | ❌ FAIL | `first-job` |
| `2.25.5` | ❌ FAIL | `first-job` |
| `2.26.0` | ❌ FAIL | `first-job` |
| `2.26.1` | ❌ FAIL | `first-job` |
| `2.27.0-SNAPSHOT` | ❌ FAIL | `first-job` |

Confirmed: still present at the head of `main`. Full output per version is
under `output/`, and `2.25.0` itself is not on this pack's version axis — the
break is bracketed by `2.24.1` passing and `2.25.4` failing, which agrees with
the reporter's `2.24.3` / `2.25.0` pair.

## What the reporter did not mention

Three things the bench turned up that widen the report.

**1. `%tp` is stale too, not just `%t`.** `RingBufferLogEventTranslator`
freezes `threadId`, `threadName` *and* `threadPriority` at construction. A task
that calls `Thread.setPriority()` gets the same staleness:

```
                       full async, 2.24.1, UNCACHED
logged [first-job  tid=22 prio=3] actual name=first-job  priority=3
logged [second-job tid=22 prio=8] actual name=second-job priority=8

                       full async, 2.26.1, UNCACHED
logged [first-job  tid=22 prio=3] actual name=first-job  priority=3
logged [first-job  tid=22 prio=3] actual name=second-job priority=8
```

`threadId` is genuinely per-thread and unaffected; the other two are not.

**2. It is not opt-in — the broken value is the default on any modern JDK.**

```java
static final ThreadNameCachingStrategy DEFAULT_STRATEGY = isAllocatingThreadGetName() ? CACHED : UNCACHED;
```

`isAllocatingThreadGetName()` returns `Constants.JAVA_MAJOR_VERSION < 8` off
Java 8, so `UNCACHED` is the default everywhere from Java 9 up. The status
logger confirms it:

```
DEBUG AsyncLogger.ThreadNameStrategy=UNCACHED (user specified UNCACHED, default is UNCACHED)
```

So every full-async application on a current JDK is on the path the property
was meant to select, whether or not it sets the property.

**3. `log4j2.enable.threadlocals=false` does not route around it.**

The issue's example sets `log4j2.enableThreadlocals=true`, which reads as if
`false` would take the vararg path — where `AsyncLogger.translateTo` still
calls `THREAD_NAME_CACHING_STRATEGY.getThreadName()` and is correct. It does
not. `AsyncLogger.getTranslatorType()` switches on
`AsyncLoggerDisruptor.isUseThreadLocals()`, a field defaulting to `true` and
only ever set by `AsyncLoggerContext.setUseThreadLocals()` from the servlet
listener. With `-Dlog4j2.enable.threadlocals=false` and `log4j2.debug=true`:

```
TRACE [AsyncContext@b1a58a3] AsyncLoggers use a threadlocal translator
```

Outside a web application there is no configuration that avoids this.

## What is not affected

| Path | Version measured | `%t` / `%tp` | Why |
|---|---|:---:|---|
| Full async (`AsyncLoggerContextSelector`) | 2.26.1, 2.27.0-SNAPSHOT | ❌ stale | `RingBufferLogEventTranslator` fields, never refreshed |
| Mixed async (`AsyncRoot` / `AsyncLoggerConfig`) | 2.26.1, 2.27.0-SNAPSHOT | ✅ fresh | goes through `ReusableLogEventFactory`, which still honours `UNCACHED` |
| Full async on Log4j 3 | 3.0.0-SNAPSHOT | ✅ fresh | `AsyncLogger.doLog` calls `translator.updateThreadValues()` unconditionally |

All three rows measured, not read off the source. `probe/run-probe.sh <version>`
runs the first two side by side; `output/probe-*.log` holds what they printed,
including `3.0.0-SNAPSHOT` built from `~/apache/log4j-main`.

```
                    mixed async, 2.26.1, UNCACHED
logged [first-job  tid=22 prio=3] actual name=first-job  priority=3
logged [second-job tid=22 prio=8] actual name=second-job priority=8

                  full async, 3.0.0-SNAPSHOT, UNCACHED
logged [first-job  tid=28 prio=3] actual name=first-job  priority=3
logged [second-job tid=28 prio=8] actual name=second-job priority=8
```

## Cause

The reporter's diagnosis is right. `dc6c53ab7b` (PR #3171, released in 2.25.0)
dropped `initTranslatorThreadValues(translator)` from both
`AsyncLogger.logWithThreadLocalTranslator` overloads and deprecated
`RingBufferLogEventTranslator.updateThreadValues()`, on the reasoning that a
translator is only ever used by the thread that created it. That reasoning
holds for the `Thread` instance and so for `threadId`; it does not hold for
`getName()` or `getPriority()`, which are mutable on a live thread and which
thread pools routinely rewrite per task.

The result is that `ThreadNameCachingStrategy` is now dead on the only path
full-async logging actually takes: `CACHED` and `UNCACHED` produce identical
output, and both produce `CACHED` behaviour.

Log4j 3 already has the shape of a fix — `AsyncLogger.doLog` in
`log4j-async-logger` calls `updateThreadValues()` on every event, with no
strategy consulted at all.

## Environment

- Generated: 2026-09-01
- JDK: openjdk version "22.0.2" 2024-07-16 (the report used OpenJDK 26)
- Maven: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- LMAX Disruptor: 4.0.0
- `3.0.0-SNAPSHOT` built from `~/apache/log4j-main` with JDK 17 (the module
  enforces `[17,18)`); it takes `log4j.configuration.location`, not
  `log4j2.configurationFile`, and its selector is
  `org.apache.logging.log4j.async.logger.AsyncLoggerContextSelector`
