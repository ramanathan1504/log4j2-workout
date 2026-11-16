# PR #4049 — `NoClassDefFoundError` while resolving a stack frame drops the whole log event

https://github.com/apache/logging-log4j2/pull/4049

`ThrowableExtendedStackTraceRenderer.ExtendedContext#loadClass` tries three class-loading strategies for every frame
it cannot match against the execution stack, the first being the class loader of the previously resolved frame. It
caught only `Exception`, so a loader that throws `NoClassDefFoundError` (a `LinkageError`) escaped the renderer, the
appender call failed with `AppenderLoggingException`, and the event was lost. The PR (`466f98f471`, fixes #4028)
catches `Throwable`, so the frame is rendered without class metadata instead.

## Commands

```bash
cd log4j-pr-4049-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`Repro` defines `Hidden` through its own `ClassLoader`, which throws `NoClassDefFoundError` for any `com.missing.*`
name. `Hidden.run()` logs an exception whose stack trace gets a `com.missing.Gone.vanish(Gone.java:7)` frame on top,
so the renderer resolves `Hidden` from the execution stack and then asks that loader for `com.missing.Gone`. A second
event with an ordinary exception is the control. The oracle reads `app.log` (`%m%n%xEx`).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | event with unloadable frame written | yes | no; StatusLogger `AppenderLoggingException: java.lang.NoClassDefFoundError: com/missing/Gone` | FAIL |
| 2.26.1 | `com.missing.Gone.vanish(Gone.java:7)` rendered | yes | no | FAIL |
| 2.26.1 | control event written | yes | yes | PASS |
| 2.x HEAD ea28f5a78b | event with unloadable frame written | yes | yes, no StatusLogger output | PASS |
| 2.x HEAD ea28f5a78b | `com.missing.Gone.vanish(Gone.java:7)` rendered | yes | yes | PASS |
| 2.x HEAD ea28f5a78b | control event written | yes | yes | PASS |

`app.log` is 133 bytes on 2.26.1 (control only) and 321 bytes on 2.x HEAD.
