# PR #4411 — skip provider-reserved root fields in NoSQL `MapMessage` documents

https://github.com/apache/logging-log4j2/pull/4411 (issue #4331)

With a `MessageLayout`, `NoSqlDatabaseManager.setFields(MapMessage, ...)` copies every `MapMessage` key into the root document.
On MongoDB a key named `_id` therefore becomes the document's primary key: the application controls it, and a repeated value makes
the insert fail with `E11000 duplicate key`, losing the event. The PR adds `NoSqlConnection#isReservedFieldName` (default `false`),
implements it in `MongoDb4Connection` (`_id` only, from `reserved-fields.properties`) and `CouchDbConnection` (`(?s)_.*`), and makes
the manager skip reserved keys with one StatusLogger `WARN` per manager. `log4j-mongodb` has no connection class of its own and
reuses `MongoDb4Connection`, so the `MongoDb` appender gets the same behaviour.

The repro uses the `mongodb` service of `infra/docker-compose.yml` (`mongo:7`, started for this run and left running), a
`NoSql` appender with `MessageLayout` and `MongoDb4`, and logs two `StringMapMessage`s, both with `_id=fixed-id`, `user=u1|u2` and
`_rev=r1|r2`. The oracle reads the collection back with the driver.

## Commands

```bash
docker compose -f ~/apache/log4j2-workout/infra/docker-compose.yml up -d mongodb
log4j-pr-4411-repro/run.sh 2.26.1 2.26.1
log4j-pr-4411-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4411 pr-head-e0ec46b7a9
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | both events stored | 2 documents | 1 (`users=[u1]`); second insert `E11000 duplicate key ... _id_ dup key` | FAIL |
| 2.26.1 | `_id` not taken from the message | `ObjectId` | `String:fixed-id` | FAIL |
| 2.26.1 | non-reserved `_rev` kept (control) | `r1` present | `[r1]` | PASS |
| PR head `e0ec46b7a9` | both events stored | 2 documents | 2 (`users=[u1, u2]`) | PASS |
| PR head `e0ec46b7a9` | `_id` not taken from the message | `ObjectId` | two generated `ObjectId`s | PASS |
| PR head `e0ec46b7a9` | non-reserved `_rev` kept (control) | `r1` present | `[r1, r2]` | PASS |

StatusLogger on the PR head: exactly one `WARN NoSQL manager ... ignored reserved field '_id'; further reserved field warnings will
be suppressed` for two skipped keys.

Raw: `output/<label>/result.txt`, `output/<label>/status.txt`.

Notes: CouchDB (`(?s)_.*`) was not run. The design questions in the review note (properties-file loader vs plain checks, skip vs
prefix, still unanswered on #4331) are not settled by this run; the run only shows the skip behaviour works for MongoDB.
