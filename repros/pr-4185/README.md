# PR #4185 — Log4j 1 JMX `addAppender` / `setLayout` with a bad class name fail cleanly

https://github.com/apache/logging-log4j2/pull/4185

In `log4j-1.2-api`, `LoggerDynamicMBean.addAppender` dereferenced the `null` returned by `OptionConverter.instantiateByClassName`, and `AppenderDynamicMBean.invoke("setLayout")` passed that `null` to `appender.setLayout`. The fix logs an error and throws `MBeanException(IllegalArgumentException)` in both cases, so the existing layout is left alone.

`Repro.java` registers both MBeans on the platform `MBeanServer` and invokes the operations with nonexistent classes through `server.invoke`. The appender is a Log4j 1 `WriterAppender` over a `StringWriter`, so the oracle can check that it still writes afterwards.

## Commands

```bash
cd log4j-pr-4185-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.1 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `addAppender(bad, ghost)` | `MBeanException` | `RuntimeMBeanException` caused by NPE `Cannot invoke "org.apache.log4j.Appender.setName(String)"` | FAIL |
| 2.26.1 | `setLayout(bad)` | `MBeanException` | returned `null` (reported as success) | FAIL |
| 2.26.1 | layout kept | existing `PatternLayout` | `null` | FAIL |
| 2.26.1 | appender still writes | `after-failed-setLayout` | empty | FAIL |
| 2.x HEAD ea28f5a78b | `addAppender(bad, ghost)` | `MBeanException` | `MBeanException` / `IllegalArgumentException: Could not instantiate appender class [...]` | PASS |
| 2.x HEAD ea28f5a78b | `setLayout(bad)` | `MBeanException` | `MBeanException` / `IllegalArgumentException: Could not instantiate layout class [...]` | PASS |
| 2.x HEAD ea28f5a78b | layout kept | existing `PatternLayout` | `PatternLayout` | PASS |
| 2.x HEAD ea28f5a78b | appender still writes | `after-failed-setLayout` | `after-failed-setLayout` | PASS |

Notes: on 2.26.1 the silent `setLayout(null)` is the worse half. The JMX client sees success, and the appender then drops every event.
