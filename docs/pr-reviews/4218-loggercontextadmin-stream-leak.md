# PR #4218 — close streams in `LoggerContextAdmin.setConfigLocationUri`

https://github.com/apache/logging-log4j2/pull/4218

| | |
|---|---|
| Author | `SebTardif` |
| Prior merged PRs in this repo | **3** — established |
| Base | `2.x` |
| Size | +172 −5, 3 files |
| Linked issue | none |
| CI | green |
| Reviews | **`ramanathan1504`: CHANGES_REQUESTED** — author has since responded |

## Verdict: ✅ your review was right, the response addresses it, and the production change is now verified correct. Remaining concerns are test-stability only.

## Your review was the right call

You asked the question that matters:

> I reverted `LoggerContextAdmin.java` back to `2.x` and kept your tests, all 3
> still pass. They only check the config loads, not that anything is closed. Can
> you make at least one of them fail without the production change?

That is exactly the check that separates a test from a decoration, and it caught
a real gap. The author responded with
`setConfigLocationUri_closesCallerOwnedStreamWhenFactoryDoesNotConsumeIt`, which
installs a `ConfigurationFactory` that never consumes the stream, and asserts on
open-FD count. They also dropped the full-buffering approach you objected to in
point 2.

## The production change is correct — I verified the part that could have broken it

```java
try (final InputStream in = new FileInputStream(configFile)) {
    final ConfigurationSource configSource = new ConfigurationSource(in, configFile);
    final Configuration config = ConfigurationFactory.getInstance().getConfiguration(loggerContext, configSource);
    loggerContext.start(config);
}
```

The obvious risk is that closing the stream after `start()` breaks later re-reads
for `monitorInterval`. It does not. `ConfigurationSource.resetInputStream()`
(line 279) opens a **fresh** stream rather than reusing the retained one:

```java
File file = getFile();
if (file != null) {
    return new ConfigurationSource(Files.newInputStream(file.toPath()), getFile());
}
```

So the File path is safe. The URL path (line 288) requires `data != null` to
reconstruct, which a stream-backed source never has — but that was equally true
before this PR, so nothing regresses. The author's inline comment claiming
re-reads still work is accurate.

## Remaining concerns — all in the test, none in the fix

### 1. FD counting will flake in CI

```java
final long expectedFdCount = getOpenFileDescriptorCount();
...
assertEquals(expectedFdCount, getOpenFileDescriptorCount());
```

Open-descriptor count is a **process-wide** number in a JVM that surefire shares
across test classes. Class loading, JIT, GC closing a jar handle, or any
concurrent test opening a file moves it between the two samples.

The author's own evidence hints at this: they report the pre-fix failure as
`expected: 173 but was: 177` — a delta of **4** for a single leaked stream. If one
unclosed `FileInputStream` produced a delta of 4, three of those descriptors came
from something else, which is precisely the noise that makes the assertion
unreliable.

A more robust shape: wrap the stream so the test observes `close()` directly —
e.g. a `FilterInputStream` recording closure, or a factory that captures
`source.getInputStream()` and asserts on it afterwards. That tests the actual
contract rather than a proxy for it.

### 2. The FD assertion is a silent no-op off Unix

```java
private static long getOpenFileDescriptorCount() {
    final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    if (os instanceof UnixOperatingSystemMXBean) {
        return ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount();
    }
    return 0;
}
```

On Windows this returns 0 both times, so `assertEquals(0, 0)` passes whether or
not the fix is present. The test then rests entirely on the `Files.delete`
check — which is a genuine Windows signal, so the test is not worthless there,
but the headline assertion is vacuous and nothing says so.

`com.sun.management.UnixOperatingSystemMXBean` is also a proprietary API; fine on
HotSpot, not guaranteed elsewhere.

### 3. Global static mutation is a parallel-test hazard

`ConfigurationFactory.setConfigurationFactory(...)` is process-global. The
`@AfterEach` reset is correct, but if `log4j-core-test` ever runs classes in
parallel within one JVM, a concurrently-running test would pick up a stub factory
that returns `DefaultConfiguration` for `getSupportedTypes() == {"*"}` — failing
in a way that looks unrelated and reproduces once in fifty runs.

Worth confirming the module's surefire configuration is single-threaded, or
annotating for isolation.

### 4. The tests leak a `LoggerContext` each

All four tests do `new LoggerContext(...)` and never `stop()` it, so a PR whose
subject is resource cleanup leaks four contexts across its own suite. A one-line
`@AfterEach` fixes it. The module's usual extensions (`@UsingStatusListener`,
`LoggerContextSource`) are also bypassed, but that is a style point, not a leak.

## Repro

None built. The observable is a file descriptor, not appender output — the bench
runs a real JVM per cell but has no JMX/`LoggerContextAdmin` app, and building one
to count FDs would reproduce the same flakiness the test has.

---

## ── paste-ready comment ──

Thanks for turning that around — the new
`setConfigLocationUri_closesCallerOwnedStreamWhenFactoryDoesNotConsumeIt` does
what I asked. I re-ran the check on my side rather than take it on trust: `2.x`
plus your test files only, without `LoggerContextAdmin.java`, gives

```
Tests run: 4, Failures: 1, Errors: 0, Skipped: 0
  <<< FAILURE! -- LoggerContextAdminSetConfigLocationUriTest
```

and adding the fix back turns it green. That is a real red-green now. Dropping
the buffering resolves my second point.

I also checked the thing that worried me most about the fix, and it is fine:
closing the stream after `start()` does not break `monitorInterval` re-reads,
because `ConfigurationSource.resetInputStream()` opens a fresh stream from the
file rather than reusing the retained one. Your inline comment is accurate.

Your answer to my third question is right too, and I am glad it made it into the
changelog `<description>` rather than just the thread — "the gap is the
caller-owns-stream contract when a factory never consumes" is the accurate
description of what this fixes.

**The production change is good and I am no longer blocking on it.** One ask on
the test before merge, then two you can take or leave.

**1. The FD count will flake — this is the one I would like changed.**
Open-descriptor count is process-wide in a JVM surefire shares across test
classes, so class loading, GC closing a jar handle, or any concurrent test moves
it between the two samples. Your own numbers hint at it: `expected: 173 but was:
177` is a delta of 4 for one leaked stream, so three of those descriptors came
from somewhere else. Could the test observe `close()` directly instead? Wrapping
the stream in a `FilterInputStream` that records closure, or capturing
`source.getInputStream()` in the stub factory and asserting on it afterwards,
tests the contract rather than a proxy for it — and it is deterministic, which
matters more than usual for a test that will run on every PR in this project.

**2. `getOpenFileDescriptorCount()` returns 0 when the bean is not a
`UnixOperatingSystemMXBean`,** so on Windows the assertion is `assertEquals(0, 0)`
and passes with or without the fix. The `Files.delete` check still carries it
there, so the test is not worthless on Windows — but the headline assertion is
vacuous and nothing says so. A comment or an assumption-skip off Unix would do.
Moot if you take (1).

**3. Minor, and I smiled at it:** all four tests create a `LoggerContext` and
never `stop()` it, so the resource-cleanup PR leaks four contexts through its own
suite. An `@AfterEach ctx.stop()` closes that.

Also worth noting for later: `ConfigurationFactory.setConfigurationFactory` is a
global static, and the `@AfterEach` reset is the right call — but if this module
ever runs test classes in parallel, a concurrent test would pick up a factory
returning `DefaultConfiguration` for `"*"` and fail in a way that looks
unrelated. Not something to fix here.
