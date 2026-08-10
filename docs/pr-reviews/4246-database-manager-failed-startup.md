# PR #4246 — skip writes and still shut down after failed DB manager startup

https://github.com/apache/logging-log4j2/pull/4246

| | |
|---|---|
| Author | `arimu1` (dev_Hakaze) |
| Prior merged PRs in this repo | **0** |
| Base | `2.x` |
| Size | +128 −6, 4 files |
| Linked issue | **#4241 — filed by you (`ramanathan1504`) on 2026-08-05 07:12Z** |
| PR opened | 2026-08-06 01:59Z — **18h 46m** after your issue |
| CI | green |

## Verdict: ⚠️ real bug, but the fix trades a loud failure for a silent one. Needs changes.

## Is it really needed?

Yes — you filed the issue, and the diagnosis holds. `AbstractDatabaseManager`
genuinely checks `isRunning()` on the shutdown path and not on the write path
(`AbstractDatabaseManager.java:227,290`), and `CassandraManager.shutdownInternal()`
dereferences `session` unconditionally (`CassandraManager.java:83-86`).

What is worth knowing is the provenance: this is the same author's second PR,
opened ten minutes after #4245, both against issues you filed the previous day,
from an account with no prior merged work here. The tests in the PR assert
exactly the behaviour your issue text specified, so **CI passing tells you
nothing about whether the fix is right** — it tells you the author read your
issue carefully.

## The blocking finding: the NPE is Cassandra-only, and the fix breaks the two managers that got it right

The PR body's premise is that `write()`

> kept accepting events and dereferencing state startup never assigned (NPE per event)

That is true of **exactly one** of the four managers. I checked all of them:

| Manager | `startupInternal` can fail? | `writeInternal` guards `!isRunning()`? |
|---|---|---|
| `CassandraManager` | ✅ `cluster.connect()` throws | ❌ **no guard — this is the NPE** |
| `JpaDatabaseManager` | ✅ `Persistence.createEntityManagerFactory` throws | ✅ throws `AppenderLoggingException` |
| `NoSqlDatabaseManager` | ❌ empty method | ✅ throws `AppenderLoggingException` |
| `JdbcDatabaseManager` | ❌ empty method | n/a |

`JpaDatabaseManager.java:88-95`:

```java
if (!this.isRunning() || this.entityManagerFactory == null
        || this.entityManager == null || this.transaction == null) {
    throw new AppenderLoggingException(
            "Cannot write logging event; JPA manager not connected to the database.");
}
```

`NoSqlDatabaseManager.java:272-277` is the same shape.

So JPA and NoSQL **already solved this**, correctly, by throwing a typed
exception instead of NPEing. The bug is that `CassandraManager.writeInternal`
never got the same guard — it goes straight into `columnMappings` and
`preparedStatement`.

The PR fixes a Cassandra-specific defect by changing base-class behaviour for
all four, and in doing so replaces the two correct implementations' loud,
typed failure with a silent drop.

The PR adds this to `write(LogEvent, Serializable)`:

```java
if (!this.isRunning()) {
    if (!this.writeWhileNotRunningLogged) { … LOGGER.warn(…); }
    return;
}
```

`AppenderLoggingException` is the mechanism by which `ignoreExceptions="false"`
reaches the application. A user who configured

```xml
<JpaAppender name="db" ignoreExceptions="false"> … </JpaAppender>
```

is explicitly asking to be told, on the calling thread, when logging to the
database fails. After this PR the base class returns before `writeInternal` is
ever reached, so that exception is never constructed, the appender never sees a
failure, and the application is never told. The user gets **one** status-logger
warning for the entire lifetime of the process and silent data loss thereafter.

That is a behaviour regression for JPA, MongoDB and CouchDB users, and it is not
mentioned in the PR body. The bug being fixed is "NPE per event"; the fix
overshoots into "no signal at all".

The narrower change that fixes #4241 without this side effect: leave `write()`
alone and give `CassandraManager.writeInternal` the same not-running guard that
`JpaDatabaseManager` and `NoSqlDatabaseManager` already have. That keeps the
`ignoreExceptions` contract intact and still stops the per-event NPE — and it is
a three-line change in one module instead of a contract change in the base class.

## Second finding: the `shutdownInternal()` contract change is under-verified

The PR rewrites the javadoc of `shutdownInternal()` from "will only be called
*after* `startupInternal()`" to "may be called after a failed startup … so
implementations must tolerate partially initialized state", and makes
`shutdown()` call it unconditionally.

That widens the contract for **every** implementation, not just Cassandra. I
checked all four in the 2.x tree:

| Implementation | Null-safe already? |
|---|---|
| `JpaDatabaseManager.java:63` | ✅ guards `entityManager`, `transaction`, `entityManagerFactory` |
| `JdbcDatabaseManager.java:912` | ✅ guards `reconnector`; `commitAndCloseAll()` guards `connection`/`statement`/`connectionSource` |
| `NoSqlDatabaseManager.java:262` | ✅ `Closer.closeSilently(connection)` |
| `CassandraManager.java:83` | ❌ — fixed by this PR |

So the change happens to be safe in-tree. But `AbstractDatabaseManager` is a
public extension point and `shutdownInternal` is `protected abstract` — third-party
database managers exist (this is how people write custom DB appenders), and they
were written against the old guarantee. Any of them that dereferences a field
assigned in `startupInternal()` will now NPE on shutdown where it previously did
nothing. That deserves an explicit note in the PR and probably a changelog entry
of type `changed`, not just `fixed`.

## Non-blocking

- `writeWhileNotRunningLogged` is reset in `startup()` but not in `shutdown()`.
  A manager that is stopped, restarted, and fails startup a second time will not
  re-log — minor, but the field is there specifically to control that.
- `testShutdownWithoutStartupStillRunsShutdownInternal` asserts the new contract
  is exercised, but there is no test that a **third-party** manager which is not
  null-safe now fails. That is the risk the change introduces, and it is untested.
- The `CassandraManager` fix itself is good: `try { session.close() } finally {
  cluster.close() }` is right, and it is what #4242 (also yours) needs.

## Repro

See **[`repros/pr-4246/`](../../repros/pr-4246/)** — manual steps, needs no
database service.

The key realisation from the table above is that **JPA is the clean vehicle**:
`Persistence.createEntityManagerFactory` throws on an unknown persistence unit,
so `startupInternal` fails for real without anything external being down, and
`JpaDatabaseManager.writeInternal` already throws the typed exception the PR
would suppress.

Your existing evidence for the underlying bug:

- `docs/evidence/nosql-cassandra-startup.log`
- `docs/evidence/nosql-hang-threads.txt`

---

## ── paste-ready comment ──

Thanks for picking this up. The diagnosis matches what I filed in #4241, and
the `CassandraManager.shutdownInternal()` change is exactly right — `try {
session.close(); } finally { cluster.close(); }` is what #4242 needs too.

I have a concern about the `write()` early return, though. The not-running case
is already handled one level down, and handled deliberately loudly —
`NoSqlDatabaseManager.writeInternal`:

```java
if (!this.isRunning() || this.connection == null || this.connection.isClosed()) {
    throw new AppenderLoggingException(
            "Cannot write logging event; NoSQL manager not connected to the database.");
}
```

That `AppenderLoggingException` is how `ignoreExceptions="false"` reaches the
application. With the new guard in the base class, `writeInternal` is never
reached, so a user who explicitly configured `ignoreExceptions="false"` on a
`NoSql` appender now gets a single `StatusLogger` warning and silent event loss
instead of a failure on the calling thread. That is a behaviour change for
MongoDB/CouchDB users that is not called out in the description.

Would it work to fix the per-event NPE where it actually originates — give
`CassandraManager.writeInternal` the same not-running guard `NoSqlDatabaseManager`
already has — and leave `AbstractDatabaseManager.write()` alone? That closes
#4241 without changing the `ignoreExceptions` contract.

On the `shutdown()` half: making `shutdownInternal()` run after a failed startup
is a widening of a documented contract on a `protected abstract` method of a
public extension point. All four in-tree implementations happen to tolerate it
(I checked JPA, JDBC, NoSQL, Cassandra), but third-party database managers were
written against *"will only be called after `startupInternal()`"*. Could the
changelog entry be `changed` rather than `fixed`, and the javadoc change called
out in the description as a compatibility note?
