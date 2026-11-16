# PR #4272 — `SortedArrayStringMap` deserialization trusted the stream's capacity

https://github.com/apache/logging-log4j2/pull/4272

## Mechanism

Up to 2.26.1, `SortedArrayStringMap.writeObject` wrote a capacity and a mapping count in block data, and `readObject` called `inflateTable(capacity)` before reading any entry, so a 146-byte stream could make the reader allocate two arrays of any length. The PR uses default serialization for `keys`/`size`/`threshold` and writes `values` as a `byte[][]`, one wrapped element per value, with a new `serialVersionUID`; array lengths are now read by the JDK, which reads every element it allocates. `run.sh` writes a legacy stream with the 2.26.1 classpath (`Repro forge`), patches the capacity to 16777216 with one mapping, and reads it with the version under test (`Repro check`) through `FilteredObjectInputStream`, measuring `keys.length` by reflection.

## Commands

```bash
cd log4j-pr-4272-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`jars-2x` is the `2.x` HEAD build (`ea28f5a78b`); it contains this PR and the rest of the serialization stack (#4269–#4274), so the fixed column is cumulative. Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | forged legacy capacity (146 bytes, capacity 2^24, 1 mapping) | rejected or bounded | accepted, size=1, `keys.length=16777216` | FAIL |
| 2.26.1 | non-serializable value | string form | `null` | FAIL |
| 2.26.1 | control: serializable value | `text` | `text`, `keys.length=4` | PASS |
| 2.x HEAD ea28f5a78b | forged legacy capacity | rejected or bounded | `InvalidClassException`: stream UID -5748905872274478116, local -3516174557123823846 | PASS |
| 2.x HEAD ea28f5a78b | non-serializable value | string form | `non-serializable` | PASS |
| 2.x HEAD ea28f5a78b | control: serializable value | `text` | `text`, `keys.length=4` | PASS |

Notes: the fixed build rejects the forged stream because the serialized form (and UID) changed, not because of a length check; legacy 2.26.1 context maps are equally unreadable. The non-serializable-value fallback comes from the fallback commit in #4271, not from this PR's own diff.
