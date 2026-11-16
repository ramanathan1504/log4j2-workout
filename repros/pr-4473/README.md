# PR #4473 — Add `createOnDemand` to the random access and memory mapped file appenders

https://github.com/apache/logging-log4j2/pull/4473

On 2.26.1, `RandomAccessFile`, `MemoryMappedFile` and `RollingRandomAccessFile` have no `createOnDemand` builder
attribute: the config gets a StatusLogger `ERROR … contains an invalid element or attribute "createOnDemand"`, and each
manager opens (and creates) its file and parent directories when the configuration starts.
The PR adds the attribute to the three appenders. With it set, `RandomAccessFileManager` and `MemoryMappedFileManager`
start with no open file (`randomAccessFile == null`, an empty byte buffer) and open, truncate if `append="false"`, and map
on the first write (`openFile()` / `openAndMap()`); `RollingRandomAccessFileManager` already supported a lazy open and now
gets the flag from its appender.

## Setup

For each of `RandomAccessFile`, `MemoryMappedFile`, `RollingRandomAccessFile` and a `File` control, two appenders with
`createOnDemand="true"`: one on logger `never` (never logged to), one on logger `used` (one event, `first-event`).
`Repro.java` checks for each `app.log` after startup, after the event and after shutdown.

## Commands

```bash
cd log4j-pr-4473-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-pr-4473 pr-4473-head
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | RAF / MMF / RollingRAF, after startup (never and used) | no `app.log` | `app.log` exists | **FAIL** ×6 |
| 2.26.1 | RAF / MMF / RollingRAF, never-used, after shutdown | no `app.log` | `app.log` exists | **FAIL** ×3 |
| 2.26.1 | RAF / MMF / RollingRAF, after one event | `first-event` | `first-event` | PASS ×3 |
| 2.26.1 | `File` control, all phases | no `app.log` / `first-event` | as expected (dir exists) | PASS |
| 2.26.1 | StatusLogger | — | 6× `ERROR … invalid element or attribute "createOnDemand"` | — |
| PR head bb88685734 | RAF / MMF / RollingRAF, after startup (never and used) | no `app.log` | no `app.log` (no dir) | PASS ×6 |
| PR head bb88685734 | RAF / MMF / RollingRAF, never-used, after shutdown | no `app.log` | no `app.log` (no dir) | PASS ×3 |
| PR head bb88685734 | RAF / MMF / RollingRAF, after one event | `first-event` | `first-event` | PASS ×3 |
| PR head bb88685734 | `File` control, all phases | no `app.log` / `first-event` | as expected (dir exists) | PASS |
| PR head bb88685734 | StatusLogger | — | empty | — |

Full output in `output/<label>/result.txt`, with the log directories.

Notes: the new appenders do not create the parent directory until the first write, while `File` with
`createOnDemand="true"` creates it at startup; the PR's behaviour matches its own Javadoc ("the file and its parent
directories"). The note's F1 (header written at construction once #4469 lands) is not exercised: no header is configured.
