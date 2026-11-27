# finding-boot46468-rolling

Spring Boot [#46468](https://github.com/spring-projects/spring-boot/issues/46468): Log4j2 rolling-policy properties. Checks the `max-history` mapping that ships in Boot 4.1.0, 4.2.0 and `main` (same `log4j2-file.xml`), and the change Stéphane Nicoll stashed on `snicoll:gh-46468` (`283b523`), against Logback in the same Boot.

## Proof on real Spring Boot (primary)

```bash
repros/finding-boot46468-rolling/boot-app/run.sh
```

- Spring Boot 4.2.0 from Maven Central, with Log4j 2.26.1 (`spring-boot-starter-log4j2`) or Logback 1.6.6 (`spring-boot-starter`).
- Boot's own `log4j2-file.xml` is used for L1. `configs/boot-4.2.0-log4j2-file.xml` is the copy taken from the 4.2.0 jar, and it is identical to Boot `main`.
- `configs/stashed-283b523.xml` is byte-identical to `log4j2-file.xml` at `snicoll/spring-boot@283b523`. Boot 4.2.0 does not set the new `LOG4J2_ROLLINGPOLICY_*` properties, so the run passes them as `-D`. Stéphane's branch passes `DataSize.toBytes()`, a plain byte count.
- `configs/fixed-cap.xml` is the stashed XML with `basePath="${sys:ARCHIVE_DIR}" maxDepth="1"` and `glob="${sys:ARCHIVE_GLOB}"`.
- `configs/xml-only-parent.xml` uses `basePath="${sys:LOG_FILE}/.."`.
- Each case first writes earlier-day archives (`<file>.<today-6..4>.{1,2,3}.gz`, 400 B each, with their mtime set to that day) and the unrelated `.gz` files shown. It then logs for 4 s with `max-file-size=1KB` and `max-history=2`, closes the context, and lists the files.
- Output: `output/boot-4.2.0-run.txt`.

### Results (2026-11-26)

| Case | Setup | Observed | Verdict |
|---|---|---|---|
| L1 | Log4j2, Boot's XML | earlier days kept, all 9 files; today 2 | `max-history` is per day; old days are never deleted |
| B1 | Logback, same properties | earlier days deleted; today 300+ (no per-day limit) | Logback `maxHistory` counts days |
| L2a | stashed XML, `logging.file.name=logs/app.log` | `LOG_PATH` unset; `./other-1.gz` and `./other-2.gz` deleted; archives in `logs/` untouched (3826 B over a 2048 cap) | wrong folder |
| L2b | stashed XML, `logging.file.name=app.log` | archives capped (1826 B), and the unrelated `./other-1.gz` also deleted | right folder, wrong files |
| L2c | stashed XML, `logging.file.path=logs` | `LOG_PATH=logs`; archives capped (1828 B), and another app's `logs/other-app.log.1.gz` also deleted | right folder, wrong files |
| L3 | fixed: `ARCHIVE_DIR=logs`, `ARCHIVE_GLOB=app.log.*.gz` | archives capped (1825 B), oldest deleted first; `./other-1.gz` and `logs/other-app.log.1.gz` kept | works with existing Log4j features |
| L4 | `basePath="${sys:LOG_FILE}/.."` | `FileSystemException: logs/app.log/..: Not a directory` on all 294 rollovers; nothing deleted | the folder cannot be derived in XML |
| L5 | `OnStartupTriggeringPolicy` + fixed cap; previous `app.log` from yesterday | `app.log` rolled to `app.log.<yesterday>.1.gz` at startup; cap ran (2047 B) | rolls, then cleans |
| L6 | as L5, with no previous `app.log` | nothing rolled; earlier days kept (3600 B over cap) | no rollover, no cleanup |
| L7 | as L5; previous `app.log` from 1 h ago | `app.log` rolled to `app.log.<today>.1.gz`; cap ran | rolls even within the same day |
| B5 | Logback `clean-history-on-start`; previous `app.log` from yesterday | old archives deleted; `app.log` rolled, because the day changed (not because of the flag) | — |
| B7 | Logback `clean-history-on-start`; previous `app.log` from 1 h ago | old archives deleted; `app.log` kept and appended to (first line still the previous run's) | cleans without rolling |
| B8 | as B7 with the flag off | old archives kept | the cleanup in B7 is the flag |

## Strategies and date-in-folder (real Boot)

```bash
repros/finding-boot46468-rolling/boot-app/run-strategies.sh
```

Output: `output/boot-4.2.0-strategies.txt`. A per-second pattern `logs/app.log.%d{yyyy-MM-dd-HH-mm-ss}.%i.gz` stands in for days.

| Case | Setup | Observed | Verdict |
|---|---|---|---|
| T1 | `strategy=time`, `max-history=2`, Boot XML | 6 periods, 1 archive each, all kept | `max-history` never deletes with `time` |
| T2 | `strategy=size-and-time`, 1KB | 6 periods, 2 archives each, all kept | per-period limit, as L1 |
| T3 | `strategy=cron` `* * * * * ?` | 6 periods, 1 each, all kept | never deletes with `cron` |
| TB | Logback, same pattern, `max-history=2` | 2 periods kept | Logback counts periods |
| T4 | `time` + fixed cap 5000 | 2 archives, 3613 B | cap works with `time` |
| T5 | `cron` + fixed cap 5000 | 2 archives, 3449 B | cap works with `cron` |
| T6 / T7 | `time` / `cron` + `OnStartupTriggeringPolicy` + cap, previous `app.log` from 1 h ago | rolled at startup, cap ran (2047 B) | startup rollover is the same for every strategy |
| D1 | pattern `logs/archive/%d{yyyy-MM-dd-HH-mm}/app.%d{yyyy-MM-dd-HH-mm-ss}.%i.log.gz`, Boot XML | seeded date folders kept; 2 per second | same per-period limit |
| D2 | same pattern, stashed XML, `file.path=logs` | `logs/other-app.log.1.gz` deleted; nested archives untouched (4731 B over a 2048 cap) | stashed cap misses nested archives |
| D3 | fixed cap, `glob=app.*.log.gz`, `maxDepth=1` | nothing deleted | a flat glob misses date folders |
| D4 | fixed cap, `glob=*/app.*.log.gz`, `maxDepth=2` | app archives capped (1927 B), oldest first; `other-app` kept; emptied date folders left behind | works when the glob covers the folder level |

A cap smaller than one archive deletes every archive, including the newest one. An earlier T4/T5 run used a 1024 B cap against ~1.8 KB archives and ended with no archives.

## Stand-in on plain Log4j (secondary)

`log4j-boot46468-repro/run.sh 2.26.1` uses plain Log4j with no Boot: a time-based plus a 2 KB size-based policy, per-second dates. It reaches the same results for cases a–d. See `output/run-2.26.1.txt`.

## Mechanism

- `DefaultRolloverStrategy max` limits only `%i`. `AbstractRolloverStrategy.getEligibleFiles` formats the pattern with the current period's date (`PatternProcessor.formatFileName(subst, buf, false, NAN)`), so only archives of the current period are counted.
- Boot sets `LOG_PATH` only from `logging.file.path` (`LogFile.java:78-80`), so `${sys:LOG_PATH:-.}` is the working directory when only `logging.file.name` is set.
- `IfFileName` matches the path relative to `basePath`. In a glob, `*` does not cross directories.
- `Delete` runs only as part of a rollover (`RollingFileManager.java:791`). `OnStartupTriggeringPolicy` rolls when the file predates JVM start and is at least `minSize` (default 1) bytes (`OnStartupTriggeringPolicy.java:87`).

Review note: `~/knowledge/Projects/log4j/issues/20261125-log4j-boot46468-log4j2-rolling-policy-review.md`.
