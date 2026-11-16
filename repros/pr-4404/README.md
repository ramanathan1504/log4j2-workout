# PR #4404 — `LocationAwareLogger.log()` ignores the configured `MessageFactory`

https://github.com/apache/logging-log4j2/pull/4404

`Log4jLogger.log(Marker, fqcn, level, msg, args, t)` in `log4j-slf4j-impl` and `log4j-slf4j2-impl` built `ParameterizedMessage` / `SimpleMessage` by hand, so a logger whose factory is `StringFormatterMessageFactory` (here set with `-Dlog4j2.messageFactory`) formatted `%s` correctly through `info()` but left it literal through the `LocationAwareLogger` path that wrapper libraries use. The fix calls `logger.getMessageFactory().newMessage(...)` and releases reusable messages afterwards. The repro uses the SLF4J 2 binding and calls the method from a `Wrapper` class whose name is passed as the FQCN, so `%C` must still resolve to `Repro`.

## Commands

```bash
cd log4j-pr-4404-repro
./run.sh 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control `Logger.info("info %s", "formatted")` | `info formatted \| Repro` | `info formatted \| Repro` | PASS |
| 2.26.1 | `LocationAwareLogger.log("location-aware %s", ["formatted"])` | `location-aware formatted \| Repro` | `location-aware %s \| Repro` | FAIL |
| 2.x HEAD ea28f5a78b | control `Logger.info` | `info formatted \| Repro` | `info formatted \| Repro` | PASS |
| 2.x HEAD ea28f5a78b | `LocationAwareLogger.log` | `location-aware formatted \| Repro` | `location-aware formatted \| Repro` | PASS |

Notes: only the SLF4J 2 binding was run; the reusable-message release is not observable from an application and was not measured.
