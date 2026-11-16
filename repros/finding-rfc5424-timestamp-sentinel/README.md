# Finding: `Rfc5424Layout` prints the literal `null` as TIMESTAMP for an event at -1 ms

Status: not yet filed upstream

## Mechanism

`origin/2.x` at `ea28f5a78b`, `log4j-core/src/main/java/org/apache/logging/log4j/core/layout/Rfc5424Layout.java`:

- `:129-130` seed the per-layout cache with `lastTimestamp = -1` and `timestamppStr` unset (`null`). Instance fields, not `ThreadLocal`, guarded by `synchronized (this)`.
- `:497-498` (`computeTimeStampString`) return `timestamppStr` whenever `now == lastTimestamp`. An event at exactly -1 ms matches the seed, so `null` is returned and `appendTimestamp` (`:320`) appends it through `StringBuilder.append(String)`, which writes the four characters `null`.
- The cache is only replaced on a miss (`:536-538`), so every -1 ms event prints `null` until an event with another time reaches the same layout instance; after that, -1 ms formats correctly.
- Same code on `origin/main` (`:128`, `:481-482`). Present since `27b1fee717` "Add RFC 5424 Syslog and BSD Syslog layouts" (2011-05-30), so every 2.x release.

Nothing in Log4j itself produces a -1 ms timestamp (`git grep` on `origin/2.x`). It needs a custom `Clock`, an application-built event, or a bridged/deserialized event carrying -1.

## Commands

```bash
cd log4j-finding-rfc5424-timestamp-sentinel-repro
J=/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt
./run.sh 2.17.2 2.17.2
./run.sh 2.24.3 2.24.3
./run.sh 2.26.1 2.26.1
./run.sh $J/jars-2x 2.x-HEAD-ea28f5a78b
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh $J/jars-main 3.x-main-2bb673f650
```

`Repro.java` takes each `Rfc5424Layout` from `log4j2.xml` and formats `Log4jLogEvent`s built with `setTimeMillis`, printing field 2 of the record (TIMESTAMP). `1` ms is used instead of `0` because `Log4jLogEvent.Builder` replaces `timeMillis == 0` with the clock time. The last case installs `log4j2.clock=ScriptedClock` and logs through `Logger.info` to a `File` appender with `Rfc5424Layout includeNL="true"`, then reads the file. Outputs: `output/<label>/result.txt`. Run with `-Duser.timezone=UTC`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.17.2, 2.24.3, 2.26.1, 2.x HEAD ea28f5a78b, 3.0.0-beta3, 3.x main 2bb673f650 | events -1,-1,1,-1 ms on one layout | `1969-12-31T23:59:59.999Z,1969-12-31T23:59:59.999Z,1970-01-01T00:00:00.001Z,1969-12-31T23:59:59.999Z` | `null,null,1970-01-01T00:00:00.001Z,1969-12-31T23:59:59.999Z` | FAIL |
| all six | first event -1 ms on a second layout instance | `1969-12-31T23:59:59.999Z` | `null` | FAIL |
| all six | first event -2 ms | `1969-12-31T23:59:59.998Z` | same | PASS |
| all six | 1000 ms then -1 ms | `1970-01-01T00:00:01.000Z,1969-12-31T23:59:59.999Z` | same | PASS |
| all six | control 1000, 2000 ms | `1970-01-01T00:00:01.000Z,1970-01-01T00:00:02.000Z` | same | PASS |
| 2.24.3, 2.26.1, 2.x HEAD | custom clock -1,-1,1,-1 ms through `Logger.info` and `File` | as row 1 | `null,null,1970-01-01T00:00:00.001Z,1969-12-31T23:59:59.999Z` | FAIL |
| 2.17.2 | same | as row 1 | `null` (all four records on one line: `includeNL` is not an attribute on 2.17.2) | FAIL |
| 3.0.0-beta3, 3.x main | same | — | SKIP: `ClockFactory` on `main` accepts only the built-in clock names | SKIP |
| all six | sweep: year 999 (`DATE-FULLYEAR = 4DIGIT`) | `0999-06-15T00:00:00.000Z` | `999-06-10T00:00:00.000Z` | FAIL: separate, see below |

The year-999 row is a sibling in the same method, not the sentinel: `Integer.toString(cal.get(Calendar.YEAR))` does not pad to four digits, and `GregorianCalendar` switches to the Julian calendar before 1582-10-15, so the date is 5 days off from RFC 3339's proleptic Gregorian. Years above 9999 and BC years (`Calendar.YEAR` is era-relative) are also outside the grammar. Recorded, not part of this finding.

## RFC 5424

`TIMESTAMP = NILVALUE / FULL-DATE "T" FULL-TIME`, `DATE-FULLYEAR = 4DIGIT`; 1969 is representable. `NILVALUE` is for an application "incapable of obtaining system time" (6.2.3), and Appendix A.6 says it "should only be used when it is actually impossible to obtain time information". The time is known here, so the correct output is `1969-12-31T23:59:59.999Z`; `null` matches neither alternative.

## Fix

`log4j-core`, `Rfc5424Layout.computeTimeStampString`: seed `lastTimestamp` with `Long.MIN_VALUE` (or test `timestamppStr != null`), on both `2.x` and `main`. Same shape and same fix as `finding-instant-cache-sentinel`.
