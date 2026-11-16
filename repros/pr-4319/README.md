# PR #4319 — Clear the database appender buffer when committing fails

https://github.com/apache/logging-log4j2/pull/4319

`AbstractDatabaseManager.flush()` cleared the buffer in the same `finally` that called `commitAndClose()`. When `commitAndClose()` threw, `buffer.clear()` never ran, so the batch stayed in the buffer, every following event triggered another flush of the whole (growing) buffer, and nothing bounded it while the failure lasted. The fix moves `clear()` into a nested `finally` and logs a one-time `WARN ... discarded N buffered events after a database flush failure`. The repro uses the JDBC appender (`bufferSize="5"`) against an in-memory H2 database through a `ConnectionFactory` whose `Connection` proxy counts rows sent per flush and fails `commit()` in three ways: always (rollback), once after the commit really reached the database, and once with a rollback.

## Commands

```bash
cd log4j-pr-4319-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `77d892c445`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | A every commit fails, 20 events | each flush sends at most 5 rows | rows per flush `[5, 6, 7, …, 19, 20, 20]` (220 rows sent for 20 events), no warning | FAIL |
| 2.26.1 | B first commit lands then throws, 10 events | 10 rows, no duplicates | 15 rows, `event-1`..`event-5` stored twice | FAIL |
| 2.26.1 | C first commit rolls back, 10 events | (trade-off, informational) | 10 rows stored: the retained batch was retried | INFO |
| 2.x HEAD ea28f5a78b | A every commit fails, 20 events | each flush sends at most 5 rows | rows per flush `[5, 5, 5, 5, 0]`, `discarded 5 buffered events` warned once | PASS |
| 2.x HEAD ea28f5a78b | B first commit lands then throws, 10 events | 10 rows, no duplicates | 10 rows, none duplicated, discard warning | PASS |
| 2.x HEAD ea28f5a78b | C first commit rolls back, 10 events | (trade-off, informational) | 5 rows stored: `event-1`..`event-5` discarded, warned | INFO |

Notes: case C is the cost the review note raised. With `autoCommit=false` a genuinely failed commit rolls back, so the old code's retained buffer acted as a retry and 2.26.1 stores all 10 rows; the fix loses that batch (with a warning). Case B, the duplicate-rows path, only happens when the commit reaches the database and the driver still throws, which is simulated here; the note calls the PR's duplicate-rows claim wrong for the ordinary rollback case, and C agrees. In case B the fixed build's warning says 5 events were discarded although they were committed. In case A, `LoggerContext.stop()` throws `DbAppenderLoggingException` from the shutdown flush on both builds, because the simulated failure also hits the empty final commit.
