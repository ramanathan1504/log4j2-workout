# PR #4461 — quadratic time in `%enc{…}{HTML}` escaping

https://github.com/apache/logging-log4j2/pull/4461

The garbage-free HTML escaper in `EncodingPatternConverter` pads the buffer in pass 1 and writes backwards in pass 2. Pass 2 wrote each escape with `StringBuilder.replace`, which moves the whole tail even for an equal-length replacement, so cost is (escapes × buffer length). The fix writes the escape with `setCharAt` in reverse. The repro formats `%enc{%m}{HTML}` through `PatternLayout.toSerializable` on 16 KiB and 64 KiB of `"` (best of 2 after 2 warm-up rounds) and compares the growth ratio; a mixed-input case checks the escaped output against a chained `String.replace` reference.

## Commands

```bash
cd log4j-pr-4461-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

The second run resolves third-party dependencies at 2.26.1 and replaces every `log4j-*` jar with the `2.27.0-SNAPSHOT` jar built from `2.x` HEAD `ea28f5a78b`, which contains this change.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | output equals reference (7600 chars) | equal | equal | PASS |
| 2.26.1 | 16 KiB → 64 KiB time ratio | < 8x (linear ~4x) | 35 ms → 886 ms, 24.9x | FAIL |
| 2.x HEAD ea28f5a78b | output equals reference | equal | equal | PASS |
| 2.x HEAD ea28f5a78b | 16 KiB → 64 KiB time ratio | < 8x | 0 ms → 2 ms, 3.9x | PASS |

Notes: timings are wall-clock on a shared laptop with other JVMs running; the ratio, not the absolute ms, is the oracle. A first attempt at 64/256 KiB did not finish on 2.26.1 within 2 minutes.
