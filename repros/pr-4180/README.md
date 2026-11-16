# PR #4180 — `StructuredDataMessage` XML encoding includes the free-form message

https://github.com/apache/logging-log4j2/pull/4180

`StructuredDataMessage.asXml()` wrote `<type>`, `<id>` and the map, but dropped the constructor's free-form `message`. The fix emits it, XML-escaped, as its own `<message>` element ahead of `<Map>`, so it stays distinct from a map entry keyed `message` (#4141).

`Repro.java` checks `asString("XML")` directly, with and without a map key named `message`, and through a `PatternLayout` `%m{XML}` written to a file.

## Commands

```bash
cd log4j-pr-4180-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.1 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | API, free-form text | `<message>user &lt;alice&gt; logged in</message>` | absent; only `type`, `id`, `Map` | FAIL |
| 2.26.1 | API, text plus map key `message` | both, distinct | only `<Entry key="message">from-map</Entry>` | FAIL |
| 2.26.1 | `%m{XML}` in a file | `<message>…</message>` | absent | FAIL |
| 2.x HEAD ea28f5a78b | API, free-form text | as above | present, escaped | PASS |
| 2.x HEAD ea28f5a78b | API, text plus map key `message` | both, distinct | `<message>text</message>` and the `Entry` | PASS |
| 2.x HEAD ea28f5a78b | `%m{XML}` in a file | `<message>…</message>` | present | PASS |
