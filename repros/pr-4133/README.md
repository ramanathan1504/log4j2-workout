# PR #4133 — Fix stack trace rendering for exceptions with identity malfunction

https://github.com/apache/logging-log4j2/pull/4133

`ThrowableProxy` tracked the visited causes in a `HashSet`, so the cycle check `causeVisited.contains(causeCause)`
used the exception's own `equals()`/`hashCode()`. An exception class whose `equals()` makes distinct instances equal
(here: every `CollidingException` equals every other) is treated as a cycle at the second cause, and the proxy drops
the rest of the chain. `ThrowableStackTraceRenderer` (Pattern Layout `%ex`/`%xEx`) had the same `HashSet`/`HashMap` bookkeeping. The fix keys
the visited sets and the metadata map by identity (`IdentityHashMap`) in both. `ThrowableProxy` is what `JsonLayout`
(and `XmlLayout`/`YamlLayout`) serialise as `thrown`, so the loss is visible in the JSON output as well as in the text.

## Command

```bash
cd log4j-pr-4133-repro
./run.sh 2.26.0 2.26.0
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`Repro.java` logs a three-level chain `colliding-outer -> colliding-middle -> colliding-inner` of `CollidingException`
and a plain `RuntimeException` chain of the same shape, through `JsonLayout` and `PatternLayout %m%n%xEx`, then reads
both files back and reports which levels were rendered.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | JsonLayout, colliding chain | outer,middle,inner | outer,middle | FAIL |
| 2.26.0 | JsonLayout, plain chain (control) | outer,middle,inner | outer,middle,inner | PASS |
| 2.26.0 | `%xEx`, colliding chain | outer,middle,inner | outer,middle | FAIL |
| 2.x HEAD ea28f5a78b | JsonLayout, colliding chain | outer,middle,inner | outer,middle,inner | PASS |
| 2.x HEAD ea28f5a78b | JsonLayout, plain chain (control) | outer,middle,inner | outer,middle,inner | PASS |
| 2.x HEAD ea28f5a78b | `%xEx`, colliding chain | outer,middle,inner | outer,middle,inner | PASS |
