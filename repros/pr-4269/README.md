# PR #4269 — `FilteredObjectInputStream` accepts dynamic proxies

https://github.com/apache/logging-log4j2/pull/4269

`ObjectInputStream` resolves a proxy class descriptor through `resolveProxyClass(String[])`, not `resolveClass(ObjectStreamClass)`. `FilteredObjectInputStream` only overrode `resolveClass`, so a `Proxy` over an allowlisted interface (`java.util.Comparator`) with a serializable handler in an allowlisted package (`org.apache.logging.log4j.repro`) was read back without the proxy class ever meeting the allowlist. The class sets no JEP 290 filter of its own, so the gap exists on Java 9+ as well as Java 8. The PR adds a `resolveProxyClass` override that always throws `InvalidObjectException`, regardless of the extra allowed classes passed to the constructor.

## Commands

```bash
cd log4j-pr-4269-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Each run executes `Repro` on Zulu 1.8.0_504 and JBR 17.0.14.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 (Java 8 and 17) | control: `HashMap` | accepted | accepted | PASS |
| 2.26.1 (Java 8 and 17) | control: `java.io.File` | rejected | rejected, `Class is not allowed` | PASS |
| 2.26.1 (Java 8) | `Comparator` proxy | rejected | **accepted**, `com.sun.proxy.$Proxy0` | FAIL |
| 2.26.1 (Java 17) | `Comparator` proxy | rejected | **accepted**, `jdk.proxy1.$Proxy0` | FAIL |
| 2.26.1 (Java 8 and 17) | proxy named in extra allowed classes | rejected | **accepted** | FAIL |
| 2.x HEAD ea28f5a78b (Java 8 and 17) | control: `HashMap` | accepted | accepted | PASS |
| 2.x HEAD ea28f5a78b (Java 8 and 17) | control: `java.io.File` | rejected | rejected | PASS |
| 2.x HEAD ea28f5a78b (Java 8 and 17) | `Comparator` proxy | rejected | rejected, `Proxy classes are not allowed for deserialization` | PASS |
| 2.x HEAD ea28f5a78b (Java 8 and 17) | proxy named in extra allowed classes | rejected | rejected | PASS |

Notes: the baseline accepts the proxy on Java 17 too, which bears out the note's point that the PR body understates the reach by naming only Java 8.
