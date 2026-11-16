# PR #4393 — escaping `$`, `:`, `-` and `}` inside `${...}` in StrSubstitutor

https://github.com/apache/logging-log4j2/pull/4393 (issue #2679)

On 2.26.1 there is no way to put a `}` inside a `${...}` expression: the scan for the closing suffix stops at the first `}`, so
`${env:TZ:-%d{HH:mm:ss}{GMT+00}}` leaves `{GMT+00}}` behind as literal text. The PR makes `$` escape `$`, `:`, `-` and `}` inside an
expression (`isEscapeSequence`, `ESCAPABLE_CHARS = ":-}"`). At head `064c183a2e` it also tracks which characters of the expression are
literal text and which came from a nested lookup (`resolveNestedVariables` / `VariableExpression` / `isLiteralEscapeSequence`), and
excludes `$${` from the escape rule. Those are the two regressions the review of `08d297a368` found (nested values unescaped,
`${x:-$${ctx:k}}` giving `set}`), so the run checks both.

The repro loads one XML config whose `PatternLayout` is `${sys:undefined.tz:-%d{HH:mm:ss$}{GMT+00$}} %m%n` and logs `msg`. It also
calls the configuration's own `StrSubstitutor.replace(...)` with `-Dx=set -Dp='a$$b' -Dq='a$}b'` (set in `Repro.java`).

## Commands

```bash
log4j-pr-4393-repro/run.sh 2.26.1 2.26.1
log4j-pr-4393-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4393 pr-head-064c183a2e
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | layout `${sys:undefined.tz:-%d{HH:mm:ss$}{GMT+00$}} %m%n` | `HH:mm:ss msg` | `18:36:38${AD11T+00$} msg` | FAIL |
| 2.26.1 | `${sys:undefined:-a$:-b}` | `a:-b` | `a$:-b` | FAIL |
| 2.26.1 | `${sys:undefined:-a$$b}` | `a$b` | `a$$b` | FAIL |
| 2.26.1 | `${sys:x:-$${ctx:k}}`, x set (control) | `set` | `set` | PASS |
| 2.26.1 | `${sys:undefined:-$${ctx:k}}` (control) | `${ctx:k}` | `${ctx:k}` | PASS |
| 2.26.1 | `${sys:undefined:-${sys:p}}`, p=`a$$b` (control) | `a$$b` | `a$$b` | PASS |
| 2.26.1 | `${sys:undefined:-${sys:q}}`, q=`a$}b` (control) | `a$}b` | `a$}b` | PASS |
| 2.26.1 | `${sys:undefined:-plain}` (control) | `plain` | `plain` | PASS |
| PR head `064c183a2e` | layout with `$}` | `HH:mm:ss msg` | `13:06:51 msg` | PASS |
| PR head `064c183a2e` | `${sys:undefined:-a$:-b}` | `a:-b` | `a:-b` | PASS |
| PR head `064c183a2e` | `${sys:undefined:-a$$b}` | `a$b` | `a$b` | PASS |
| PR head `064c183a2e` | `${sys:x:-$${ctx:k}}`, x set | `set` | `set` | PASS |
| PR head `064c183a2e` | `${sys:undefined:-$${ctx:k}}` | `${ctx:k}` | `${ctx:k}` | PASS |
| PR head `064c183a2e` | `${sys:undefined:-${sys:p}}`, p=`a$$b` | `a$$b` | `a$$b` | PASS |
| PR head `064c183a2e` | `${sys:undefined:-${sys:q}}`, q=`a$}b` | `a$}b` | `a$}b` | PASS |
| PR head `064c183a2e` | `${sys:undefined:-plain}` | `plain` | `plain` | PASS |

The two timestamps differ in zone because the 2.26.1 run fell back to local time; the stray `${AD11T+00$}` is the leftover
`{GMT+00$}}` after `%d` parsed `GMT` as a date pattern.

Raw: `output/<label>/result.txt`, `output/<label>/app.log`.

Notes: both regressions from the 2026-10-21 review (`set}` for `${x:-$${ctx:k}}`, and `a$b`/`a}b` for nested lookup values) are gone
at `064c183a2e`. The design change the PR body accepts is still visible: `${sys:undefined:-a$}` gives `a$` on 2.26.1 and stays
unexpanded (`${sys:undefined:-a$}`) on the PR head. `fixed` vs `changed` in the changelog remains a maintainer call.
