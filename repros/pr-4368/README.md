# PR #4368 — `SerializedLayout` writes a truncated record when an event fails to serialize

https://github.com/apache/logging-log4j2/pull/4368

`SerializedLayout.toByteArray` writes the event with an `ObjectOutputStream` into a `ByteArrayOutputStream`. When
`writeObject` throws part-way (a non-serializable field reachable from the event), the error was logged but the
bytes written so far were still returned and appended to the output. A reader using `ObjectInputStream` then fails
at that record, and every later event in the stream is unreadable. The fix resets the buffer in the `catch`, so the
bad event is dropped and the stream stays readable.

## Commands

```bash
cd log4j-pr-4368-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`log4j2.xml`: `File` appender with `<SerializedLayout/>`. `Repro.java` logs `event-1`, then `event-2` with a custom
`Marker` holding a `java.lang.Object` field (not serializable), then `event-3`, and reads the file back with
`ObjectInputStream`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: event-1 readable | yes | yes | PASS |
| 2.26.1 | event-3 readable after failed event-2 | yes | no; reader stops with `WriteAbortedException`, events read=[event-1], file 7934 bytes | FAIL |
| 2.x HEAD ea28f5a78b | control: event-1 readable | yes | yes | PASS |
| 2.x HEAD ea28f5a78b | event-3 readable after failed event-2 | yes | yes; events read=[event-1, event-3], clean EOF, file 3458 bytes | PASS |

Both versions log one `ERROR Serialization of LogEvent failed.` (`NotSerializableException: java.lang.Object`).

Notes: the file sizes differ also because #4271/#4272/#4274 changed the event's serialized form on 2.x; the
verdict rests on readability, not size.
