# PR #4271 — Log4jLogEvent message escapes the deserialization allow-list through `MarshalledObject`

https://github.com/apache/logging-log4j2/pull/4271

## Mechanism

`Log4jLogEvent.LogEventProxy` carried its `Message` in a `java.rmi.MarshalledObject`. `MarshalledObject.get()` deserializes its bytes on its own private `ObjectInputStream`, with no filter, so a message class that is not on Log4j's allow-list is instantiated (and its `readObject` runs) even when the outer stream is a `FilteredObjectInputStream`. The PR writes the message with `SerializationUtil.writeWrappedObject`/`readWrappedObject`, which re-applies the outer filter, and drops `MarshalledObject` from the allow-list. `Repro.java` serializes a `Log4jLogEvent` whose message is `Repro$GadgetMessage` (a `Message` with a counting `readObject`), then reads it back through `org.apache.logging.log4j.util.FilteredObjectInputStream`.

## Commands

```bash
cd log4j-pr-4271-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`jars-2x` is the `2.x` HEAD build (`ea28f5a78b`); it contains this PR and the rest of the serialization stack (#4269–#4274), so the fixed column is cumulative. Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | non-allow-listed message class | rejected, `readObject` not run | accepted, `GadgetMessage.readObject` calls=1, message=`Repro$GadgetMessage` | FAIL |
| 2.26.1 | stream free of `MarshalledObject` | absent | present (1276 bytes) | FAIL |
| 2.26.1 | control: `SimpleMessage` round trip | `hello` | `SimpleMessage=hello` | PASS |
| 2.x HEAD ea28f5a78b | non-allow-listed message class | rejected, `readObject` not run | `InvalidObjectException: Class is not allowed for deserialization: Repro$GadgetMessage`, calls=0 | PASS |
| 2.x HEAD ea28f5a78b | stream free of `MarshalledObject` | absent | absent (1294 bytes) | PASS |
| 2.x HEAD ea28f5a78b | control: `SimpleMessage` round trip | `hello` | `SimpleMessage=hello` | PASS |

Notes: wire compatibility (events written by 2.8.2–2.25.x rejected by the new reader) was not exercised here.
