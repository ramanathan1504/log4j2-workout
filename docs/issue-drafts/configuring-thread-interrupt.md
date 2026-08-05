# DRAFT — not filed

**Title:** Interrupting the thread that happens to be configuring Log4j leaves every appender unusable

---

## Description

Whichever thread makes the first `LogManager.getLogger` call owns building the
configuration. If that thread is interrupted while configuring, the resulting
`LoggerContext` is left unusable — and not only for the appender that was being
started. **Every** appender in the configuration stops receiving events.

Nothing is logged to say the configuration was abandoned part-built.

## How it happens in practice

The owning thread is rarely one chosen deliberately. Library code frequently
acquires a logger during its own initialisation, so the first `getLogger` can land
on any thread that happens to touch that library first.

Observed here: a bounded worker called a DataStax driver method. The driver pulls
in Netty, whose `InternalLoggerFactory` acquires a Log4j logger, which triggered
configuration *on that worker*. The worker's timeout then fired and
`ExecutorService.shutdownNow()` interrupted it mid-configuration.

The result was that **MongoDB and CouchDB appenders — unrelated to the driver,
both healthy, both writing normally minutes earlier — stored nothing**. The only
visible symptom was absent output.

Any application that time-boxes work which might log, or that logs from a thread
it later cancels, is exposed. Task frameworks that cancel on timeout make this
reachable without anyone writing an explicit interrupt.

## Configuration

**Version:** 2.x @ `04c93c1d33`

**Operating system:** macOS 15 (Darwin 25.5.0)

**JDK:** Temurin 21

## Reproduction

Sketch, from the case above:

```java
ExecutorService pool = Executors.newSingleThreadExecutor();
Future<?> f = pool.submit(() -> {
    // Anything whose initialisation acquires a Log4j logger.
    // Netty via a database driver is one real example.
    someLibrary.connect();
});
try {
    f.get(1, TimeUnit.SECONDS);          // deliberately shorter than connect()
} catch (TimeoutException e) {
    pool.shutdownNow();                  // interrupts mid-configuration
}

// Now log from the main thread: appenders unrelated to someLibrary
// are silent, and nothing explains why.
LogManager.getLogger("anything").info("this may go nowhere");
```

Reliability depends on winning a race with configuration, so it may need a few
attempts or a deliberately narrow timeout.

**This is the one draft here without a captured run.** The other three carry
output pasted from a reproduction; this one does not, because the failure is a
race and I have no deterministic trigger for it. What was observed was a single
occurrence: two unrelated appenders, healthy and writing minutes earlier, stored
nothing after a worker was interrupted during configuration. That is suggestive,
not proof. Treat the mechanism described above as a hypothesis until someone
reproduces it on demand.

## What would help

I am not proposing a specific fix — restoring a half-built context safely is
likely to be involved, and interrupt semantics are legitimately the caller's
responsibility.

What seems clearly wrong is the **silence**. If configuration is abandoned
because the configuring thread was interrupted, the `StatusLogger` reporting so
would turn an unexplained absence of logging into a one-line diagnosis. At
present the only way to reach the cause is to know this behaviour already exists.

A second question worth a maintainer's view: whether an interrupt during
configuration should leave the context in a state that can be retried, rather
than one where every subsequent event is silently dropped.

## Note on classification

Lower confidence than the other drafts. The behaviour was reproduced, but the
boundary between it and ordinary interrupt semantics is a judgement call, and I
have not traced exactly which internal state is left inconsistent. Worth a second
opinion before filing.
