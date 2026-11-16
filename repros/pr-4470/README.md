# PR #4470 — Keep the `AsyncLogger` location when the ring buffer is full

https://github.com/apache/logging-log4j2/pull/4470

When `AsyncLogger.handleRingBufferFull` routes an event `SYNCHRONOUS` (or the recursion branch fires),
`logMessageInCurrentThread(fqcn, level, marker, message, thrown)` calls
`ReliabilityStrategy.log(...)` without a location, so `LoggerConfig` recomputes it from the stack.
A location passed with `LogBuilder.withLocation(...)` is therefore replaced by the caller frame.
The PR passes the already-resolved location down (`translator.getLocation()` / `location`) and calls
`Logger.log(level, marker, fqcn, location, message, thrown)`, for both translator types.

## Setup

`AsyncLoggerContextSelector`, `asyncLoggerRingBufferSize=128`, `asyncQueueFullPolicy=AlwaysSynchronousPolicy`
(always `SYNCHRONOUS`), root `includeLocation="true"`, and a programmatic appender that blocks the
background thread on the first event. 300 fillers fill the buffer, then three events are logged from `main`:
`logger.info` (classic), `logger.atInfo().log` (builder), `logger.atInfo().withLocation(com.example.Caller.explicitMethod:4242).log` (explicit).
The appender records `event.getSource()` and the delivering thread (`thread=main` proves the synchronous route).
Each run covers `log4j2.enableThreadlocals=true` and `false`.

## Commands

```bash
cd log4j-pr-4470-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-pr-4470 pr-4470-head
./run.sh <scratchpad>/wt/jars-2x 2x-head
```

The jar directories hold `log4j-api` and `log4j-core`; disruptor 4.0.0 comes from Maven.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | classic, both translators | `Repro.main:65` | `Repro.main:65 thread=main` | PASS |
| 2.26.1 | builder, both translators | `Repro.main:67` | `Repro.main:67 thread=main` | PASS |
| 2.26.1 | explicit, both translators | `com.example.Caller.explicitMethod:4242` | `Repro.main:68 thread=main` | **FAIL** |
| 2.x HEAD ea28f5a78b | explicit, both translators | `com.example.Caller.explicitMethod:4242` | `Repro.main:68 thread=main` | **FAIL** |
| PR head ac0eb401b0 | classic, both translators | `Repro.main:65` | `Repro.main:65 thread=main` | PASS |
| PR head ac0eb401b0 | builder, both translators | `Repro.main:67` | `Repro.main:67 thread=main` | PASS |
| PR head ac0eb401b0 | explicit, both translators | `com.example.Caller.explicitMethod:4242` | `com.example.Caller.explicitMethod:4242 thread=main` | PASS |

Full output in `output/<label>/result.txt`.

Notes: the classic and builder cases already resolve correctly on 2.26.1, because `LoggerConfig` recomputes
the location from `fqcn`; what that costs is the second stack walk the note mentions, which this repro does not measure.
