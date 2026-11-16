# PR #4154 — [main] Reopen log file when rollover is unsuccessful (#3226 port)

https://github.com/apache/logging-log4j2/pull/4154

`RollingFileManager.rollover(RolloverStrategy)` writes the footer and closes the output stream before running the
strategy's synchronous action (the rename of `app.log` into the archive). On 3.0.0-beta3, when that action failed it
returned `false`, and the caller only reopens the file (`createFileAfterRollover()`) and resets `size` on `true`. The
stream stayed closed and the size counter stayed over the limit, so every following event re-triggered a rollover and
events were lost. The port returns "output stream closed" instead of "action succeeded", so a failed rename still
reopens `app.log` and logging continues in it.

## Command

```bash
cd log4j-pr-4154-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
```

`log4j2.xml`: `RollingFile` `app.log` → `archive/app-%i.log`, `SizeBasedTriggeringPolicy size="1 KB"`.
`Repro.java` makes `archive/` read-only (`r-xr-xr-x`), so every rename/copy fails with `AccessDeniedException`, logs
200 numbered 46-byte events, restores the permission, and counts the events in `app.log`. `log4j-api` 2.24.3,
`-Dlog4j.configuration.location`, StatusLogger at WARN; the ERROR lines are summarised in `result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | archive blocked (control) | 0 archived files | 0 | PASS |
| 3.0.0-beta3 | events kept after failed rollover | 200/200 | 23/200 (last `seq=0022`); 177 copy errors, 2 `Unable to write to stream` | FAIL |
| main HEAD 2bb673f650 | archive blocked (control) | 0 archived files | 0 | PASS |
| main HEAD 2bb673f650 | events kept after failed rollover | 200/200 | 200/200 (last `seq=0199`); 8 copy errors, one per 1 KB | PASS |

Notes: on 3.0.0-beta3 only 2 of the 177 lost events produce a write ERROR; the rest disappear with no message of
their own. On main HEAD `app.log` keeps growing past the 1 KB limit while the archive is unwritable, which is the intended
behaviour.
