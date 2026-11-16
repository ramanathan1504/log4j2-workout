# PR #4218 — `LoggerContextAdmin.setConfigLocationUri` leaks the config stream

https://github.com/apache/logging-log4j2/pull/4218

`setConfigLocationUri` opens a `FileInputStream` (or `URL.openStream()`) and hands it to a `ConfigurationSource` without ever closing it. The built-in XML, JSON and properties factories close the stream when they consume it, so the leak needs a factory path that does not read it. The fix wraps both branches in try-with-resources while keeping a stream-backed source, so `monitorInterval` reloads still re-read the file. The repro uses the PR test's shape: a `ConfigurationFactory` that captures `source.getInputStream()` without reading it and returns `DefaultConfiguration`, driven 50 times through the public `LoggerContextAdmin` MBean class.

## Commands

```bash
cd log4j-pr-4218-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | captured streams still readable after the call | 0 of 50 | 50 of 50 | FAIL |
| 2.26.1 | open FDs over 49 further calls | no growth | +49 (13 → 62) | FAIL |
| 2.x HEAD ea28f5a78b | captured streams still readable after the call | 0 of 50 | 0 of 50 | PASS |
| 2.x HEAD ea28f5a78b | open FDs over 49 further calls | no growth | +0 (12 → 12) | PASS |

Notes: the FD count comes from `UnixOperatingSystemMXBean` (macOS here). The stream-read check is the primary oracle. The note's concern that FD counting is noisy did not show in a standalone JVM: one leaked descriptor per call.
