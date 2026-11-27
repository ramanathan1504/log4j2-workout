# PR #4365 — nested Log4j streams ignored the outer `ObjectInputFilter`, and plain streams got Log4j's allow-list forced on them

https://github.com/apache/logging-log4j2/pull/4365 (backported to `2.25.x` in #4466)

## Mechanism

Log4j writes some objects (an `ObjectMessage` payload, a log event's message) as a nested byte array and reads them back on a second `ObjectInputStream`. Before the fix that nested stream had two problems:

- When the outer stream was a `FilteredObjectInputStream`, the nested stream was a fresh one with no `ObjectInputFilter`, so a filter set on the outer stream never saw the nested objects.
- When the outer stream was a plain `ObjectInputStream`, Log4j added its own allow-list to the nested stream, overriding the filter the application had chosen. A payload outside the allow-list silently became `null`.

`SerializationUtil.unwrapObject` now copies the outer filter (`copyObjectInputFilter`) onto the nested stream and only uses `FilteredObjectInputStream` when the outer stream is one. `Repro.java` uses a filter that rejects `java.util.ArrayList` (which is on the allow-list) and a filter that allows everything with a `java.net.URI` payload (which is not on the allow-list). It reads `ObjectMessage` and `Log4jLogEvent` through both kinds of stream. JDK 17.

## Commands

```bash
cd log4j-pr-4365-repro
./run.sh 2.25.5 2.25.5
./run.sh ~/apache/vote-2.25.6/staged-jars 2.25.6-staged
```

`staged-jars` holds `log4j-api`, `log4j-core` and `log4j-api-test` 2.25.6 from Nexus staging `orgapachelogging-1330`, checked against their `.sha1`. Outputs: `output/<label>/result.txt`.

## Results

| Case | 2.25.5 | 2.25.6 staged |
|---|---|---|
| `FilteredObjectInputStream` + filter rejecting `ArrayList`, `ObjectMessage(ArrayList)` | **FAIL** accepted `[a, b]` | PASS `InvalidClassException: filter status: REJECTED` |
| same, inside a `Log4jLogEvent` | **FAIL** accepted, message `null` | PASS `filter status: REJECTED` |
| control: `FilteredObjectInputStream`, no filter, `ArrayList` | PASS accepted | PASS accepted |
| plain stream + allow-all filter, `ObjectMessage(URI)` | PASS accepted `URI` | PASS accepted `URI` |
| plain stream + allow-all filter, event with `URI` | PASS accepted `URI` | PASS accepted `URI` |
| plain stream, no filter, event with `URI` | **FAIL** accepted, message `null` | PASS accepted `URI` |
| control: plain stream + filter rejecting `ArrayList`, event | **FAIL** accepted, message `null` | PASS `filter status: REJECTED` |
