# PR #4442 — plugin builders in GraalVM native images (#4441)

https://github.com/apache/logging-log4j2/pull/4442

Written by hand in the `repros/pr-*` layout: the pack has no `repro` or `pr` verb (`oss run --pack ~/apache/log4j2-workout list`
shows apps and versions only), and `native-image` is not installed on this machine, so the native run is not done.

## Mechanism

On 2.26.0 `PluginBuilder.injectFields` and `validateFields` call `setAccessible` on every declared field of the builder
and its superclasses, while `GraalVmProcessor` registers only fields with Log4j annotations, and skips `@PluginAttribute`
and constraint-only fields. In a native image the unregistered fields fail. On a JVM the same code fails when a builder
extends a JDK class, because `java.base` does not open its private fields (`InaccessibleObjectException`).
The PR makes `PluginBuilder` reflect only on fields with a visitor or constraint annotation, and adds `@PluginAttribute`
and the four constraint annotations to the processor.

## Commands

```bash
cd log4j-pr-4442-repro
./run.sh 2.26.0 2.26.0
./run.sh <dir with log4j-api and log4j-core 2.27.0-SNAPSHOT built from ab756f7d58> pr-head-ab756f7d58
```

The jars for the second run were built with `./mvnw -pl log4j-core -am package -DskipTests` in a worktree at the PR head
(`ab756f7d58`), JDK 17.0.14. The app uses a custom filter plugin `PrefixDeny` whose builder extends `AtomicInteger`,
on the root logger, and logs `keep this event` and `drop this event`. The run also extracts the generated
`reflect-config.json` from the `log4j-core` jar.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | `builder` | filter built, `drop` event denied | `ERROR Could not create plugin of type class repro.PrefixDenyFilter … InaccessibleObjectException … AtomicInteger.serialVersionUID`; filter missing, `drop` event written | FAIL |
| PR head ab756f7d58 | `builder` | filter built, `drop` event denied | no status error, `keep=true drop=false` | PASS |
| 2.26.0 | metadata | — | 346 builder fields registered | INFO |
| PR head ab756f7d58 | metadata | `@PluginAttribute` builder fields registered | 356; new: `KafkaAppender$Builder` `key`, `retryCount`, `sendEventTimestamp`, `syncSend`, `topic`; `Routes$Builder` `pattern` | PASS |

The metadata diff is 2.26.0 against the PR head, so it also carries other `2.x` changes since 2.26.0
(`RegexFilter$Builder`, `maxRandomDelay`); the `KafkaAppender` and `Routes` fields are the ones this PR adds.
Outputs: `output/2.26.0/`, `output/pr-head-ab756f7d58/`.
