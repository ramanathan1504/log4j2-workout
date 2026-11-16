# PR #4072 — `RollingFile createOnDemand="true"` creates the log directory at startup

https://github.com/apache/logging-log4j2/pull/4072

In `RollingFileManager`'s factory, the `fileName` branch called `FileUtils.makeParentDirs(file)` unconditionally and
only skipped `createNewFile()` for `createOnDemand`, so the directory appeared at configuration time (#2006). The PR
(`878f951809`) moves both calls under `if (!createOnDemand)`, and `createParentDir()`, which used to make the directory
only for direct-write managers, now always calls `makeParentDirs` when the stream is first opened.

## Commands

```bash
cd log4j-pr-4072-repro
./run.sh 2.26.0 2.26.0
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
./run.sh 2.26.1 2.26.1
```

Three `RollingFile` appenders write into directories that do not exist yet: `ON_DEMAND` (`fileName`, `createOnDemand="true"`),
`ON_DEMAND_DIRECT` (no `fileName`, `DirectWriteRolloverStrategy`, `createOnDemand="true"`) and `EAGER`
(`createOnDemand="false"`, control). `Repro` checks the file system after the configuration starts and again after one
event per on-demand appender.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | on-demand: parent dir before first event | absent | exists | FAIL |
| 2.26.0 | on-demand: file before first event | absent | absent | PASS |
| 2.26.0 | on-demand: file after first event | written | 21 bytes | PASS |
| 2.26.0 | on-demand direct-write: parent dir before first event | absent | exists | FAIL |
| 2.26.0 | control `createOnDemand=false`: file at startup | exists | exists | PASS |
| 2.x HEAD ea28f5a78b | on-demand: parent dir before first event | absent | absent | PASS |
| 2.x HEAD ea28f5a78b | on-demand: file before first event | absent | absent | PASS |
| 2.x HEAD ea28f5a78b | on-demand: file after first event | written | 21 bytes | PASS |
| 2.x HEAD ea28f5a78b | on-demand direct-write: parent dir before first event | absent | exists | FAIL |
| 2.x HEAD ea28f5a78b | control `createOnDemand=false`: file at startup | exists | exists | PASS |

2.26.1 matches 2.x HEAD on every row, so the fix shipped in 2.26.1.

Notes: the direct-write case still creates its directory at startup on 2.x HEAD. `DirectWriteRolloverStrategy`
scans for existing files through `AbstractRolloverStrategy.getEligibleFiles`, which calls `parent.mkdirs()`; the PR
did not touch that path. `RollingRandomAccessFile` has no `createOnDemand` and was not tested.
