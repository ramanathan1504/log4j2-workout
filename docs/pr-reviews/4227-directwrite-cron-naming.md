# PR #4227 — Name direct write cron files after the rollover period they cover

https://github.com/apache/logging-log4j2/pull/4227

| | |
|---|---|
| Author | `tupelo-schneck` |
| Prior merged PRs in this repo | **0** (2 open: #4226, #4227) |
| Base | `2.x` |
| Size | +50 −3, 4 files |
| Linked issue | none |
| CI | green |

## Verdict: ✅ technically sound. The decision is the maintainers', and the author says so themselves.

## Is it really needed?

The bug is real. An appender with no `fileName` writes directly to the file its
pattern resolves to; that file covers a whole rollover period, but it is named
after the moment the process started. A restart mid-period opens a *second* file
for a period that should have one. Daily restarts across a week leave seven
files where the schedule implies one, and rollover only ever closes out whichever
fragment happens to be current.

What separates this from most of the batch is the first line of the PR body:

> **This changes the names of direct write files for cron based appenders.** That
> is the point of the change, but it is user visible on upgrade, so it deserves a
> decision rather than a rubber stamp.

That is the opposite of resume-padding behaviour. The author is flagging their
own change's blast radius and explicitly declining to have it waved through.

## The analysis is correct — I checked the part that could have been wrong

The change removes the `LOG4J2-3339` workaround at
`DirectWriteRolloverStrategy.java:393-394`:

```java
// LOG4J2-3339 - Always use the current time for new direct write files.
manager.getPatternProcessor().setCurrentFileTime(System.currentTimeMillis());
```

**The obvious risk is that this reintroduces LOG4J2-3339 for every non-cron
direct-write appender.** The author claims it does not, because policies that do
not track a period leave the value at 0 and `formatFileName()` falls back on its
own. That claim is load-bearing, so I verified it rather than accepting it —
`PatternProcessor.java:301-303`:

```java
final long time = useCurrentTime
        ? currentFileTime != 0 ? currentFileTime : System.currentTimeMillis()
        : prevFileTime != 0 ? prevFileTime : System.currentTimeMillis();
```

Confirmed. And the three policy paths are all covered:

| Policy | Sets `currentFileTime`? | After this PR |
|---|---|---|
| `CronTriggeringPolicy` | ✅ `initialize()` sets period start | named after the period — the fix |
| `TimeBasedTriggeringPolicy` | ✅ line 171 sets `currentTimeMillis()` | **unchanged** |
| SizeBased / OnStartup only | ❌ leaves it 0 | falls back to `currentTimeMillis()` — **unchanged** |

So the removal is safe. Nothing regresses to LOG4J2-3339.

The second change is also right. `RollingFileManager.rollover(prevFileTime,
prevRollTime)` at line 589 does `setCurrentFileTime(prevRollTime.getTime())` — so
the **second** argument names the *new* file. Passing `lastRollDate` there named
each replacement file after the file it had just rolled; `rollTime` names it
after the period it opens.

## What the maintainers actually have to decide

Not correctness — compatibility. On upgrade, direct-write cron file names change.
Anyone with log shipping, retention globs, or dashboards keyed to the old names is
affected, silently, at the moment they upgrade a patch release.

Options worth putting to `ppkarwasz`/`vy`:

1. Take it as a fix, with a prominent changelog entry (author's implicit proposal)
2. Gate it behind an attribute, defaulting to today's behaviour on `2.x`
3. Take it on `main` only, where a breaking change is in scope

The changelog entry as written (`fix_direct_write_cron_current_file_name.xml`)
should say the names change, not only that they are now correct.

## Non-blocking

- The new test `testDirectWriteFileNameUsesPeriodStart` computes its expected
  value with `new CronExpression(schedule).getPrevFireTime(new Date())` — the same
  call the production code makes. It will pass if both are wrong together. A
  hard-coded expected date with a fixed clock would be stronger, though awkward
  here.
- `SimpleDateFormat` in a test is not thread-safe, but it is local, so fine.
- `main` diverged in this area (#2921 moved compression; the rollover strategies
  changed too). Worth confirming whether `main` needs the same fix or already
  behaves correctly.

## Repro

[`repros/pr-4226/`](../../repros/pr-4226/) — the same config
(`configs/xml/repro-cron-directwrite.xml`) carries both PRs.

**It cannot demonstrate this one today.** The schedule is weekly on Sunday and
today is Sunday 2026-08-09, so period start and "now" are the same date — buggy
and fixed naming both produce `error-a.log-20260809`. Run it Monday onward, or
switch the schedule to monthly, and the difference appears. That caveat is
recorded in the repro README rather than papered over.

---

## ── paste-ready comment ──

Thank you for leading with the compatibility note — that is the right call, and
it is the part that needs a maintainer decision rather than a review.

On the mechanics I think you are right, and I checked the step that worried me
most: removing the `LOG4J2-3339` override could plausibly regress every
*non-cron* direct-write appender. It does not, and
`PatternProcessor.java:301-303` is why —

```java
final long time = useCurrentTime
        ? currentFileTime != 0 ? currentFileTime : System.currentTimeMillis()
```

— so a policy that never sets the value gets the same `System.currentTimeMillis()`
it got before. Walking the three paths: `CronTriggeringPolicy` sets the period
start (the fix), `TimeBasedTriggeringPolicy` sets `currentTimeMillis()` itself at
line 171 (unchanged), and size/onStartup-only configurations leave it at 0 and
hit the fallback (unchanged). Agreed that nothing regresses.

The `manager.rollover(..., lastRollDate)` → `rollTime` change also looks right:
`RollingFileManager.rollover` assigns the second argument via
`setCurrentFileTime`, so it names the *new* file, and `lastRollDate` was naming
each replacement after the file it had just rolled.

Two things:

- The changelog entry should say the file names **change**, not only that they
  are now correct. Someone with retention globs or log shipping keyed to the old
  names will hit this silently on a patch upgrade.
- `testDirectWriteFileNameUsesPeriodStart` derives its expected value from
  `getPrevFireTime(new Date())` — the same call the production path makes — so it
  would pass if both moved together. Could it pin a fixed clock and a literal
  expected name instead?

@ppkarwasz @vy — this needs a call on compatibility: take it as a fix with a
loud changelog entry, gate it behind an attribute defaulting to current
behaviour on `2.x`, or land it on `main` only?

Separately: this and #4226 both touch `CronTriggeringPolicy` (different methods,
so no conflict), and this one builds on `initialize()` recording the period start
— worth noting the intended merge order.
