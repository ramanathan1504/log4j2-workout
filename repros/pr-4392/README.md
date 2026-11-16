# PR #4392 — an idle `RollingFileManager` thread kept the JVM from exiting

https://github.com/apache/logging-log4j2/pull/4392

## Mechanism

#4165 (`7775e125ae`, unreleased) replaced the manager's unbounded `ThreadPoolExecutor(0, MAX_VALUE, 0 ms)` with `new ScheduledThreadPoolExecutor(1, threadFactory)` so the asynchronous rollover chain can be delayed. That executor keeps its one core thread forever, and `Log4jThreadFactory.createThreadFactory("RollingFileManager")` makes non-daemon threads, so after the first asynchronous rollover action (here `.gz` compression) the JVM never exits once `main` returns: the shutdown hook that would stop the manager only runs after the last non-daemon thread ends. The PR uses a zero-core executor with `allowCoreThreadTimeOut(true)` and a 1 s keep-alive. `Repro.java` logs 40 lines into a `RollingFile` (`SizeBasedTriggeringPolicy size="1 KB"`, `.gz` pattern), sleeps 3 s, lists the live non-daemon threads and returns; `run.sh` waits up to 15 s for the process to exit and kills it otherwise.

No release has the bug: 2.26.1 still has the old executor. The "before" column is therefore a 2.x build after #4165 and before #4392: the head of open PR #4175 (`b10b76ab00`, jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4175`), whose `RollingFileManager.java` differs from `1f23510d85^` only by the unrelated compression-level initialization and whose branch touches no rolling code.

## Commands

```bash
cd log4j-pr-4392-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4175 2.x-pre-fix-b10b76ab00
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt` and `app-stdout.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: rollover ran | ≥ 1 `.gz` | 2 | PASS |
| 2.26.1 | non-daemon threads 3 s after the rollover | none | `[]` | PASS |
| 2.26.1 | JVM exits after `main` returns | exits | exited within 4 s | PASS |
| 2.x pre-fix b10b76ab00 | control: rollover ran | ≥ 1 `.gz` | 2 | PASS |
| 2.x pre-fix b10b76ab00 | non-daemon threads 3 s after the rollover | none | `[Log4j2-TF-1-RollingFileManager-1]` | FAIL |
| 2.x pre-fix b10b76ab00 | JVM exits after `main` returns | exits | still running after 15 s, killed | FAIL |
| 2.x HEAD ea28f5a78b | control: rollover ran | ≥ 1 `.gz` | 2 | PASS |
| 2.x HEAD ea28f5a78b | non-daemon threads 3 s after the rollover | none | `[]` | PASS |
| 2.x HEAD ea28f5a78b | JVM exits after `main` returns | exits | exited within 5 s | PASS |
