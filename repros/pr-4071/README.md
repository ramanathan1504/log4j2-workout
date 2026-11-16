# PR #4071 — `maxCompressionDelaySeconds` on `DefaultRolloverStrategy` (superseded by #4165)

https://github.com/apache/logging-log4j2/pull/4071

The PR (`446d2baa20`, for #4012) added a `maxCompressionDelaySeconds` attribute to `DefaultRolloverStrategy`, passed
through `FileExtension` to `GzCompressAction`/`ZipCompressAction`, which slept a random 0..N seconds before compressing.
`7775e125ae` (#4165, "Adds `maxRandomDelay` configure to Rolling Appenders") later removed all of it from `2.x` and
replaced it with a `maxRandomDelay` attribute on `RollingFile`/`RollingRandomAccessFile` that delays the whole
asynchronous action chain. So the attribute this PR introduced is in no release and not in 2.x HEAD.

## Commands

```bash
cd log4j-pr-4071-repro
./run.sh 2.26.0 2.26.0
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Three `RollingFile` appenders roll to `.gz` at 1 KB: `DELAYED` (`DefaultRolloverStrategy maxCompressionDelaySeconds="2"`),
`SUCCESSOR` (`RollingFile maxRandomDelay="2"`) and `IMMEDIATE` (neither). Each runs five rollovers; for each, `Repro`
measures the time from the triggering event to the new `.gz` appearing, then decompresses every archive and checks its
content. A delay counts as applied when the largest of the five latencies is between 0.9 s and 3.5 s (all five draws
being 0 s has probability 1/243).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | `maxCompressionDelaySeconds=2` | delay applied | rejected: `DefaultRolloverStrategy contains an invalid element or attribute "maxCompressionDelaySeconds"`; latencies 30, 17, 18, 21, 18 ms | FAIL |
| 2.26.0 | `maxRandomDelay=2` (#4165) | delay applied | rejected: `RollingFile contains an invalid element or attribute "maxRandomDelay"`; max 13 ms | FAIL |
| 2.26.0 | control, no delay | < 0.9 s | max 25 ms | PASS |
| 2.26.0 | archives decompress | 5/5 each | 5/5 each | PASS |
| 2.x HEAD ea28f5a78b | `maxCompressionDelaySeconds=2` | delay applied | rejected, same StatusLogger error; latencies 12, 2, 1, 14, 1 ms | FAIL |
| 2.x HEAD ea28f5a78b | `maxRandomDelay=2` (#4165) | delay applied | 1276, 1190, 1719, 1612, 1349 ms | PASS |
| 2.x HEAD ea28f5a78b | control, no delay | < 0.9 s | max 15 ms | PASS |
| 2.x HEAD ea28f5a78b | archives decompress | 5/5 each | 5/5 each | PASS |

Notes: NO-DIFF for this PR's own attribute; both columns reject it. The behaviour #4012 asked for ships as
`maxRandomDelay` (#4165). The PR's head was not built, so its `maxCompressionDelaySeconds` was never run here.
