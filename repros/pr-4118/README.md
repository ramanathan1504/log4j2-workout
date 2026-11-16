# PR #4118 — `ConfigurationScheduler.scheduleWithCron` NPE when the first fire runs before the future is assigned

https://github.com/apache/logging-log4j2/pull/4118

## Mechanism

On 2.26.1 `scheduleWithCron` calls `schedule(...)` first and only then calls `runnable.setScheduledFuture(...)`.
If the first fire runs in between, `CronRunnable.run()` dereferences a `null` `scheduledFuture`: the command is
skipped, the `finally` block reschedules and NPEs again on `reset`, and the `CronScheduledFuture` returned to the
caller wraps the stale first future, so `cancel()` does not stop the chain. The PR creates the
`CronScheduledFuture` before scheduling, null-guards `run()`/`toString()`, and only resets it forward in time.

## Commands

```bash
cd log4j-pr-4118-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2x-head-ea28f5a78b
```

`Repro.java` forces the interleaving the way the PR's test does: a `ConfigurationScheduler` subclass whose first
`schedule(...)` call schedules with delay 0 and waits (up to 3 s) for the command before returning. Cron is
`0/1 * * * * ?`; the returned future is then cancelled and runs are counted for 2.5 s.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | first fire runs the command | ran | did not run within 3 s | FAIL |
| 2.26.1 | runs after `cancel()` (2.5 s) | 0 | 3 | FAIL |
| 2.26.1 | `caught error running command` status errors | 0 | 4, each `NullPointerException … "this.scheduledFuture" is null` | FAIL |
| 2.x HEAD ea28f5a78b | first fire runs the command | ran | ran | PASS |
| 2.x HEAD ea28f5a78b | runs after `cancel()` (2.5 s) | 0 | 0 | PASS |
| 2.x HEAD ea28f5a78b | `caught error running command` status errors | 0 | 0 | PASS |

## Notes

- The interleaving is forced through a public, overridable method. In production it needs the first fire to be
  due immediately (a past `startDate`, or a fire time within the scheduling call), so it is rare but real.
- The baseline's ineffective `cancel()` is the worse half: a cancelled cron task kept firing every second.
