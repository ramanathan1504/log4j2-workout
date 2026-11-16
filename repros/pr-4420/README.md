# PR #4420 — `Rfc5424Layout` throws when merging SD elements with the same SD-ID

https://github.com/apache/logging-log4j2/pull/4420

`Rfc5424Layout.addStructuredData` found an existing element by `getId(id)` (which appends the layout's enterprise number) but fetched it with `id.toString()` (which does not), so a `StructuredDataMessage` with id `mdc` merged with a non-empty ThreadContext got `null` and threw `NullPointerException`. Elements created from a message also kept the unmodifiable `MapMessage.getData()` map, so a `StructuredDataCollectionMessage` with two messages of id `Transfer@18060` threw `UnsupportedOperationException` on `union`. Both exceptions are caught by the appender, so the event is silently lost. The fix looks up by the computed SD-ID and copies the fields before merging. The repro logs three events through a `File` appender with `Rfc5424Layout includeMDC="true"` and reads the file back.

## Commands

```bash
cd log4j-pr-4420-repro
./run.sh 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | single SDM (control) | `[control@32473] plain` | `[control@32473] plain` | PASS |
| 2.26.1 | SDM id `mdc` + ThreadContext `user=alice` | `[mdc@32473 request="r1" user="alice"]` | no line; StatusLogger `NullPointerException ... "union" is null` | FAIL |
| 2.26.1 | two SDMs `Transfer@18060` in a collection | `[Transfer@18060 from="A" to="B"]` | no line; StatusLogger `UnsupportedOperationException` | FAIL |
| 2.x HEAD ea28f5a78b | single SDM (control) | `[control@32473] plain` | `[control@32473] plain` | PASS |
| 2.x HEAD ea28f5a78b | SDM id `mdc` + ThreadContext | `[mdc@32473 request="r1" user="alice"]` | `[mdc@32473 request="r1" user="alice"] mdc-merge` | PASS |
| 2.x HEAD ea28f5a78b | two SDMs `Transfer@18060` | `[Transfer@18060 from="A" to="B"]` | `[Transfer@18060 from="A" to="B"] first, second` | PASS |

Lines written: 1 of 3 events on 2.26.1, 3 of 3 on 2.x HEAD.
