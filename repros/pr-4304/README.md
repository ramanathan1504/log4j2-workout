# PR #4304 — Deprecated `SmtpAppender.createAppender` dropped the name and every mail attribute

https://github.com/apache/logging-log4j2/pull/4304

## Mechanism

Since 2.18.0 the deprecated static factory built the appender through `newBuilder()` but forwarded only port, debug, buffer size, layout, filter and `ignoreExceptions`. Name, to/cc/bcc/from/replyTo/subject, protocol, host, username and password were dropped, so `AbstractAppender`'s `requireNonNull(name)` threw on every call. The PR adds the eleven missing setters. `Repro.java` runs an embedded SMTP server on a loopback ephemeral port, calls `createAppender(...)` with name `Mail`, to `to@example.org`, subject `Subject 4304`, appends one `ERROR` event and reads the SMTP transcript.

## Commands

```bash
cd log4j-pr-4304-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`com.sun.mail:javax.mail:1.6.2` is on the classpath. Outputs: `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `createAppender(...)` | appender named `Mail` | `NullPointerException: name` | FAIL |
| 2.26.1 | mail delivered | RCPT and Subject from arguments | no appender | FAIL |
| 2.x HEAD ea28f5a78b | `createAppender(...)` | appender named `Mail` | `name=Mail` | PASS |
| 2.x HEAD ea28f5a78b | mail delivered | RCPT and Subject from arguments | `RCPT TO:<to@example.org>`, `Subject: Subject 4304`, `MAIL FROM:<from@example.org>` | PASS |
