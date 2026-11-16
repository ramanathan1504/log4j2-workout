# PR #4401 — JPA `ThrowableAttributeConverter` lost the message of throwables with only a no-arg constructor

https://github.com/apache/logging-log4j2/pull/4401

## Mechanism

When `convertToEntityAttribute` rebuilt a throwable that has a message but no cause, and the class has no `(String)` constructor, it fell back to the no-arg constructor and then set `Throwable.detailMessage` reflectively, but to `cause` (always `null` on that branch) instead of `message`. The restored throwable came back with `getMessage() == null`. The PR writes `message`. `Repro.java` round-trips throwables through `convertToDatabaseColumn` / `convertToEntityAttribute` (needs `--add-opens java.base/java.lang=ALL-UNNAMED` on JDK 17, which `run.sh` passes).

## Commands

```bash
cd log4j-pr-4401-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | no-arg constructor, message `Message from default constructor.` | message kept | `message=null` | FAIL |
| 2.26.1 | control: no-arg constructor with a cause | message kept | `Message from default constructor.` | PASS |
| 2.26.1 | control: `IllegalStateException(String)` | message kept | `String constructor message.` | PASS |
| 2.x HEAD ea28f5a78b | no-arg constructor, message | message kept | `Message from default constructor.` | PASS |
| 2.x HEAD ea28f5a78b | control: no-arg constructor with a cause | message kept | `Message from default constructor.` | PASS |
| 2.x HEAD ea28f5a78b | control: `IllegalStateException(String)` | message kept | `String constructor message.` | PASS |
