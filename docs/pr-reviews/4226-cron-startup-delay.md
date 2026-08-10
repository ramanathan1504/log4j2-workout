# PR #4226 — Fix multi-second startup delay in `CronTriggeringPolicy`

https://github.com/apache/logging-log4j2/pull/4226

| | |
|---|---|
| Author | `tupelo-schneck` |
| Prior merged PRs in this repo | **0** (2 open: #4226, #4227) |
| Base | `2.x` |
| Size | +68 −1, 5 files |
| Linked issue | none |
| CI | green |

## Verdict: ✅ take it. The best-diagnosed PR in the whole batch.

## Is it really needed?

Yes, and this one is not close. **Measured on the bench: ~3 seconds of 100%-CPU
main thread per appender, on every startup.** Full evidence in
[`repros/pr-4226/`](../../repros/pr-4226/):

| Appender | `fileName` | `initialize()` duration |
|---|---|---:|
| `DirectCronA` | none | **2.996 s** |
| `DirectCronB` | none | **2.756 s** |
| `CronWithFileName` | set | **0.000118 s** |

25,000×, additive per appender, on the thread that starts your application.

**This does not fit the profile-building pattern.** The PR body contains a
correct root-cause analysis of a non-obvious infinite-ish loop, a stack sample
from a real affected startup, and a fix at the right layer. Nobody produces this
by trawling the issue feed — it comes from having been bitten in production. The
author filed no issue first, which in the other PRs I would read as skipping
process; here the write-up *is* the issue report.

## The diagnosis is correct

I verified every step against `2.x` at `04c93c1d33`.

`CronExpression.getTimeBefore()` (line 1570) walks backwards one
`findMinIncrement()` step at a time:

```java
do {
    final Date prevCheckDate = new Date(start.getTime() - minIncrement);
    prevFireTime = getTimeAfter(prevCheckDate);
    if (prevFireTime == null || prevFireTime.before(MIN_DATE)) {
        return null;
    }
    start = prevCheckDate;
} while (prevFireTime.compareTo(targetDateNoMs) >= 0);
```

The bounds check is on `prevFireTime` — the **result** — never on
`prevCheckDate`, the **candidate**. Since `getTimeAfter()` clamps to 1970, a
target at the epoch makes every iteration return the same 1970 date, which is
neither `null`, nor before `MIN_DATE`, nor before the target. The exit condition
is unsatisfiable, and the walk grinds until the `YEAR > 2999` guard trips.

`MIN_DATE` is 1970 (`CronExpression.java:263`), and `RollingFileManager
.getFileTime()` returns `initialTime` (line 519), which is 0 for a direct-write
manager. Both premises hold.

## The fix is correct, and does not change results

```java
if (prevCheckDate.before(MIN_DATE)) {
    return null;
}
```

I worked the boundary case rather than trusting it. For a target comfortably
after 1970 the walk terminates on the normal exit condition long before
`prevCheckDate` reaches `MIN_DATE`, so nothing changes. The new guard only fires
when the walk has crossed below 1970 — at which point there genuinely is no fire
time in range and `null` is the correct answer, which is also what the old code
eventually returned. **Same result, ~3 s faster.**

## One redundancy worth raising

The PR fixes it in *two* places:

1. `CronExpression.getTimeBefore()` — the root cause
2. `CronTriggeringPolicy.initialize()` — `fileTime > 0 ? … : null`, skipping the
   call entirely

With (1) in place, (2) is no longer load-bearing: `getPrevFireTime(new Date(0))`
now returns `null` in microseconds anyway, and `null` is exactly what (2)
substitutes. It is cheap and self-documenting, so this is a question rather than
an objection — but a reviewer should know it is belt-and-braces, not two
independent bugs.

The one thing (2) does change: `CronWithFileName` must still get a **non-null**
`LastRollForFile`. My control run confirms it does today
(`2026-08-09T00:00:00`), so the guard must not accidentally widen to appenders
that have a file time. Worth asserting in the test.

## Test quality

Good. `testPrevFireTimeAtEpochReturnsNullPromptly` uses `@Timeout(1, SECONDS)`
against a ~3 s bug — a real margin, not a flaky one — and picks a weekly
schedule because that is the worst case for `findMinIncrement()`. The comments
explain *why* the timeout is the assertion. `testBuilderWithoutFileNameInitializesPromptly`
covers the integration path at 2 s.

## Interaction with #4227

Same author, same file. #4226 changes `CronTriggeringPolicy.initialize()`,
#4227 changes `CronTriggeringPolicy.rollover()` — different methods, so no
textual conflict, but they should be merged in a known order and #4227's premise
(that `initialize()` records the period start correctly) sits on top of this one.
Worth stating on both PRs.

## Repro

[`repros/pr-4226/`](../../repros/pr-4226/) — measured, with a control appender
that isolates the cause. `configs/xml/repro-cron-directwrite.xml`.

Note: neither existing bench config reaches this. `rollover-full.xml`'s Cron
appender has a `fileName`; its DirectWrite appender is not cron. All three
conditions have to coincide.

---

## ── paste-ready comment ──

This is an excellent write-up — the root cause is exactly right, and the
unsatisfiable exit condition is not something you would find without the stack
sample.

I reproduced it against a real application on 2.26.1, with three cron appenders
differing only in whether `fileName` is set:

| Appender | `fileName` | `initialize()` → `LastRollForFile` | Returned |
|---|---|---:|---|
| `DirectCronA` | none | **2.996 s** | `null` |
| `DirectCronB` | none | **2.756 s** | `null` |
| `CronWithFileName` | set | **0.000118 s** | `2026-08-09T00:00:00` |

So ~2.8–3.0 s per appender, additive, matching your "roughly three seconds".
End to end the configuration costs 8.36 s wall against 1.98 s for the same
application with a non-cron config.

One thing worth adding to the description, because it makes the bug worse than
it reads: I assumed the delay would disappear once the files exist, and it does
not — a second run measured 8.04 s. For a direct-write appender the manager is
constructed with `file == null`, so `getFileTime()` is 0 on **every** startup.
Your text says this, but "on every startup" is easy to skim past.

On the fix itself: I worked the boundary and agree it preserves results — for
any target comfortably after 1970 the loop exits normally long before
`prevCheckDate` reaches `MIN_DATE`, and when the guard does fire, `null` is what
the old code returned anyway, just ~3 s later.

Two questions:

1. With the `getTimeBefore()` bound in place, is the `fileTime > 0` guard in
   `initialize()` still load-bearing? `getPrevFireTime(new Date(0))` now returns
   `null` in microseconds, which is what the guard substitutes. Happy to keep it
   as documentation, just want to confirm it is belt-and-braces rather than a
   second distinct bug.
2. Could a test assert that an appender **with** a `fileName` still gets a
   non-null `lastRollForFile`? That is the case the new guard could
   accidentally widen to, and it is the one my control appender covers.

Finally: this and #4227 both touch `CronTriggeringPolicy`, in different methods.
Worth noting the intended merge order on both, since #4227 builds on
`initialize()` recording the period start.
