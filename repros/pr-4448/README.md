# PR #4448 — [main] Honor `compressionLevel` in the rollover strategies (#4234 port)

https://github.com/apache/logging-log4j2/pull/4448

On `main`, #2921 removed `compressionLevel` from `DefaultRolloverStrategy` and `DirectWriteRolloverStrategy` in favour of
an options map, but both strategies pass `Map.of()` to `CompressActionFactory.createCompressAction`. The XSD still
declares the attribute, so a 2.x config with `compressionLevel` only gets a StatusLogger error and archives use the
default level. The PR brings the attribute back, puts it into the options map under
`CompressActionFactory.COMPRESSION_LEVEL` (`AbstractRolloverStrategy.getCompressOptions()`), passes it on both the plain
and the `tempCompressedFilePattern` branches, and makes the JRE gz/zip factories (and `log4j-compress`) honour it; an
unsupported level warns once and falls back to the default.

## Setup

Eight `RollingFile` appenders, `SizeBasedTriggeringPolicy size="40KB"`, 600 deterministic lines (~61 KB), so each rolls
once into `app-1.log.<ext>`. `Repro.java` decompresses each archive and prints compressed size, raw size and ratio.
Level 0 (stored) must give ratio ≥ 0.9; level 9, unset and the invalid level 42 are controls (ratio < 0.5).
Only the JRE gz and zip factories are covered; `log4j-compress` (bzip2, deflate, zstd) is not on the classpath.

## Commands

```bash
cd log4j-pr-4448-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-head
./run.sh <scratchpad>/wt/jars-pr-4448 pr-4448-head
```

`log4j-api` is 2.24.3 on every run; the configuration is passed with `log4j.configuration.location`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | Default gz level 0 | ratio ≥ 0.9 | 1191 / 41000 = 0.029 | **FAIL** |
| 3.0.0-beta3 | Default gz level 0 + `tempCompressedFilePattern` | ratio ≥ 0.9 | 0.029 | **FAIL** |
| 3.0.0-beta3 | Default zip level 0 | ratio ≥ 0.9 | 1305 / 41000 = 0.032 | **FAIL** |
| 3.0.0-beta3 | DirectWrite gz level 0 | ratio ≥ 0.9 | 0.029 | **FAIL** |
| 3.0.0-beta3 | DirectWrite gz level 0 + `tempCompressedFilePattern` | ratio ≥ 0.9 | 0.029 | **FAIL** |
| 3.0.0-beta3 | gz level 9 / unset / 42 (controls) | ratio < 0.5 | 0.029 each | PASS |
| 3.0.0-beta3 | StatusLogger | — | 7× `ERROR … invalid element or attribute "compressionLevel"` | — |
| main HEAD 2bb673f650 | all eight cases | as above | identical to 3.0.0-beta3, same 7 errors | FAIL ×5, controls PASS |
| PR head e1a8f8154b | Default gz level 0 | ratio ≥ 0.9 | 41028 / 41000 = 1.001 | PASS |
| PR head e1a8f8154b | Default gz level 0 + `tempCompressedFilePattern` | ratio ≥ 0.9 | 1.001 | PASS |
| PR head e1a8f8154b | Default zip level 0 | ratio ≥ 0.9 | 41142 / 41000 = 1.003 | PASS |
| PR head e1a8f8154b | DirectWrite gz level 0 | ratio ≥ 0.9 | 1.001 | PASS |
| PR head e1a8f8154b | DirectWrite gz level 0 + `tempCompressedFilePattern` | ratio ≥ 0.9 | 1.001 | PASS |
| PR head e1a8f8154b | gz level 9 / unset / 42 (controls) | ratio < 0.5 | 0.029 each | PASS |
| PR head e1a8f8154b | StatusLogger | one warning for 42 | 1× `WARN Unsupported gz compression level '42' (invalid compression level); using the default level.` | PASS |
| PR head e1a8f8154b, rerun 2026-11-17 (`output/pr-4448-head-2026-11-17`) | all eight cases | as above | identical to the PR head row above: five level-0 cases ratio 1.001 / 1.003, controls 0.029, one WARN for 42 | PASS ×8 |

Full output in `output/<label>/result.txt`, with the archives.

Notes: the review's blocking finding (the two `tempCompressedFilePattern` call sites were untested at `578a0aec09`) is
answered at runtime here: both temp-branch cases store at level 0 on `e1a8f8154b`.

## Reference: the 2.x original, #4234

`repros/pr-4234` (an older generated repro: zstd level 0, zstd default, zip level 0; pins commons-compress 1.27.1 and
zstd-jni 1.5.6-9) was run unchanged from a scratchpad copy, against 2.26.1 and against 2.x HEAD `ea28f5a78b` (jars-2x),
for reference only. On 2.26.1 all three appenders roll and compress (zstd archives ~210 bytes, zip 8393 bytes).
On 2.x HEAD every zstd rollover, including the one with no level set, fails on the rollover thread with
`NoSuchMethodError: ZstdCompressorOutputStream.builder()` (124 times) and leaves 0-byte `.zst` archives; zip is unaffected.
2.x HEAD builds against commons-compress 1.28.0, where that builder appeared, so an application pinned to 1.27.1 loses
its zstd archives after upgrading. Not investigated further.
