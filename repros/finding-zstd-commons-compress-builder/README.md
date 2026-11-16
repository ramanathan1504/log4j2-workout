# Finding: ZstdCompressAction — `.zst` rollover writes a 0-byte archive on commons-compress older than 1.28.0

Status: not yet filed upstream

Unreleased regression on `origin/2.x` (`ea28f5a78b`). No release has it.

## Mechanism

On `origin/2.x`, `.zst` no longer goes through `CommonsCompressAction` (which uses
`CompressorStreamFactory`, present in every commons-compress version). It goes
through a new `ZstdCompressAction` that calls API added in commons-compress 1.28.0:

- `log4j-core/src/main/java/org/apache/logging/log4j/core/appender/rolling/action/internal/ZstdCompressAction.java:80`
  `ZstdCompressorOutputStream.builder()`: `NoSuchMethodError` on 1.27.1 and 1.26.2.
- `ZstdCompressAction.java:56`, reached from `:79` whenever `compressionLevel` is set:
  `ZstdConstants`: `NoClassDefFoundError` on 1.27.1 and 1.26.2.
- `CompressActionSupport.java:96` opens the destination `FileOutputStream` before
  `:97` creates the compressor, so the failure leaves a 0-byte `app-N.log.zst`.
  `:105` deletes the source only after the copy succeeds, so the renamed
  `app-N.log` stays behind uncompressed.
- `AbstractAction.java:66-69` wraps the `Error` and reports it at `WARN` on the
  rollover thread. The default StatusLogger level is `ERROR`, so by default
  nothing is printed.
- `FileExtension.java:127` catches `LinkageError` at configuration time, so the
  configuration loads and the failure is deferred to every rollover.

commons-compress is `<optional>true</optional>` in `log4j-core/pom.xml:154-158`.
`log4j-parent/pom.xml:73` manages `1.28.0` on `origin/2.x` (bumped by `dff7bfa261`,
#4206); `rel/2.26.1` managed `1.27.1`. The manual
(`rolling-file.adoc:999-1009`, `partials/features/compression.adoc`) shows the
dependency with `{commons-compress-version}`, which renders the build's own
version (`pom.xml:348`, `1.28.0`); it states no minimum. Only the OSGi manifest
of the HEAD jar says it: `Import-Package ... zstandard;version="[1.28,2)"`
(2.26.1: `[1.27,2)`).

## Commands

```bash
cd log4j-finding-zstd-commons-compress-builder-repro
J2=<scratchpad>/wt/jars-2x
J3=<scratchpad>/wt/jars-main
for cc in 1.26.2 1.27.1 1.28.0; do
  ./run.sh 2.26.1 $cc 2.26.1-cc$cc
  ./run.sh $J2   $cc 2.x-HEAD-ea28f5a78b-cc$cc
done
STATUS=ERROR ./run.sh $J2 1.27.1 2.x-HEAD-ea28f5a78b-cc1.27.1-status-ERROR
for cc in 1.27.1 1.28.0; do CONFIG=log4j3-algorithm-names.xml ./run.sh $J3 $cc 3.x-main-2bb673f650-cc$cc; done
CONFIG=log4j3-algorithm-names.xml ./run.sh 3.0.0-beta3 1.27.1 3.0.0-beta3-cc1.27.1
./run.sh $J3 1.27.1 3.x-main-2bb673f650-cc1.27.1-2x-extensions
```

`run.sh <log4j version | jars dir> <commons-compress version> [label]`. Each
appender writes 150 numbered lines in three segments with two manual rollovers
(`%i`, `max=5`), zstd-jni 1.5.7-24 and xz 1.12 on the classpath. The oracle
decompresses every file in the directory and counts the numbered lines it gets
back; a case passes only with 150/150 recovered, 2/2 non-empty archives that
decode, and no uncompressed `app-N.log` left over. `gz` uses only the JRE and is
the control.

## Results

| Version | commons-compress | Case | Expected | Observed | Verdict |
|---|---|---|---|---|---|
| 2.26.1 | 1.26.2, 1.27.1, 1.28.0 | `.zst` | 2 archives, 150/150 | `app-1.log.zst=140B app-2.log.zst=140B`, 150/150 | PASS |
| 2.26.1 | 1.26.2, 1.27.1, 1.28.0 | `.zst` `compressionLevel=5` | 2 archives, 150/150 | 146B each, 150/150 | PASS |
| 2.26.1 | all three | gz, bz2, deflate, xz | 2 archives each | 2 archives each, 150/150 | PASS |
| 2.x HEAD | 1.26.2 | `.zst` | 2 archives | `app-1.log.zst=0B app-2.log.zst=0B`, plus `app-1.log` and `app-2.log` uncompressed; 150/150 lines still on disk | FAIL |
| 2.x HEAD | 1.26.2 | `.zst` `compressionLevel=5` | 2 archives | same, via `NoClassDefFoundError: ZstdConstants` | FAIL |
| 2.x HEAD | 1.27.1 | `.zst` | 2 archives | `0B` archives, uncompressed leftovers, 150/150 on disk | FAIL |
| 2.x HEAD | 1.27.1 | `.zst` `compressionLevel=5` | 2 archives | same, `ZstdConstants` | FAIL |
| 2.x HEAD | 1.28.0 | `.zst`, `.zst` level 5 | 2 archives | 140B / 146B, 150/150 | PASS |
| 2.x HEAD | 1.26.2, 1.27.1, 1.28.0 | gz (control), bz2, deflate, xz | 2 archives each | 2 archives each, 150/150 | PASS |
| 2.x HEAD, StatusLogger WARN | 1.27.1 | stderr | — | 4 `WARN Exception reported by action ... RollingFileManager$AsyncAction` with the `NoSuchMethodError` / `NoClassDefFoundError` cause | — |
| 2.x HEAD, StatusLogger default (ERROR) | 1.27.1 | stderr | — | 0 bytes: nothing reported | — |
| 3.x main `2bb673f650` | 1.27.1, 1.28.0 | `.zstd`, `.bzip2`, `.xz`, gz | 2 archives each | 140B / 122B / 160B / 135B, 150/150 | PASS |
| 3.0.0-beta3 | 1.27.1 | `.zstd`, `.bzip2`, `.xz`, gz | 2 archives each | same | PASS |

Lines lost: **0** in every 2.x HEAD cell. The content survives as the
uncompressed `app-N.log` next to the empty `app-N.log.zst`, and the `%i` purge
renamed both together on the second rollover. The claim that the source is
deleted did not reproduce. Retention at `max` with the leftovers was not run.

## Since when

`git log -S 'ZstdCompressorOutputStream.builder()' origin/2.x` returns one commit:
`fa95dbb532` (2026-10-30), *Add ZstdCompressAction to support configurable
Zstandard compression levels* (#4234). `git tag --contains fa95dbb532` is empty.
`rel/2.26.1` maps `.zst` to `new CommonsCompressAction("zstd", ...)`, which works
on 1.26.2 through 1.28.0 above. No released version is affected.

## 3.x

Not affected. `origin/main` has no `ZstdCompressAction`; `log4j-compress`
(`CommonsCompressActionFactoryProvider`) goes through
`CompressorStreamFactory.findAvailableCompressorOutputStreamProviders()`, and
`.zstd` compresses on 1.27.1 and 1.28.0.

Side observation, not this finding: on 3.x the extension is the commons-compress
algorithm name (`.zstd`, `.bzip2`). A 2.x-style `.zst` or `.bz2` filePattern
(output `3.x-main-2bb673f650-cc1.27.1-2x-extensions`) writes the log uncompressed
under the compressed name (`app-2.log.zst` starts `zst-line-0051`), with nothing
on StatusLogger, while the 3.x manual's note still says "`.xz` and `.zst`
extensions require additional dependencies".

## Defect-class sweep: commons-compress API newer than 1.26 on `origin/2.x`

`git grep 'org.apache.commons.compress' origin/2.x -- '*/src/main/*'`:

| Call site | API | Verdict |
|---|---|---|
| `ZstdCompressAction.java:80` `ZstdCompressorOutputStream.builder()` | 1.28.0 | affected, ran |
| `ZstdCompressAction.java:56` `ZstdConstants.ZSTD_CLEVEL_MIN/MAX` | 1.28.0 | affected, ran (`compressionLevel` set) |
| `ZstdCompressAction.java:53,76` `ZstdUtils.isZstdCompressionAvailable()` | old | not affected |
| `Bzip2CompressAction.java:37` `new BZip2CompressorOutputStream(out, int)`, `MIN/MAX_BLOCKSIZE` | old | not affected, ran 1.26.2/1.27.1 |
| `DeflateCompressAction.java:48-53` `DeflateParameters`, `DeflateCompressorOutputStream` | old | not affected, ran |
| `CommonsCompressAction.java:104` `CompressorStreamFactory` (`xz`, `pack200`) | old | xz not affected, ran; pack200 untested |
| `FileExtension.java:22` `BZip2CompressorOutputStream` constants | old | not affected |
| `GzCompressAction`, `ZipCompressAction` | JRE only | not affected, ran (gz) |

## Class

`log4j-bug`. Reproduced on `origin/2.x` with commons-compress 1.26.2 and 1.27.1:
an optional dependency at a version 2.26.1 itself was built against stops `.zst`
compression with no output at the default status level. The manual states no
minimum commons-compress version.
