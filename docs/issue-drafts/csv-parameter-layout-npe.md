# FILED — https://github.com/apache/logging-log4j2/issues/4243

**Title:** `CsvParameterLayout` throws NullPointerException on any event without parameters

---

## Description

`CsvParameterLayout.toSerializable` passes `Message.getParameters()` straight to
`CSVFormat.printRecord` without a null check. `getParameters()` returns `null`
for any message type that has none — `SimpleMessage`, and therefore every plain
`logger.info("some text")` call — so the layout throws a
`NullPointerException` for each such event.

`log4j-core/src/main/java/org/apache/logging/log4j/core/layout/CsvParameterLayout.java`, lines 93–104:

```java
public String toSerializable(final LogEvent event) {
    final Message message = event.getMessage();
    final Object[] parameters = message.getParameters();   // null for SimpleMessage
    final StringBuilder buffer = getStringBuilder();
    try {
        getFormat().printRecord(buffer, parameters);       // NPE here
        return buffer.toString();
    } catch (final IOException e) {                        // catches IOException only
        StatusLogger.getLogger().error(message, e);
        return getFormat().getCommentMarker() + " " + e;
    }
}
```

Only `IOException` is caught, so the NPE escapes into the appender. There it is
reported through the `StatusLogger` and the application continues, which means a
configuration using this layout produces one error per event and no output, while
the JVM still exits 0.

A parameterised call such as `logger.info("order {}", id)` works, so the failure
depends on which logging call is reached — a layout that appears to work in
testing can fail on the first plain-text message in production.

## Configuration

**Version:** reproduced on **2.26.1**. Present on both lines:

- 2.x `04c93c1d33` — `log4j-core/.../core/layout/CsvParameterLayout.java:98`
- 3.x `main` — `log4j-csv/.../csv/layout/CsvParameterLayout.java:104`

The 3.x copy moved into its own module and gained a recycler, but the null check
is still absent and the `catch` still handles only `IOException`.

**Operating system:** macOS 15 (Darwin 25.5.0)

**JDK:** Temurin 21

## Logs

Captured against Log4j **2.26.1** on JDK 21, not reconstructed:

```
──── messages ── All Message types: Simple, Parameterized, Formatted, MessageFormat, Object, Map, StructuredData, ThreadDump, Flow, Supplier
2026-08-05T18:07:16.257782Z main ERROR An exception occurred processing Appender CsvParams
java.lang.NullPointerException: Cannot read the array length because "values" is null
	at org.apache.commons.csv.CSVFormat.printRecord(CSVFormat.java:2265)
	at org.apache.logging.log4j.core.layout.CsvParameterLayout.toSerializable(CsvParameterLayout.java:98)
	at org.apache.logging.log4j.core.layout.CsvParameterLayout.toSerializable(CsvParameterLayout.java:49)
	at org.apache.logging.log4j.core.layout.AbstractStringLayout.toByteArray(AbstractStringLayout.java:295)
	at org.apache.logging.log4j.core.layout.AbstractLayout.encode(AbstractLayout.java:207)
	at org.apache.logging.log4j.core.layout.AbstractLayout.encode(AbstractLayout.java:36)
	at org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender.directEncodeEvent(AbstractOutputStreamAppender.java:227)
	at org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender.tryAppend(AbstractOutputStreamAppender.java:220)
	at org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender.append(AbstractOutputStreamAppender.java:211)
	at org.apache.logging.log4j.core.config.AppenderControl.tryCallAppender(AppenderControl.java:160)
	at org.apache.logging.log4j.core.config.AppenderControl.callAppender0(AppenderControl.java:133)
--
23:37:16.280 DEBUG o.a.l.b.s.MessageScenario - Supplier form — expensive value: computed-lazily
23:37:16.281 INFO  o.a.l.b.s.MessageScenario - Fluent API — LogBuilder with location capture
```

## ## Reproduction

```xml
<Configuration status="warn">
  <Appenders>
    <File name="Csv" fileName="target/params.csv">
      <CsvParameterLayout format="Default"/>
    </File>
  </Appenders>
  <Loggers>
    <Root level="info"><AppenderRef ref="Csv"/></Root>
  </Loggers>
</Configuration>
```

```java
logger.info("order {} accepted", 4711);   // fine: one parameter
logger.info("plain text");                // NPE, once, and the file gains nothing
```

## Suggested fix

Treat a null parameter array as an empty record:

```java
final Object[] parameters = message.getParameters();
getFormat().printRecord(buffer, parameters == null ? EMPTY : parameters);
```

An empty CSV row is at least well-formed, and matches what the layout already
does for a message whose parameter array is present but zero-length. Widening the
`catch` to `Exception` would stop the throw but leave the column count wrong,
which is worse for a format whose whole value is being machine-readable.

Whether a plain-text message should be silently dropped or rendered as an empty
row is a design call, but the current behaviour — throwing per event — is
unlikely to be the intended one.
