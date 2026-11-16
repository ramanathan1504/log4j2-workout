# PR #4157 — [main] Port `InternalLoggerRegistry` from `2.x` (#3418 and #3681 ports)

https://github.com/apache/logging-log4j2/pull/4157

On 3.0.0-beta3 `LoggerContext` kept its loggers in the API's `LoggerRegistry`, a `HashMap` of strong references, so
every logger ever requested stayed reachable for the life of the context. The port replaces it with core's
`InternalLoggerRegistry`: loggers are held through `WeakReference`s registered on a `ReferenceQueue` and expunged
when collected (#3681), `computeIfAbsent` creates the logger outside the write lock (#3418), and `null` message
factories resolve to the context's injected default. This repro shows the weak-reference half; the lock-scope change
has no output difference to measure and is not covered.

## Command

```bash
cd log4j-pr-4157-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
```

`Repro.java` asks the context for 5000 uniquely named loggers while holding them in a list, counts them through
`LoggerContext.getLoggers()`, drops the list, runs `System.gc()` up to ten times, and counts again. One logger is kept
referenced throughout as a control. `-Xmx128m`, `log4j-api` 2.24.3, `-Dlog4j.configuration.location`. Run twice on
each side with the same numbers.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | registered while referenced (control) | 5000 | 5000 | PASS |
| 3.0.0-beta3 | unreferenced loggers released after GC | 0/5000 remaining | 5000/5000 remaining | FAIL |
| 3.0.0-beta3 | referenced logger kept, same instance (control) | true | true | PASS |
| main HEAD 2bb673f650 | registered while referenced (control) | 5000 | 5000 | PASS |
| main HEAD 2bb673f650 | unreferenced loggers released after GC | 0/5000 remaining | 0/5000 remaining | PASS |
| main HEAD 2bb673f650 | referenced logger kept, same instance (control) | true | true | PASS |
