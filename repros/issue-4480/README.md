# Issue #4480 — `MemoryMappedFileAppender` ignores `immediateFlush="true"`

https://github.com/apache/logging-log4j2/issues/4480

Written by hand: `oss run --pack ... repro 4480` failed in this oss build
(`scenario source not found: .../ExceptionScenario.java`; `--version` flag not accepted).

## Reporter's input (unchanged)

> In `MemoryMappedFileAppender.Builder.build()`, the configured value is correctly passed to
> `MemoryMappedFileManager` [...] However, the `MemoryMappedFileAppender` constructor receives a hard-coded false.
> As a result, setting `immediateFlush`=`true` does not enable per-event flushing, contrary to the documented behavior.

No version given; the code is unchanged on every release checked.

## Command

```bash
cd log4j-issue-4480-repro
./run.sh 2.26.1 | tee ../output/run-2.26.1.txt
./run.sh 2.25.3 | tee ../output/run-2.25.3.txt
```

`src/main/resources/log4j2.xml` configures `MemoryMappedFile` with `immediateFlush="true"` and `"false"`,
plus a `RandomAccessFile` control with `immediateFlush="true"`.

## Results

| Version | Appender | Configured | `appender.getImmediateFlush()` | `manager.isImmediateFlush()` |
|---|---|---|---|---|
| 2.26.1 | `MemoryMappedFile` | true | **false** | true |
| 2.26.1 | `MemoryMappedFile` | false | false | false |
| 2.26.1 | `RandomAccessFile` (control) | true | true | n/a |
| 2.25.3 | `MemoryMappedFile` | true | **false** | true |
| 2.25.3 | `MemoryMappedFile` | false | false | false |
| 2.25.3 | `RandomAccessFile` (control) | true | true | n/a |

Fix check on `origin/2.x` `953933a9b7` (clone, restored afterwards): a test asserting
`getImmediateFlush()` on `MemoryMappedFileAppenderLocationTest.xml` (`immediateFlush="true"`)
fails unfixed (`expected: <true> but was: <false>`) and passes with `false` → `isImmediateFlush()`;
`MemoryMappedFileAppenderTest` 5/5 and `MemoryMappedFileManagerTest` 2/2 green with the fix.

## Mechanism

- `MemoryMappedFileAppender.Builder.build()` passes `isImmediateFlush()` to the manager but a literal `false` to the appender.
- `AbstractOutputStreamAppender.directEncodeEvent()` / `writeByteArrayToManager()` flush only when the appender's own flag or `event.isEndOfBatch()` is true.
- `MemoryMappedFileManager.write()` ignores its `immediateFlush` argument; `flush()` is `mappedBuffer.force()`.
- So with synchronous loggers (`endOfBatch` always false) the mapped buffer is forced only on remap or close. Bytes are still in the OS page cache, so readers see them; what is lost is the per-event `msync` durability against an OS crash or power loss.
