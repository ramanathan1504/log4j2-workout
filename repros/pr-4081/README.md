# PR #4081 — `CronExpression.getPrevFireTime` skips the fire time of the current second when the input has milliseconds

https://github.com/apache/logging-log4j2/pull/4081

## Mechanism

`getPrevFireTime(d)` delegates to `getTimeBefore(d)`, which works at second precision. When `d` carries a
millisecond fraction (`00:00:00.001`, or a file timestamp such as `00:00:00.201`), the fire time at that same
second is not returned and the previous day's is. The PR shifts such inputs by `+999 ms` before delegating, and
lowers the day-level reverse-search step in `findMinIncrement()` from 24 h to 23 h for spring-forward days.
`CronTriggeringPolicy` calls `getPrevFireTime` on startup, so a wrong answer there means an extra or missed rollover.

## Commands

```bash
cd log4j-pr-4081-repro
./run.sh 2.26.0 2.26.0
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2x-head-ea28f5a78b
```

`Repro.java` evaluates `0 0 0 * * ?` in `Australia/Sydney` (fallback Apr 6 2025, spring-forward Oct 5 2025),
`America/Santiago` (fallback at midnight, Apr 6 2025) and `UTC`, at 1 ms before and 1 ms after midnight, the
inputs of the PR's own tests, plus a UTC midnight + 201 ms input with no DST involved.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | Sydney fallback `prev(apr6 + 1ms)` | 2025-04-05T13:00Z | 2025-04-04T13:00Z | FAIL |
| 2.26.0 | Santiago fallback `prev(apr6 + 1ms)` | 2025-04-06T04:00Z | 2025-04-05T03:00Z | FAIL |
| 2.26.0 | Sydney spring-forward `prev(oct6 + 1ms)` | 2025-10-05T13:00Z | 2025-10-04T14:00Z | FAIL |
| 2.26.0 | UTC, no DST, `prev(midnight + 201ms)` | 2025-06-02T00:00Z | 2025-06-01T00:00Z | FAIL |
| 2.26.0 | the 7 `prev(… − 1ms)` and `next(…)` checks | as in tests | as expected | PASS |
| 2.x HEAD ea28f5a78b | all 11 checks above | as in tests | as expected | PASS |

Full output: `output/<label>/result.txt`.

## Notes

- The millisecond defect is not DST-specific: the UTC case fails on 2.26.0 too. DST only changes which
  wrong day comes back.
- Every `getNextValidTimeAfter` check passes on both, so the visible change is entirely in `getPrevFireTime`.
