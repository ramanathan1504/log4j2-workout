# PR #4125 — `KafkaAppender` reports an error after a successful retry

https://github.com/apache/logging-log4j2/pull/4125

## Mechanism

In `KafkaAppender.append`, when the first `tryAppend` throws and `retryCount` is set, the retry loop exits on
success with `break`, and execution falls through to `error("Unable to write to Kafka in appender …")`. So a
transient failure recovered by a retry is still reported to the `ErrorHandler`. The PR replaces `break` with
`return` and logs failed retry attempts at DEBUG.

## Commands

```bash
cd log4j-pr-4125-repro
./run.sh 2.26.0 2.26.0
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2x-head-ea28f5a78b
```

No broker: `Repro.java` sets the package-private `KafkaManager.producerFactory` by reflection (as the PR's test
does) to return a kafka-clients 3.9.1 `MockProducer` whose first `send` throws. Config: `Kafka` appender with
`retryCount="2" syncSend="true" ignoreExceptions="true"`; the app logs one event and `run.sh` greps the StatusLogger
output at WARN.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | first send fails, retry delivers once (control) | 2 attempts, `[retried-event]` | 2, `[retried-event]` | PASS |
| 2.26.0 | errors reported after successful retry | 0 | 1: `ERROR Unable to write to Kafka in appender [KafkaWithRetry]` with the first attempt's exception | FAIL |
| 2.x HEAD ea28f5a78b | first send fails, retry delivers once (control) | 2 attempts, `[retried-event]` | 2, `[retried-event]` | PASS |
| 2.x HEAD ea28f5a78b | errors reported after successful retry | 0 | 0 | PASS |
