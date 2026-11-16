# PR #4469 — layout header missing from `RandomAccessFile` and `MemoryMappedFile` appenders

https://github.com/apache/logging-log4j2/pull/4469

`RandomAccessFileManager` and `MemoryMappedFileManager` pass `NullOutputStream` to `OutputStreamManager` with `writeHeader`, so the layout header was written to the null stream, while the footer goes through `write()` and reaches the file. The fix passes `false` to `super` and writes the header through the manager buffer once the file is set up. The repro logs one event to `File`, `RandomAccessFile` and `MemoryMappedFile` appenders, each `append="false"` with `PatternLayout header="HEADER%n" footer="FOOTER%n"`, and compares each file with `HEADER
DATA
FOOTER
` (NUL padding stripped for the mapped file).

## Commands

```bash
cd log4j-pr-4469-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

The second run resolves third-party dependencies at 2.26.1 and replaces every `log4j-*` jar with the `2.27.0-SNAPSHOT` jar built from `2.x` HEAD `ea28f5a78b`, which contains this change.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | File (control) | HEADER, DATA, FOOTER | HEADER\nDATA\nFOOTER\n | PASS |
| 2.26.1 | RandomAccessFile | HEADER, DATA, FOOTER | DATA\nFOOTER\n | FAIL |
| 2.26.1 | MemoryMappedFile | HEADER, DATA, FOOTER | DATA\nFOOTER\n | FAIL |
| 2.x HEAD ea28f5a78b | File (control) | HEADER, DATA, FOOTER | HEADER\nDATA\nFOOTER\n | PASS |
| 2.x HEAD ea28f5a78b | RandomAccessFile | HEADER, DATA, FOOTER | HEADER\nDATA\nFOOTER\n | PASS |
| 2.x HEAD ea28f5a78b | MemoryMappedFile | HEADER, DATA, FOOTER | HEADER\nDATA\nFOOTER\n | PASS |
