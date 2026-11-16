# PR #4172 — a Log4j 1 XML `<appender>` that cannot be created no longer discards the whole configuration

https://github.com/apache/logging-log4j2/pull/4172

In `log4j-1.2-api` `XmlConfiguration.parse()`, `parseAppender()` returns `null` when the appender class cannot be instantiated, and `appender.getName()` then threw a `NullPointerException`. The parse aborted with `Could not parse ...log4j.xml`, leaving a configuration with no appenders. The fix skips the element with a warning, and also skips an appender with a null name.

`log4j.xml` declares a `broken` appender (`com.example.DoesNotExistAppender`) and a valid `FileAppender` named `good` on the root logger. It runs with `-Dlog4j1.compatibility=true -Dlog4j.configuration=log4j.xml`. The oracle checks the appender map and the file content.

## Commands

```bash
cd log4j-pr-4172-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.1 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `good` appender configured | `[good]` | `[]`; StatusLogger NPE `Cannot invoke "org.apache.log4j.Appender.getName()" because "appender" is null` | FAIL |
| 2.26.1 | `good` receives the event | `hello-from-log4j1` | no file | FAIL |
| 2.x HEAD ea28f5a78b | `good` appender configured | `[good]` | `[good]` | PASS |
| 2.x HEAD ea28f5a78b | `good` receives the event | `hello-from-log4j1` | `hello-from-log4j1` | PASS |

Notes: the new `WARN Could not create appender named [broken] ...; ignoring.` appears only with `-Dlog4j2.debug=true`. Under `-Dlog4j2.StatusLogger.level=WARN` it is filtered out, while the existing `ERROR Could not create an Appender` (ClassNotFoundException) still shows. `run.sh` therefore runs with `log4j2.debug`.
