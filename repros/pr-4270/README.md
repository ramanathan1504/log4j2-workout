# PR #4270 — `SerialUtil` test helper skipped the allow-list on Java 9+

https://github.com/apache/logging-log4j2/pull/4270 (backported to `2.25.x` in #4466)

## Mechanism

`org.apache.logging.log4j.test.junit.SerialUtil` (in `log4j-api-test`) is the helper that Log4j's own serialization tests use to read objects back. On Java 9 and later it returned a plain `ObjectInputStream`, so tests that expected a class outside the allow-list to be rejected were never rejecting anything. It now returns a `FilteredObjectInputStream` unless `jdk.serialFilter` is set, and gains overloads that take extra allowed classes. `Repro.java` serializes a `java.io.File`, which is not on the allow-list, and a `HashMap`, which is. It uses only the one-argument `deserialize`, because the overloads with extra allowed classes do not exist in 2.25.5. JDK 17.

## Commands

```bash
cd log4j-pr-4270-repro
./run.sh 2.25.5 2.25.5
./run.sh ~/apache/vote-2.25.6/staged-jars 2.25.6-staged
```

Outputs: `output/<label>/result.txt`.

## Results

| Case | 2.25.5 | 2.25.6 staged |
|---|---|---|
| `java.io.File`, not on the allow-list | **FAIL** accepted | PASS `InvalidObjectException: Class is not allowed for deserialization: java.io.File` |
| control: `HashMap` | PASS accepted | PASS accepted |
