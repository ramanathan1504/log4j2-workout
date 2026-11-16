# PR #4184 — `underline` emits SGR 4 and `italic` resolves in `%style` / `%highlight`

https://github.com/apache/logging-log4j2/pull/4184

`AnsiEscape.UNDERLINE` was `"3"` (the italic code), and there was no `ITALIC` constant, so `{italic}` was rejected. The fix sets `UNDERLINE("4")`, adds `ITALIC("3")`, and restores the Jansi names lost in 2.25.0 (`RESET`, `FAINT`, `BLINK_FAST`, `UNDERLINE_DOUBLE`, `*_OFF`, `BG_DEFAULT`, and others).

A `File` appender with `PatternLayout disableAnsi="false"` writes `%style{U}{underline}`, `%style{I}{italic}` and a `%style{B}{bold}` control. The oracle compares the raw escape bytes.

## Commands

```bash
cd log4j-pr-4184-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`2.x-HEAD` is the 2.x build at `ea28f5a78b` (jars in `/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x`, `2.27.0-SNAPSHOT`); `run.sh` resolves the rest with 2.26.1 and swaps every `log4j-*` jar.
Output: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `underline` | `ESC[4mUESC[m` | `ESC[3mUESC[m` (italic) | FAIL |
| 2.26.1 | `italic` | `ESC[3mIESC[m` | `ESC[mIESC[m`; StatusLogger `WARN The style attribute italic is incorrect.` | FAIL |
| 2.26.1 | `bold` (control) | `ESC[1mBESC[m` | `ESC[1mBESC[m` | PASS |
| 2.x HEAD ea28f5a78b | `underline` | `ESC[4mUESC[m` | `ESC[4mUESC[m` | PASS |
| 2.x HEAD ea28f5a78b | `italic` | `ESC[3mIESC[m` | `ESC[3mIESC[m` | PASS |
| 2.x HEAD ea28f5a78b | `bold` (control) | `ESC[1mBESC[m` | `ESC[1mBESC[m` | PASS |
