# Log4j issue #4262 reproduction

https://github.com/apache/logging-log4j2/issues/4262

> `ConsoleAppender` `immediateFlush=true` is hardcoded

## Reproduction

```bash
unzip log4j-issue-4262-repro.zip
cd log4j-issue-4262-repro
./run.sh 2.24.1            # the oldest version on this pack's axis
./run.sh 2.26.1            # the version reported
./run.sh 3.0.0-SNAPSHOT    # pairs core 3.x with log4j-api 2.24.3
```

The project is standalone — `log4j-api` and `log4j-core`, no parent POM and no
reference to the pack it came from. `run.sh` forks a real JVM (`exec:exec`).

The reproduction replaces `System.out` with a counting stream *before* Log4j
initialises, so the appender wraps it, then logs one 14-byte event and asks how
many bytes crossed the stream boundary. A `File` appender configured with the
same three attributes runs alongside as a control.

## The report is correct, and it is three attributes, not one

`ConsoleAppender.Builder` extends `AbstractOutputStreamAppender.Builder`, which
declares three `@PluginBuilderAttribute` fields:

```java
@PluginBuilderAttribute private boolean bufferedIo = true;
@PluginBuilderAttribute private int     bufferSize = Constants.ENCODER_BYTE_BUFFER_SIZE;
@PluginBuilderAttribute private boolean immediateFlush = true;
```

`ConsoleAppender.build()` reads none of them. The constructor hardcodes the
flush flag:

```java
super(name, layout, filter, ignoreExceptions, true, properties, manager);
```

and the manager is built through a constructor that takes no buffer size:

```java
new OutputStreamManager(data.os, data.name, data.layout, true);
// -> this(os, streamName, layout, writeHeader, Constants.ENCODER_BYTE_BUFFER_SIZE)
```

So `immediateFlush` and `bufferSize` are both parsed, bound onto the builder,
and then dropped. `bufferedIo` is accepted too and is not even documented for
this appender.

## Nothing warns

With `-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE`, the plugin system
reports the builder fully populated with the configured values and then says
nothing more (`output/statuslogger-trace-2.26.1.txt`):

```
DEBUG ConsoleAppender$Builder(target="SYSTEM_OUT", follow="null", direct="null",
      bufferedIo="true", bufferSize="256", immediateFlush="false", ... )
DEBUG FileAppender$Builder(   ... bufferedIo="true", bufferSize="256", immediateFlush="false", ... )
```

The two appenders are handed identical values. One honours them.

## Verification matrix

Config under test: `<Console immediateFlush="false" bufferedIo="true" bufferSize="256">`,
with a `File` appender carrying the same three attributes as a control.

| Check | 2.24.1 | 2.25.4 | 2.25.5 | 2.26.0 | 2.26.1 | 2.27.0-SNAPSHOT | 3.0.0-SNAPSHOT |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `[A]` Console appender field `immediateFlush` is `false` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `[B]` no bytes reach `System.out` after one event | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `[C]` Console manager `ByteBuffer` capacity is 256 | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `[D]` control: File appender field `immediateFlush` is `false` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `[E]` control: no bytes reach the file after one event | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `[F]` control: File manager `ByteBuffer` capacity is 256 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

Every version fails the same three checks and passes the same three controls.
Measured values are identical everywhere: `immediateFlush` reads `true`, all 14
bytes of the event are on `System.out` before the next statement runs, and the
buffer is the default 8192 rather than the configured 256. Full output per
version is under `output/`.

The three control checks matter: they are the same attribute names, the same
values, the same config file and the same plugin machinery. The difference is
entirely in `ConsoleAppender.build()`.

## Since when

- The hardcoded `true` predates the 2.0 GA — it is in the constructor as far
  back as the 2013 module rename (`b93cdf9416`), and `61b77dfb6e` (2018, the
  `Property[]` array) only moved it along.
- The attribute started being *accepted from configuration* in **2.7**, when
  `2207cae2a1` refactored `ConsoleAppender.Builder` to extend
  `AbstractOutputStreamAppender.Builder`. That commit is what turned a
  never-configurable field into a silently ignored one.

There is no good version to fall back to. This is not a regression.

## The documentation says both are configurable

`src/site/antora/modules/ROOT/pages/manual/appenders.adoc`, the Console Appender
attribute table — identical text in the `2.x` and `main` branches:

| Attribute | Line (2.x / main) | Documented default | Actual behaviour |
|---|---|---|---|
| `bufferSize` | 254 / 252 | `8192`, "the size of the `ByteBuffer` internally used by the appender" | fixed at `log4j2.encoderByteBufferSize`, config value discarded |
| `immediateFlush` | 307 / 308 | `true`, "if set to `true`, the appender will flush … after each log event" | fixed at `true` |

So the docs need a change whichever way the issue is resolved.

## What a fix has to deal with

**`immediateFlush` is per appender, `bufferSize` is per manager.** Passing
`isImmediateFlush()` to `super(...)` fixes `[A]` and `[B]` and is confined to
the appender instance. `bufferSize` is not: the manager is keyed by

```java
final String managerName = target.name() + '.' + follow + '.' + direct;
```

which does not include the buffer size. Two `Console` appenders on the same
target share one `OutputStreamManager`, so whichever is built first would win
and the second one's `bufferSize` would still be silently discarded. Honouring
`bufferSize` needs the key widened, or the attribute documented as
manager-wide, or removed from the Console table.

**The default must stay `true`.** `createDefaultAppenderForLayout` builds the
`DefaultConfiguration` console appender directly through the constructor;
whatever the fix, that path has to keep flushing on every event or a program
that dies without a clean shutdown loses its last output.

**The attribute is worth having.** With `immediateFlush` forced to `false` reflectively
(`output/bench-2.26.1.txt`, 500k events, stdout to `/dev/null`, Log4j 2.26.1):

| appender | immediateFlush | events/s |
|---|---|---:|
| `Console` | `true` (today) | 996,145 |
| `Console` | `false` | 2,154,528 |
| `Console direct="true"` | `true` (today) | 988,206 |
| `Console direct="true"` | `false` | 2,338,731 |
| `File` (`/dev/null`) | `false` | 2,444,568 |

The `direct` attribute is documented as bypassing `System.out` buffering and giving "performance
comparable to a file appender". It cannot: the forced flush caps it at 2.4x slower than the file
appender it is compared to. Honouring the configured value closes that gap.

**Honouring it would be a visible behaviour change.** Java's `System.out` is a
`PrintStream` constructed with `autoFlush=true`, so today every event reaches
the terminal as it is logged. Making the Log4j buffer effective would hold up
to 8192 bytes back from an interactive program until it filled or the JVM shut
down. That is an argument for documenting the attribute as fixed rather than
making it work — but it is a decision, and right now neither outcome is what
the code does.
