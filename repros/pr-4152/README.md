# PR #4152 — [main] Fix RegexFilter NPE when `useRawMsg` is null (#3265 port)

https://github.com/apache/logging-log4j2/pull/4152

`RegexFilter.createFilter(String regex, String[] patternFlags, Boolean useRawMsg, Result onMatch, Result onMismatch)`
passed the boxed `useRawMsg` straight to the private constructor's `boolean` parameter, so a programmatic call with
`useRawMsg == null` threw `NullPointerException` on unboxing. The fix passes `Boolean.TRUE.equals(useRawMsg)`, which
makes `null` mean `false`. Configuration files never hit this on 3.0.0-beta3: the plugin injector already supplies a
non-null value when the attribute is omitted, which the XML control row shows.

## Command

```bash
cd log4j-pr-4152-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
```

Both runs use `log4j-api` 2.24.3 and `-Dlog4j.configuration.location=log4j2.xml`. The jars-dir run swaps
`log4j-core`, `log4j-plugins` and `log4j-kit` for the `3.0.0-SNAPSHOT` jars (printed in `result.txt`).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | `createFilter(".*secret.*", null, null, DENY, NEUTRAL)` | filter built, `secret-message` → DENY | `NullPointerException: ... because "useRawMsg" is null` | FAIL |
| 3.0.0-beta3 | XML `<RegexFilter>` without `useRawMsg` (control) | only `public-message` written | `[public-message]` | PASS |
| main HEAD 2bb673f650 | `createFilter(".*secret.*", null, null, DENY, NEUTRAL)` | filter built, `secret-message` → DENY | built, DENY | PASS |
| main HEAD 2bb673f650 | XML `<RegexFilter>` without `useRawMsg` (control) | only `public-message` written | `[public-message]` | PASS |
