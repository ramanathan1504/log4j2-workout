# PR #4226 — reproduction

https://github.com/apache/logging-log4j2/pull/4226 — *Fix multi-second startup
delay in `CronTriggeringPolicy`*

**Status: reproduced and measured on 2.26.1**, 2026-08-09. No linked issue, no
external service. Config: `configs/xml/repro-cron-directwrite.xml`.

Also carries the #4227 file-naming case — see the caveat at the bottom.

---

## Result

Three cron appenders in one configuration. The only difference between them is
whether `fileName` is set.

| Appender | `fileName` | `initialize()` → `LastRollForFile` | Returned |
|---|---|---:|---|
| `DirectCronA` | none | **2.996 s** | `null` |
| `DirectCronB` | none | **2.756 s** | `null` |
| `CronWithFileName` | set | **0.000118 s** | `2026-08-09T00:00:00` |

A **25,000×** difference between the two shapes, and the cost is **additive per
appender** — two direct-write appenders cost ~5.75 s between them.

End to end, against the same app and scenario:

```
xml/repro-cron-directwrite   8.36 s wall / 7.04 s user
xml/baseline-console         1.98 s wall / 0.51 s user
```

~6.4 s of extra CPU on the main thread, before the application logs anything.
The PR body claims "roughly three seconds per appender"; measured here it is
2.76–3.00 s.

Raw timings: `output/2.26.1-init-timings.log`.

### Reproduce it

```bash
cd ~/apache/log4j2-workout
rm -rf logs/repro-cron-directwrite
BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' \
  ./bench run core-java --config xml/repro-cron-directwrite --log4j 2.26.1 messages 2>&1 \
  | grep -E "Initializing triggering policy|LastRollForFile"
```

Subtract each `Initializing triggering policy` timestamp from the
`LastRollForFile` line that follows it. The `LOGGER.debug("LastRollForFile {},
LastRegularRole {}")` line already exists in released code — nothing was added to
get this.

---

## Why rollover-full.xml does not show it

Three conditions have to hold together:

1. `CronTriggeringPolicy` — so `initialize()` calls `getPrevFireTime`
2. **no `fileName`** — direct write, so `RollingFileManager.getFileTime()` is 0
   and the lookup runs against the epoch
3. a coarse schedule — `findMinIncrement()` returns a day-sized step

`rollover-full.xml`'s `Cron` appender has a `fileName`, so condition 2 fails. Its
`DirectWrite` appender has no `fileName` but uses TimeBased+SizeBased policies,
so condition 1 fails. **Neither triggers it** — which is why this needed a new
config rather than an existing cell.

## One correction to the PR's framing, in the PR's favour

I expected the delay to disappear on a second run, once the files exist. It does
not — the warm run measured **8.04 s**, statistically identical to the cold
8.36 s.

The author is right and my expectation was wrong: for a direct-write appender the
manager is constructed with `file == null`, so `getFileTime()` returns 0 on
**every** startup, not only the first. The PR body says exactly this ("on every
startup of an appender that writes directly to the file its pattern resolves
to"). Worth knowing, because "delete the logs and it gets slow again" would be a
much weaker bug than "it is slow every single time".

---

## Verifying the fix

```bash
cd ~/apache/logging-log4j2
git stash push -m "pre-4226" log4j-perf-test/src/main/java/org/apache/logging/log4j/perf/jmh/AsyncTraceContextBenchmark.java

cd ~/apache/log4j2-workout
./bench pr 4226 --checkout --install

rm -rf logs/repro-cron-directwrite
time ./bench run core-java --config xml/repro-cron-directwrite messages
```

**Expect:** wall time drops to roughly the `baseline-console` figure (~2 s), and
both direct-write appenders report `LastRollForFile null` in well under a
millisecond.

Two things to check beyond "it got faster":

- `CronWithFileName` must still report `LastRollForFile 2026-08-09T00:00:00`,
  **not** `null`. The PR adds a `fileTime > 0` guard in `initialize()`; if that
  guard is wrong, the control silently loses its previous-roll lookup.
- The file names must not change. That is #4227's job, not this PR's.

Restore afterwards — until this runs, `2.27.0-SNAPSHOT` *is* PR #4226:

```bash
cd ~/apache/logging-log4j2 && git switch 2.x && git stash pop && mvn install -DskipTests
```

---

## Caveat on the #4227 half — it cannot be shown today

The same config was built to demonstrate #4227's file naming, and **today
defeats it.** The schedule is weekly on Sunday and today is **Sunday
2026-08-09**, so the period start and "now" fall on the same date. Both the
buggy naming (current time) and the fixed naming (period start) produce
`error-a.log-20260809`, and the files confirm it:

```
logs/repro-cron-directwrite/error-a.log-20260809
logs/repro-cron-directwrite/error-b.log-20260809
```

To see the #4227 difference, run this config **any day except Sunday** — Monday
onward, the buggy name is that day's date while the correct name stays
`20260809`. Alternatively change the schedule to a monthly one (`0 0 0 1 * ?`)
so the period start is further from today.

Reviews: [`docs/pr-reviews/4226-cron-startup-delay.md`](../../docs/pr-reviews/4226-cron-startup-delay.md),
[`docs/pr-reviews/4227-directwrite-cron-naming.md`](../../docs/pr-reviews/4227-directwrite-cron-naming.md)
