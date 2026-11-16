# PR #4370 — default `MapMessage` rendering does not escape RFC 5424 delimiters

https://github.com/apache/logging-log4j2/pull/4370

With no format, `MapMessage.getFormattedMessage()` (and `StructuredDataMessage`) renders `key="value"` pairs through
`MapMessage.appendMap`, which appended each value verbatim. RFC 5424 §6.3.3 requires `"`, `\` and `]` inside a
PARAM-VALUE to be escaped with a backslash; without it, a value such as `x" admin="true` reads as an extra pair to
anything that parses the message. The fix escapes those three characters in the value after it is rendered.

## Commands

```bash
cd log4j-pr-4370-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`Repro.java` formats a `StringMapMessage` and a `StructuredDataMessage` with value `a"b\c]d`, and renders a
`StringMapMessage` with value `x" admin="true` through `PatternLayout` `%m`. Both `log4j-api` and `log4j-core` are
replaced from the jars directory.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: plain value | `user="alice"` | `user="alice"` | PASS |
| 2.26.1 | `StringMapMessage` `a"b\c]d` | `user="a\"b\\c\]d"` | `user="a"b\c]d"` | FAIL |
| 2.26.1 | `StructuredDataMessage` `a"b\c]d` | `type [id@32473 user="a\"b\\c\]d"] message` | `type [id@32473 user="a"b\c]d"] message` | FAIL |
| 2.26.1 | `%m` of `x" admin="true` | `user="x\" admin=\"true"` | `user="x" admin="true"` | FAIL |
| 2.x HEAD ea28f5a78b | control: plain value | `user="alice"` | `user="alice"` | PASS |
| 2.x HEAD ea28f5a78b | `StringMapMessage` `a"b\c]d` | `user="a\"b\\c\]d"` | `user="a\"b\\c\]d"` | PASS |
| 2.x HEAD ea28f5a78b | `StructuredDataMessage` `a"b\c]d` | `type [id@32473 user="a\"b\\c\]d"] message` | same | PASS |
| 2.x HEAD ea28f5a78b | `%m` of `x" admin="true` | `user="x\" admin=\"true"` | `user="x\" admin=\"true"` | PASS |
