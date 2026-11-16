# PR #4124 — `DisruptorUtil` logs the detected LMAX Disruptor major version

https://github.com/apache/logging-log4j2/pull/4124

## Mechanism

`DisruptorUtil.detectDisruptorMajorVersion()` probes for `com.lmax.disruptor.SequenceReportingEventHandler`
(Disruptor 3 only). The PR loads it with `initialize=false` instead of `true`, and adds
`LOGGER.debug("LMAX Disruptor version detected: {}", version)`. The `return 3` stays inside the `try`, so the
debug line is reached only on the Disruptor 4 path, which the PR's own test asserts.

## Commands

```bash
cd log4j-pr-4124-repro
./run.sh 2.26.0 2.26.0
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2x-head-ea28f5a78b
```

`run.sh` runs the app twice per version, with `com.lmax:disruptor` 4.0.0 and 3.4.4, using
`-Dlog4j2.contextSelector=…AsyncLoggerContextSelector -Dlog4j2.StatusLogger.level=DEBUG`, and greps the status output.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | Disruptor 4.0.0: async logger writes the event (control) | written, 0 status errors | written, 0 | PASS |
| 2.26.0 | Disruptor 4.0.0: DEBUG version line | `LMAX Disruptor version detected: 4` | absent | FAIL |
| 2.26.0 | Disruptor 3.4.4: async logger writes the event (control) | written, 0 status errors | written, 0 | PASS |
| 2.26.0 | Disruptor 3.4.4: DEBUG version line | none (early return) | absent | INFO |
| 2.x HEAD ea28f5a78b | Disruptor 4.0.0: async logger writes the event (control) | written, 0 status errors | written, 0 | PASS |
| 2.x HEAD ea28f5a78b | Disruptor 4.0.0: DEBUG version line | `LMAX Disruptor version detected: 4` | present | PASS |
| 2.x HEAD ea28f5a78b | Disruptor 3.4.4: async logger writes the event (control) | written, 0 status errors | written, 0 | PASS |
| 2.x HEAD ea28f5a78b | Disruptor 3.4.4: DEBUG version line | none (early return) | absent | INFO |

## Notes

- The diagnostic is one-sided: Disruptor 3 users, who are the ones most likely to hit the 3/4 `LinkageError`
  mix-up the PR title refers to, still get no version line. A `LinkageError` itself was not reproduced here; the
  `initialize=false` change has no visible effect with either a clean 3.4.4 or 4.0.0 classpath.
