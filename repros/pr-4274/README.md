# PR #4274 — `ParameterizedMessage` with null arguments failed to serialize; `ObjectArrayMessage` elements unwrapped

https://github.com/apache/logging-log4j2/pull/4274

## Mechanism

`ParameterizedMessage.writeObject` wrote `args.length` and threw `NullPointerException` when the argument array was `null`. `ObjectArrayMessage` wrote its `Object[]` straight onto the outer stream, so one non-serializable element failed the whole message. Both now write `SerializationUtil.wrapObjects(...)`, a `byte[][]` with each element serialized separately and replaced by `String.valueOf` when it cannot be, with new `serialVersionUID`s. `Repro.java` round-trips each message through `ObjectOutputStream` and `FilteredObjectInputStream`.

## Commands

```bash
cd log4j-pr-4274-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`jars-2x` is the `2.x` HEAD build (`ea28f5a78b`); it contains this PR and the rest of the serialization stack (#4269–#4274), so the fixed column is cumulative. Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `ParameterizedMessage("Hello!", (Object[]) null, null)` | params=null, `Hello!` | `NullPointerException: Cannot read the array length because "this.args" is null` | FAIL |
| 2.26.1 | `ObjectArrayMessage("A", nonSerializable, "C")` | `[A, non-serializable, C]` | `NotSerializableException: Repro$NonSerializable` | FAIL |
| 2.26.1 | control: `ObjectArrayMessage("A", 1, "C")` | `[A, 1, C]` | `[A, 1, C]` | PASS |
| 2.x HEAD ea28f5a78b | null arguments | params=null, `Hello!` | params=null, `Hello!` | PASS |
| 2.x HEAD ea28f5a78b | non-serializable element | `[A, non-serializable, C]` | `[A, non-serializable, C]` | PASS |
| 2.x HEAD ea28f5a78b | control | `[A, 1, C]` | `[A, 1, C]` | PASS |

`serialVersionUID`: `ObjectArrayMessage` -5903272448334166185 → 3470900984521271892, `ParameterizedMessage` -665975803997290697 → -4633763537142017035, so the two versions cannot read each other's messages.
