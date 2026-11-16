# PR #4373 — JPA `ThrowableAttributeConverter` could not read back messages with line breaks

https://github.com/apache/logging-log4j2/pull/4373

## Mechanism

`convertThrowable` wrote `throwable.toString()` verbatim, so a multi-line message became extra lines in the column, and `convertString` read every following line either as a frame (`line.trim().substring(3)`, which throws `StringIndexOutOfBoundsException` on a non-frame line) or, for a line starting `Caused by `, as a cause whose class it loaded and initialized through `LoaderUtil.loadClass`. The PR escapes `\`, `\r`, `\n` behind a `!LOG4J2!:` marker only when the message needs it, parses only `\tat ` lines as frames and skips malformed ones, and loads classes with `Class.forName(name, false, …)` so a non-throwable is never initialized (issue #4343). `Repro.java` round-trips through `convertToDatabaseColumn` / `convertToEntityAttribute` and also reads a hand-written legacy row.

## Commands

```bash
cd log4j-pr-4373-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | message `line one\nline two` | round-trips | `StringIndexOutOfBoundsException: begin 0, end -1, length 5` | FAIL |
| 2.26.1 | message `line one\r\nline two` | round-trips | `StringIndexOutOfBoundsException` | FAIL |
| 2.26.1 | message `boom\nCaused by Repro$InitializationProbe: injected` | probe class not initialized | probe's static initializer ran | FAIL |
| 2.26.1 | legacy row with an unescaped line break | no exception | `StringIndexOutOfBoundsException` | FAIL |
| 2.26.1 | message starting `!LOG4J2!:` | unchanged | `!LOG4J2!:literal` | PASS |
| 2.26.1 | control: `C:\new` | unchanged | `C:\new` | PASS |
| 2.x HEAD ea28f5a78b | message `line one\nline two` | round-trips | `line one\nline two` | PASS |
| 2.x HEAD ea28f5a78b | message `line one\r\nline two` | round-trips | `line one\r\nline two` | PASS |
| 2.x HEAD ea28f5a78b | fake `Caused by` line in the message | probe class not initialized | not initialized | PASS |
| 2.x HEAD ea28f5a78b | legacy row with an unescaped line break | no exception | `message=line one`, 1 frame | PASS |
| 2.x HEAD ea28f5a78b | message starting `!LOG4J2!:` | unchanged | `!LOG4J2!:literal` | PASS |
| 2.x HEAD ea28f5a78b | control: `C:\new` | unchanged | `C:\new` | PASS |

## Notes

As the review note expected, a legacy row stays misparsed (the second message line is dropped, `message=line one`) but no longer throws.
