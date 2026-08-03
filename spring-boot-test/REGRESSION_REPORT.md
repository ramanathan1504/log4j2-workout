# Log4j2 Regression Verification Report
## Issue: `ThrowableStackTraceRenderer` NPE with Mutating `hashCode()` 

**Date:** 2026-06-23  
**Tested by:** Automated regression test — `MutatingHashCodeRegressionTest`  
**Test location:** `spring-boot-test/src/test/java/com/springtest/regression/MutatingHashCodeRegressionTest.java`

---

## 1. Executive Summary

A **NullPointerException regression** was introduced in Log4j **2.25.0** inside `ThrowableStackTraceRenderer` 
(part of `log4j-layout-template-json`). It is triggered when logging an exception whose `hashCode()` 
returns a different value on each invocation — violating the `hashCode` contract.

The bug affects **all versions from 2.25.0 through 2.26.0**, including **2.25.4** which is used in 
Apache Spark branch-4.2. The fix — using `IdentityHashMap` instead of `HashMap` for metadata caching — 
is available in the local `2.x` branch as **2.27.0-SNAPSHOT**.

---

## 2. Verification Matrix

| Version             | Role               | Test Result | Evidence                                                                           |
|:--------------------|:-------------------|:-----------:|:-----------------------------------------------------------------------------------|
| **2.24.1**          | Baseline (pre-bug) | ✅ **PASS**  | Stack trace rendered correctly. No NPE in StatusLogger.                            |
| **2.25.0**          | Regression start   | ❌ **FAIL**  | `NullPointerException: Cannot read field "stackLength" because "metadata" is null` |
| **2.25.4**          | Spark branch-4.2   | ❌ **FAIL**  | `NullPointerException: Cannot read field "stackLength" because "metadata" is null` |
| **2.26.0**          | Current stable     | ❌ **FAIL**  | `NullPointerException: Cannot read field "stackTrace" because "metadata" is null`  |
| **2.27.0-SNAPSHOT** | Fixed (2.x branch) | ✅ **PASS**  | Stack trace rendered correctly. No NPE in StatusLogger.                            |

---

## 3. Root Cause Analysis

### What changed in 2.25.0?

`ThrowableStackTraceRenderer` was refactored to cache rendered exception metadata in a `HashMap<Throwable, ThrowableMetadata>`. The lookup sequence is:

```java
// Simplified representation of the broken logic (2.25.0–2.26.0)
ThrowableMetadata metadata = cache.get(throwable);  // uses hashCode() → key A
// ... time passes, renderer recurses ...
cache.put(throwable, computedMetadata);             // hashCode() → key B (different bucket!)
ThrowableMetadata result = cache.get(throwable);   // hashCode() → key C → MISS → null
result.stackTrace  // ← NullPointerException
```

When `throwable.hashCode()` is not stable, `HashMap` puts the entry in one bucket and retrieves from another — returning `null`. The subsequent field access causes NPE.

### Why does this matter for Spark?

Apache Spark's internal objects (e.g., `TaskContext`, custom `SparkException` subclasses) may hold references to mutable state. Any exception that directly or transitively delegates `hashCode()` to such objects will trigger this bug. Real-world manifests as: **Spark executors silently fail to log errors**, causing lost diagnostics during task failures.

### The Fix (2.27.0-SNAPSHOT)

Replace `HashMap` with `IdentityHashMap`:

```java
// Fixed logic (2.27.0-SNAPSHOT)
Map<Throwable, ThrowableMetadata> cache = new IdentityHashMap<>();
```

`IdentityHashMap` uses object identity (`System.identityHashCode` / reference equality) rather than `hashCode()`. This makes the cache immune to mutable `hashCode()` implementations.

- **Commit:** https://github.com/ramanathan1504/logging-log4j2/commit/26933122f5711bf06be05cdb1fe6fc5ae61122e1  
- **Commit:** https://github.com/ramanathan1504/logging-log4j2/commit/8f964f176afbdfa2ca9c240fe0f27e160739128f

---

## 4. Test Methodology

### Test Case: `MutatingException`

```java
static class MutatingException extends RuntimeException {
    private int counter = 0;

    @Override
    public int hashCode() {
        return ++counter;  // Returns a different value every call — violates contract
    }
}
```

### Detection Mechanism

Rather than inspecting raw stderr (which Log4j's `StatusLogger` bypasses), the test uses the
official `StatusListener` API to intercept Log4j internal error events:

```java
StatusLogger.getLogger().registerListener(statusListener);
// ... log the exception ...
boolean hasNPE = capturedStatusEvents.stream().anyMatch(event ->
    event.getThrowable() instanceof NullPointerException ...);
assertFalse(hasNPE, "REGRESSION DETECTED ...");
```

### Configuration

The test uses `JsonTemplateLayout` (via `EcsLayout.json`) which is the code path that invokes
`ThrowableStackTraceRenderer`. Config: `src/test/resources/log4j2-test.xml`.

---

## 5. Raw Test Output

### ✅ 2.24.1 — PASS (Baseline)

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Stack trace rendered in full JSON without any StatusLogger errors. The `ThrowableStackTraceRenderer` 
class did not exist in its current form in 2.24.1 — the rendering was handled by a simpler, 
stateless code path.

---

### ❌ 2.25.0 — FAIL (Regression Introduced)

```
[StatusLogger ERROR] An exception occurred processing Appender Console
  | java.lang.NullPointerException: Cannot read field "stackLength" because "metadata" is null

[ERROR] Tests run: 1, Failures: 1, Errors: 0
REGRESSION DETECTED: NullPointerException in ThrowableStackTraceRenderer.
[INFO] BUILD FAILURE
```

**Conclusion:** This is the exact version where the Map-based metadata caching was introduced.
The NPE appears immediately on the first log-with-exception call.

---

### ❌ 2.25.4 — FAIL (Spark branch-4.2 version)

```
[StatusLogger ERROR] An exception occurred processing Appender Console
  | java.lang.NullPointerException: Cannot read field "stackLength" because "metadata" is null

[ERROR] Tests run: 1, Failures: 1, Errors: 0
REGRESSION DETECTED: NullPointerException in ThrowableStackTraceRenderer.
[INFO] BUILD FAILURE
```

**Conclusion:** Identical behavior to 2.25.0. Apache Spark branch-4.2 depends on this version.
Any Spark application using `JsonTemplateLayout` (the recommended structured logging layout) and 
encountering exceptions with mutable `hashCode()` will silently lose log output.

---

### ❌ 2.26.0 — FAIL (Current Stable)

```
[StatusLogger ERROR] An exception occurred processing Appender Console
  | java.lang.NullPointerException: Cannot read field "stackTrace" because "metadata" is null

[ERROR] Tests run: 1, Failures: 1, Errors: 0
REGRESSION DETECTED: NullPointerException in ThrowableStackTraceRenderer.
[INFO] BUILD FAILURE
```

**Note:** The field name differs slightly from 2.25.x (`"stackTrace"` vs `"stackLength"`), indicating 
minor internal refactoring between minor versions. The core bug — null `metadata` from HashMap miss — 
is identical.

---

### ✅ 2.27.0-SNAPSHOT — PASS (Fixed)

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

No StatusLogger errors. Stack trace rendered correctly in JSON. The `IdentityHashMap` fix correctly
handles the mutable `hashCode()` case.

---

> I have verified the complete regression path for this bug:
>
> - **2.24.1** is **unaffected** — the renderer did not use the Map-based caching.  
> - **2.25.0** is the **origin of the regression** — Map-based metadata caching introduced.
> - **2.25.4** (used in Spark branch-4.2) is **confirmed vulnerable** — same code path, same NPE.  
> - **2.26.0** (current stable) is **also vulnerable** — minor field name variation, same root cause.  
> - **The fix is in the `2.x` branch** — `IdentityHashMap` replaces `HashMap`, making the renderer
>   immune to objects with mutable `hashCode()`.
>
> **Recommendation for Spark:** Upgrade to Log4j 2.27.0 (once released) or apply the patch commit

---

## 7. Reproducible Zip

```bash
unzip log4j-npe-repro.zip
cd log4j-npe-repro

# Test a single version:
mvn test -Dlog4j.version=2.25.4          # ❌ FAIL — Spark's version
mvn test -Dlog4j.version=2.24.1          # ✅ PASS — safe baseline

# Run all 5 versions at once:
chmod +x run-all.sh && ./run-all.sh

# Test the fix (requires local 2.x branch in ~/.m2):
mvn test -Dlog4j.version=2.27.0-SNAPSHOT -o
```


