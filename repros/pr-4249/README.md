# PR #4249 — cyclic and equality-colliding exception chains

https://github.com/apache/logging-log4j2/pull/4249

Four causal-chain walkers had no guard, or one that relied on `equals`/`hashCode`. `NoSqlDatabaseManager` looped on `while (thrown.getCause() != null)`, so an A↔B cycle built entities until the heap ran out. `ThrowableAttributeConverter` (log4j-jpa) recursed into `getCause()` until `StackOverflowError`. `Throwables.getRootCause` and `ThrowableExtendedStackTraceRenderer` (`%xEx`) used a `HashSet`, so exceptions whose `equals` collide were treated as already visited: the walk stopped early, giving the wrong root cause and dropping the cause's packaging info. The fix uses identity sets everywhere and makes the JPA converter iterative.

## Commands

```bash
cd log4j-pr-4249-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

Each case runs in its own JVM with `-Xmx64m -Xss512k`. `--add-opens java.base/java.lang=ALL-UNNAMED` is needed by the JPA converter's static initialiser on JDK 17 in both versions. NoSQL uses an in-memory `NoSqlProvider` with a programmatic `NoSqlAppender`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `%xEx`, colliding cause frame | `[classes/:?]` packaging | `at Repro.create(Repro.java:1)`, no packaging | FAIL |
| 2.26.1 | `Throwables.getRootCause(r→r_c→r_c_c)`, colliding | `r_c_c` | `r` | FAIL |
| 2.26.1 | JPA `ThrowableAttributeConverter`, A↔B | one `Caused by` | `StackOverflowError` | FAIL |
| 2.26.1 | `NoSqlAppender`, A↔B | 1 insert, chain depth 2 | `OutOfMemoryError` | FAIL |
| 2.x HEAD ea28f5a78b | `%xEx`, colliding cause frame | packaging present | `at Repro.create(Repro.java:1) [classes/:?]` | PASS |
| 2.x HEAD ea28f5a78b | `Throwables.getRootCause`, colliding | `r_c_c` | `r_c_c` | PASS |
| 2.x HEAD ea28f5a78b | JPA converter, A↔B | one `Caused by` | length 276, 1 `Caused by` | PASS |
| 2.x HEAD ea28f5a78b | `NoSqlAppender`, A↔B | 1 insert, depth 2 | 1 insert, depth 2 | PASS |

Notes: the review's out-of-scope point (`ThrowableProxy` `causeVisited` never holds the root, so `%ex` prints A twice with no `[CIRCULAR REFERENCE]` marker) is not exercised here.
