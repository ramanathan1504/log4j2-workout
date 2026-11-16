# PR #4186 — JSON Template Layout `key: {allowed, disallowed}` MDC filter; `EcsLayout.json` stops MDC overwriting ECS fields

https://github.com/apache/logging-log4j2/pull/4186

`ReadOnlyStringMapResolver` (behind the `mdc` and `map` resolvers) now accepts `key` as an object with literal `allowed` / `disallowed` lists, with `disallowed` winning. `EcsLayout.json`'s flattened `labels` MDC resolver disallows `@timestamp`, `message`, `log.logger`, `log.level`, `event.dataset`, `process.thread.name`, `process.thread.id` and `ecs.version`. Before this, a flattened MDC entry named `message` or `log.level` produced a duplicate JSON key next to the real one (#4166).

The MDC holds `message=forged-by-mdc`, `log.level=FATAL`, `user=alice` and `secret=s3cr3t`. One appender uses `classpath:EcsLayout.json`, and a second uses an inline template with `"key":{"allowed":["user"]}`. The oracle counts keys in the written JSON.

## Commands

```bash
cd log4j-pr-4186-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.1 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | ECS, MDC `message` / `log.level` | one `message`, one `log.level` | 2 and 2: `"log.level":"INFO","message":"real-message",…,"log.level":"FATAL","message":"forged-by-mdc"` | FAIL |
| 2.26.1 | `key.allowed=[user]` | `{"message":"real-message","ctx":{"user":"alice"}}` | layout not created (`IllegalArgumentException`); appender fell back to `real-message` plain text | FAIL |
| 2.x HEAD ea28f5a78b | ECS, MDC `message` / `log.level` | one each, `user` kept | 1 and 1, forged values absent, `user` and `secret` kept | PASS |
| 2.x HEAD ea28f5a78b | `key.allowed=[user]` | as above | `{"message":"real-message","ctx":{"user":"alice"}}` | PASS |
