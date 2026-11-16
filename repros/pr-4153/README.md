# PR #4153 — [main] Add `@Required` and validation to `StringMatchFilter` `text` attribute (#3158 port)

https://github.com/apache/logging-log4j2/pull/4153

On 3.0.0-beta3 the `StringMatchFilter.Builder.text` field defaulted to `""`. A `<StringMatchFilter>` with no `text`
therefore built a filter whose `msg.contains("")` is true for every event, so `onMatch` applied to everything: with
`onMatch="DENY"` the appender wrote nothing, silently. The port marks `text` `@Required`, drops the `""` default, makes
`build()` return `null` with an ERROR when `text` is unset, and adds `setText()`/`getText()` (deprecating
`setMatchString()`).

## Command

```bash
cd log4j-pr-4153-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
```

`log4j2.xml` has two `File` appenders, `NoText` with `<StringMatchFilter onMatch="DENY" onMismatch="NEUTRAL"/>` and
`WithText` with `text="secret"` as the control. `Repro.java` logs `public-message` and `secret-message`, also calls
`StringMatchFilter.newBuilder().setOnMatch(DENY).setOnMismatch(NEUTRAL).build()` with no text, and reads both files
back. `log4j-api` 2.24.3, `-Dlog4j.configuration.location`, StatusLogger at WARN.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | XML filter without `text` | filter rejected, both lines written | `[]` (every event denied), 0 StatusLogger lines | FAIL |
| 3.0.0-beta3 | `Builder.build()` without text | `null` | filter with `text=''` | FAIL |
| 3.0.0-beta3 | XML filter `text="secret"` (control) | `[public-message]` | `[public-message]` | PASS |
| main HEAD 2bb673f650 | XML filter without `text` | filter rejected, both lines written | `[public-message, secret-message]`, `ERROR No text provided for StringMatchFilter: text` | PASS |
| main HEAD 2bb673f650 | `Builder.build()` without text | `null` | `null` | PASS |
| main HEAD 2bb673f650 | XML filter `text="secret"` (control) | `[public-message]` | `[public-message]` | PASS |

Notes: on main HEAD the rejection is followed by a third, misleading ERROR,
`StringMatchFilter contains invalid attributes "onMatch", "onMismatch"`, after the `ConstraintValidationException`;
the attributes are valid (likely reported because the failed node was never consumed; not investigated).
