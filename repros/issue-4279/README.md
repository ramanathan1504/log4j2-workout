# Log4j issue #4279 reproduction

https://github.com/apache/logging-log4j2/issues/4279

> `ThrowableStackTraceRenderer` throws `NullPointerException` when
> `Throwable.getCause()` returns different instances

## Reproduction

```bash
unzip log4j-issue-4279-repro.zip
cd log4j-issue-4279-repro
./run.sh 2.24.1            # the last good version
./run.sh 2.26.1            # or any other
./run.sh 3.0.0-SNAPSHOT    # pairs core 3.x with log4j-api 2.24.3
```

The project is standalone — `log4j-api`, `log4j-core` and
`log4j-layout-template-json`, no parent POM and no reference to the bench it
came from. `run.sh` forks a real JVM (`exec:exec`).

`probe/run-probe.sh` runs the same cases with PatternLayout only, so the 3.x
column can be measured without `log4j-layout-template-json`, whose 3.x module
requires JDK 17 exactly and does not build here.

## Mechanism

`ThrowableStackTraceRenderer` walks the causal chain **twice**.

`Context.Metadata.ofThrowable` walks it once up front and stores a `Metadata`
per throwable in an `IdentityHashMap`. `renderThrowable` then walks it again,
calling `throwable.getCause()` a second time and looking the result up in that
map:

```java
final Context.Metadata metadata = context.metadataByThrowable.get(throwable);   // null
...
renderStackTraceElements(buffer, context, metadata, prefix, lineSeparator);     // NPE
```

If the second `getCause()` does not return the same instance as the first, the
lookup misses and `renderStackTraceElements` dereferences `null`. The identity
map is doing exactly what it was added for — surviving broken `equals`/
`hashCode` — but it assumes `getCause()` is idempotent, and nothing enforces
that.

Introduced by `b55c4d3fa2` ("Consolidate stack trace rendering in Pattern
Layout", #2691), first released in **2.25.0** — which matches the reporter's
2.24.3-good / 2.25.0-bad boundary exactly.

## Verification matrix

Five cases against `%ex`, `%xEx`, `%rEx` and `JsonTemplateLayout`.
`THROWN` = propagated into the caller (`ignoreExceptions="false"`);
`FAIL` = swallowed, event lost, `StatusLogger` ERROR only (the default).

| Case | 2.24.1 | 2.25.4 | 2.25.5 | 2.26.0 | 2.26.1 | 2.27.0-SNAPSHOT | 3.0.0-SNAPSHOT |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `stable-cause` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `unstable-cause` (as reported) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `wrapped-unstable` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `late-cause` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `unstable-stacktrace` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `unstable-cause-chain` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

`JsonTemplateLayout` passes every NPE case on every version: it renders through
`Throwable.printStackTrace`, which reads each cause once. Verified against the
written JSON, not the exit code — `error.stack_trace` carries the
`Caused by: java.lang.Throwable: Throwable` line.

Full output per version is under `output/`. 2.25.0–2.25.3 are not on this
pack's version axis; the break is bracketed by 2.24.1 passing and 2.25.4
failing.

## What the reporter did not mention

**1. All three throwable converters fail, not just `%ex`.** `%xEx` and `%rEx`
render through the same base class, and `ThrowableExtendedStackTraceRenderer`
and `ThrowableInvertedStackTraceRenderer` inherit `renderStackTraceElements`
unchanged.

**2. The unstable exception does not have to be the one logged.** In
`wrapped-unstable` it sits one level down as the cause of an ordinary
`Exception`. Any library that hands back such an exception poisons every log
statement that wraps it.

**3. The trigger is non-idempotence, not "returns a new instance".**
`late-cause` overrides nothing exotic — `getCause()` returns `null` on the
first call and a `Throwable` on the second, the shape a lazily-resolved cause
takes. The metadata pass records no cause, the render pass finds one, and the
lookup misses. This is the realistic version of the bug.

**4. With the default `ignoreExceptions="true"` the event is silently lost.**
The reporter's config sets it to `false`, so the NPE propagates into the
application and is loud. The default swallows it: `out-exq.log` holds 12 lines
for the two passing cases and nothing at all for the three failing ones, with
only a `StatusLogger` ERROR to say so.

**5. The `unstable-stacktrace` case passes**, so the sibling capture added for
[#3940](https://github.com/apache/logging-log4j2/issues/3940) /
[#3955](https://github.com/apache/logging-log4j2/pull/3955) holds. `getCause()`
is the one member of that family still read twice — `getStackTrace()` and
`getSuppressed()` are captured into `Metadata`, `getCause()` is not.

## One finding here is *not* part of this regression

`unstable-cause-chain` — an exception whose fresh cause is itself unstable —
dies with `StackOverflowError` or `OutOfMemoryError` on **every** version
tested, 2.24.1 included, and on `JsonTemplateLayout` too. `populateMetadata`
recurses on `getCause()` with an identity-based visited set that can never
match, and `maxLineCount` does not bound it because it runs before any line
accounting. That is a pre-existing unbounded walk, not something 2.25.0 broke.
It is listed so that a fix for #4279 is not mistaken for a fix for it.
