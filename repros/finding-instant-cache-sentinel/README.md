# Finding: `%d` instant cache — first pre-epoch timestamp on a thread formats as an empty string

Status: not yet filed upstream

## Mechanism

`origin/2.x` at `ea28f5a78b`:

- `log4j-core/src/main/java/org/apache/logging/log4j/core/util/internal/instant/InstantPatternThreadLocalCachedFormatter.java:41-43` seeds each thread's cache entry with `{-1L, new StringBuilder()}`.
- `:94` only formats when `prevEpochInstant != nextEpochInstant`; `:104` appends the cached buffer otherwise. An instant whose cache key is `-1` matches the seed, so the empty buffer is appended and the key is never replaced.
- `InstantPatternFormatter.java:147` wraps millisecond-precision patterns with the millisecond cache (key `getEpochMillisecond()`, hit by `-1` ms); `:151` wraps every coarser pattern (seconds, minutes, hours) with the second cache (key `getEpochSecond()`, hit by any instant in `[-1000, -1]` ms on 2.x HEAD, which floors since #4400).
- `InstantPatternFormatter.java:63`: caching is on when `Constants.ENABLE_THREADLOCALS`; patterns of microsecond or finer precision, `UNIX`, `UNIX_MILLIS` and `log4j2.instantFormatter=legacy` bypass the cache.

The empty output persists for every event at that key on the thread until an event with a different key arrives; every new thread starts with the seed again. Both `PatternLayout` `%d` and `JsonTemplateLayout`'s `timestamp` resolver go through this formatter.

## Commands

```bash
cd log4j-finding-instant-cache-sentinel-repro
J=/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt
./run.sh 2.24.3 2.24.3
./run.sh 2.25.0 2.25.0
./run.sh 2.26.1 2.26.1
./run.sh $J/jars-2x 2.x-HEAD-ea28f5a78b
EXTRA_JVM_ARGS=-Dlog4j2.enableThreadlocals=false ./run.sh $J/jars-2x 2.x-HEAD-ea28f5a78b-threadlocals-off
EXTRA_JVM_ARGS=-Dlog4j2.instantFormatter=legacy ./run.sh $J/jars-2x 2.x-HEAD-ea28f5a78b-legacy-formatter
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh $J/jars-main 3.x-main-2bb673f650
```

`Repro.java` takes each appender's layout from `log4j2.xml` and formats `Log4jLogEvent`s built with `setTimeMillis` / `setInstant` on a fresh thread, comparing the string with the expected UTC value. The last case installs `log4j2.clock=ScriptedClock` and logs through `Logger.info` to a `File` appender, then reads the file. A jars directory resolves third-party dependencies at 2.26.1 (or 3.0.0-beta3, with `log4j-api` 2.24.3) and swaps every `log4j-*` jar. Outputs: `output/<label>/result.txt`.

## Results

`<empty>` is a zero-length timestamp. `0/` and `00:00:00.00/` are the separate pre-epoch arithmetic defect fixed on 2.x by #4400 (unreleased) and still present on `main`.

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.24.3 | `%d{HH:mm:ss.SSS}{UTC}`, events -1,-1,0,-1 ms | `23:59:59.999,23:59:59.999,00:00:00.000,23:59:59.999` | same | PASS |
| 2.24.3 | `%d{HH:mm:ss}{UTC}`, events -1000,-1000,0,-1000 ms | `23:59:59,23:59:59,00:00:00,23:59:59` | `23:59:59,23:59:59,23:59:59,23:59:59` | FAIL: a different, older cache defect (stale second at 0 ms), not the -1 sentinel |
| 2.24.3 | `%d{HH:mm:ss.SSS}{UTC}`, events 0,5 ms | `00:00:00.000,00:00:00.005` | `<empty>,00:00:00.005` | FAIL: the 0-ms analogue in the pre-#3121 formatter |
| 2.24.3 | `%d{yyyy-MM-dd HH:mm}`, `%d{yyyy-MM-dd HH}`, `%d{DEFAULT}`, `%d{ISO8601}`, JTL, custom clock at -1 / -1000 ms | correct timestamp | correct timestamp | PASS |
| 2.24.3 | control `%d{HH:mm:ss.SSS}{UTC}` 1000,2000 ms; `%d{UNIX_MILLIS}` -1 ms | `00:00:01.000,00:00:02.000`; `-1` | same | PASS |
| 2.25.0 | `%d{HH:mm:ss.SSS}{UTC}`, events -1,-1,0,-1 ms | `23:59:59.999,23:59:59.999,00:00:00.000,23:59:59.999` | `<empty>,<empty>,00:00:00.000,00:00:00.00/` | FAIL |
| 2.25.0 | `%d{HH:mm:ss}{UTC}`, events -1000,-1000,0,-1000 ms | `23:59:59,23:59:59,00:00:00,23:59:59` | `<empty>,<empty>,00:00:00,00:00:0/` | FAIL |
| 2.25.0 | `%d{yyyy-MM-dd HH:mm}{UTC}` / `%d{yyyy-MM-dd HH}{UTC}`, -1000 ms | `1969-12-31 23:59` / `1969-12-31 23` | `<empty>` / `<empty>` | FAIL |
| 2.25.0 | custom clock -1,-1,0,-1 ms through `Logger.info` and `File` | `23:59:59.999\|a,23:59:59.999\|b,00:00:00.000\|c,23:59:59.999\|d` | `\|a,\|b,00:00:00.000\|c,00:00:00.00/\|d` | FAIL |
| 2.25.0 | control 1000,2000 ms; `UNIX_MILLIS` -1 ms | `00:00:01.000,00:00:02.000`; `-1` | same | PASS |
| 2.26.1 | `%d{HH:mm:ss.SSS}{UTC}`, events -1,-1,0,-1 ms | `23:59:59.999,23:59:59.999,00:00:00.000,23:59:59.999` | `<empty>,<empty>,00:00:00.000,00:00:00.00/` | FAIL |
| 2.26.1 | same layout, -1 ms on a second fresh thread | `23:59:59.999` | `<empty>` | FAIL |
| 2.26.1 | `%d{DEFAULT}{UTC}` / `%d{ISO8601}{UTC}`, -1 ms | `1969-12-31 23:59:59,999` / `1969-12-31T23:59:59,999` | `<empty>` / `<empty>` | FAIL |
| 2.26.1 | `%d{HH:mm:ss}{UTC}`, events -1000,-1000,0,-1000 ms | `23:59:59,23:59:59,00:00:00,23:59:59` | `<empty>,<empty>,00:00:00,00:00:0/` | FAIL |
| 2.26.1 | `%d{HH:mm:ss}{UTC}`, `setInstant(epochSecond=-1)` | `23:59:59` | `<empty>` | FAIL |
| 2.26.1 | `%d{yyyy-MM-dd HH:mm}{UTC}` / `%d{yyyy-MM-dd HH}{UTC}`, -1000 ms | `1969-12-31 23:59` / `1969-12-31 23` | `<empty>` / `<empty>` | FAIL |
| 2.26.1 | JsonTemplateLayout `timestamp` `HH:mm:ss.SSS` UTC, -1 ms | `{"t":"23:59:59.999"}` | `{"t":""}` | FAIL |
| 2.26.1 | custom clock -1,-1,0,-1 ms through `Logger.info` and `File` | `23:59:59.999\|a,23:59:59.999\|b,00:00:00.000\|c,23:59:59.999\|d` | `\|a,\|b,00:00:00.000\|c,00:00:00.00/\|d` | FAIL |
| 2.26.1 | control 1000,2000 ms; `UNIX_MILLIS` -1 ms | `00:00:01.000,00:00:02.000`; `-1` | same | PASS |
| 2.x HEAD ea28f5a78b | `%d{HH:mm:ss.SSS}{UTC}`, events -1,-1,0,-1 ms | `23:59:59.999,23:59:59.999,00:00:00.000,23:59:59.999` | `<empty>,<empty>,00:00:00.000,23:59:59.999` | FAIL |
| 2.x HEAD ea28f5a78b | same layout, -1 ms on a second fresh thread | `23:59:59.999` | `<empty>` | FAIL |
| 2.x HEAD ea28f5a78b | `%d{DEFAULT}{UTC}` / `%d{ISO8601}{UTC}`, -1 ms | `1969-12-31 23:59:59,999` / `1969-12-31T23:59:59,999` | `<empty>` / `<empty>` | FAIL |
| 2.x HEAD ea28f5a78b | `%d{HH:mm:ss}{UTC}`, events -1000,-1000,0,-1000 ms | `23:59:59,23:59:59,00:00:00,23:59:59` | `<empty>,<empty>,00:00:00,23:59:59` | FAIL |
| 2.x HEAD ea28f5a78b | `%d{HH:mm:ss}{UTC}`, -500 ms | `23:59:59` | `<empty>` | FAIL |
| 2.x HEAD ea28f5a78b | `%d{HH:mm:ss}{UTC}`, `setInstant(epochSecond=-1)` | `23:59:59` | `<empty>` | FAIL |
| 2.x HEAD ea28f5a78b | `%d{yyyy-MM-dd HH:mm}{UTC}` / `%d{yyyy-MM-dd HH}{UTC}`, -1000 ms | `1969-12-31 23:59` / `1969-12-31 23` | `<empty>` / `<empty>` | FAIL |
| 2.x HEAD ea28f5a78b | JsonTemplateLayout `timestamp` `HH:mm:ss.SSS` UTC, -1 ms | `{"t":"23:59:59.999"}` | `{"t":""}` | FAIL |
| 2.x HEAD ea28f5a78b | custom clock -1,-1,0,-1 ms through `Logger.info` and `File` | `23:59:59.999\|a,23:59:59.999\|b,00:00:00.000\|c,23:59:59.999\|d` | `\|a,\|b,00:00:00.000\|c,23:59:59.999\|d` | FAIL |
| 2.x HEAD ea28f5a78b | `%d{HH:mm:ss.SSS}{UTC}`, 1000 then -1000 ms (cache populated first) | `00:00:01.000,23:59:59.000` | same | PASS |
| 2.x HEAD ea28f5a78b | `%d{HH:mm:ss.SSSSSS}{UTC}` (not cached), -1000 ms | `23:59:59.000000` | same | PASS |
| 2.x HEAD ea28f5a78b | control 1000,2000 ms; `UNIX_MILLIS` -1 ms | `00:00:01.000,00:00:02.000`; `-1` | same | PASS |
| 2.x HEAD, `log4j2.enableThreadlocals=false` | every `%d`, JTL and clock case above | correct timestamp | correct timestamp (17 PASS; the one FAIL is the Rfc5424Layout sweep row) | PASS |
| 2.x HEAD, `log4j2.instantFormatter=legacy` | every `%d`, JTL and clock case above | correct timestamp | correct timestamp (17 PASS; the one FAIL is the Rfc5424Layout sweep row) | PASS |
| 3.0.0-beta3 | `%d{HH:mm:ss.SSS}{UTC}`, events -1,-1,0,-1 ms | `23:59:59.999,...` | `<empty>,<empty>,00:00:00.000,00:00:59.999` | FAIL |
| 3.0.0-beta3 | `%d{DEFAULT}`, `%d{HH:mm:ss}` -1000, `setInstant`, `yyyy-MM-dd HH:mm`, `yyyy-MM-dd HH`, JTL | correct timestamp | `<empty>` (JTL `{"t":""}`) | FAIL |
| 3.x main 2bb673f650 | `%d{HH:mm:ss.SSS}{UTC}`, events -1,-1,0,-1 ms | `23:59:59.999,...` | `<empty>,<empty>,00:00:00.000,00:00:00.00/` | FAIL |
| 3.x main 2bb673f650 | same layout, -1 ms on a second fresh thread | `23:59:59.999` | `00:00:00.00/` (not empty: the cache entry is shared across threads on `main`) | FAIL |
| 3.x main 2bb673f650 | `%d{DEFAULT}`, `%d{ISO8601}`, `%d{HH:mm:ss}` -1000, `setInstant`, `yyyy-MM-dd HH:mm`, `yyyy-MM-dd HH`, JTL | correct timestamp | `<empty>` (JTL `{"t":""}`) | FAIL |
| 3.x main 2bb673f650 | control 1000,2000 ms; `UNIX_MILLIS` -1 ms | `00:00:01.000,00:00:02.000`; `-1` | same | PASS |

The custom-clock case is `SKIP` on 3.x: `ClockFactory` on `main` accepts only the built-in clock names.

Answers to the brief:

- Millisecond-precision patterns (`HH:mm:ss.SSS`, `DEFAULT`, `ISO8601`) hit the millisecond cache at exactly `-1` ms. Second, minute and hour patterns all hit the second cache at `-1000` ms on every affected version, and at any instant in `[-1000, -1]` ms on 2.x HEAD (`-500` ms is `<empty>` there; on 2.26.1 it is `23:59:00`, the #4399 defect). There is no separate minute or hour sentinel: `InstantPatternDynamicFormatter`'s minute prefix cache is seeded from `Instant.now()` at construction.
- It is not only the first event: every event at the sentinel key on that thread is empty (`<empty>,<empty>`) until one with a different key arrives; after that the same instant formats (2.x HEAD, fourth event). Each new thread starts empty again on 2.25.1 and later; on 2.25.0 and `main` the entry is shared, so only the formatter's first key is affected.

## Since when

- Introduced by `eefca5920c` "Rewrite date & time formatting (#3121)", 2024-10-31; `git log -S'{-1L, new StringBuilder()}'` returns only that commit.
- First release: `rel/2.25.0` (lowest `rel/2.*` from `git tag --contains eefca5920c`). Affected: 2.25.0, 2.26.1, 2.x HEAD. Not affected: 2.24.3.
- `6cd74a88ba` "Disable sharing of formatted timestamps between threads (#3802)", first in 2.26.0, changed the cache to per-thread only and kept the seed.

## 3.x

Affected. `origin/main` `InstantPatternThreadLocalCachedFormatter.java:41` and `:44` seed both the shared `lastEpochInstantAndBuffer` and each thread's entry with `{-1L, new StringBuilder()}`; `:96` appends the shared buffer on a key match. This is the pre-#3802 shape (only `025b4bd404`, the #3150 port, touches the file on `main`). Reproduced on 3.0.0-beta3 and `main` 2bb673f650.

## Defect-class sweep

`git grep` on `origin/2.x` for `ThreadLocal` caches and `previous*/last*` time fields with `-1` or `0` seeds:

| Site | Seed | Verdict |
|---|---|---|
| `core/util/internal/instant/InstantPatternThreadLocalCachedFormatter.java:42` | `-1L`, empty buffer | affected (this finding) |
| `layout-template-json/.../resolver/TimestampResolver.java:214` (`pattern` resolver) | same formatter via `InstantPatternFormatter.newBuilder()` | affected, run: `{"t":""}` |
| `core/layout/Rfc5424Layout.java:129-130`, `:497-498` | `lastTimestamp = -1`, `timestamppStr = null`, instance field, not `ThreadLocal` | affected, run: timestamp field is `null` at -1 ms on every version run, 2.24.3 included; older and separate, worth its own issue |
| `core/util/internal/instant/InstantPatternDynamicFormatter.java:60` (minute prefix cache) | seeded from `Instant.now()` | not affected |
| `core/util/internal/instant/InstantPatternLegacyFormatter.java:92` | `ThreadLocal` holds a char buffer or `Calendar`, no key | not affected, run with `log4j2.instantFormatter=legacy`: 17 PASS |
| `core/util/datetime/FixedDateFormat.java:419-420` (`midnightToday`/`midnightTomorrow` = 0) | `now >= midnightTomorrow \|\| now < midnightToday` is true for any first value | not affected |
| `core/pattern/CachedDateFormat.java:156` | `Long.MIN_VALUE`, and no caller on `origin/2.x` | not affected |
| `log4j-1.2-api/.../helpers/AbsoluteTimeDateFormat.java:53`, `ISO8601DateFormat.java:39` | `0`, guarded by `buffer[0] == 0` | not affected |
| `core/layout/GelfLayout.java:814` | scratch `StringBuilder`, no key | not affected |
| 2.24.3 `DatePatternConverter.PatternFormatter` / `FixedFormatter` (`previousTime` = 0) | removed by #3121 | affected on 2.24.3 only, run: `%d{HH:mm:ss.SSS}` first event at 0 ms `<empty>`; `%d{EEE HH:mm:ss.SSS}` at 0 ms repeats the previous timestamp (`Thu 00:00:05.000`). PASS on 2.25.0 and later |

## Class

`log4j-bug`. Reproduced on `origin/2.x` and `origin/main`; a timestamp formatted as an empty string contradicts the `%d` contract, and disabling the cache (`log4j2.enableThreadlocals=false`) or using the legacy formatter gives the correct value for the same input.

Fix location: `log4j-core`, `InstantPatternThreadLocalCachedFormatter.createEpochInstantAndBuffer()`, on both branches; a seed no instant can produce, such as `Long.MIN_VALUE`, closes it.
