# PR #4171 — native `traceId` / `spanId` / `traceFlags` on `LogEvent` via a `TraceContextProvider` SPI

https://github.com/apache/logging-log4j2/pull/4171

The PR adds `org.apache.logging.log4j.spi.TraceContextProvider`, loaded by `TraceContextProviderService` from `ServiceLoader` or the `log4j2.activeTraceContextProvider` system-property object. `Log4jLogEvent`, `MutableLogEvent` and `RingBufferLogEventTranslator` capture the three values on the calling thread, so async loggers carry them without copying the MDC, and the `%traceId`, `%spanId` and `%traceFlags` pattern converters print them.

`Repro.java` installs a thread-local provider (a `java.lang.reflect.Proxy`, so the class still compiles against 2.26.1) through the system property, logs through a synchronous logger and an `AsyncLogger`, changes the span between async calls, and clears it for a last event. The oracle reads the file.

## Commands

```bash
cd log4j-pr-4171-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.1 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | SPI present | — | `false` | — |
| 2.26.1 | sync event | `trace=4bf9…4736 span=00f0…02b7 flags=01` | `trace=mainraceId span=%spanId flags=mainraceFlags`; StatusLogger `Unrecognized format specifier` | FAIL |
| 2.26.1 | async event, span A | span A | same garbage | FAIL |
| 2.26.1 | async event, span B after switch | span B | same garbage | FAIL |
| 2.26.1 | no span | empty fields | same garbage | FAIL |
| 2.x HEAD ea28f5a78b | sync event | span A | span A | PASS |
| 2.x HEAD ea28f5a78b | async event, span A | span A | span A | PASS |
| 2.x HEAD ea28f5a78b | async event, span B after switch | span B | span B | PASS |
| 2.x HEAD ea28f5a78b | no span | empty fields | `trace= span= flags=` | PASS |

Notes: on 2.26.1 the unknown `%traceId` is parsed as `%t` (thread name) plus literal `raceId`, so the output is wrong rather than empty. The allocation saving the PR title claims was not measured here.
