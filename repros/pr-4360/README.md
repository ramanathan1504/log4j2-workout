# PR #4360 — HTTP appender loses the whole event when one evaluated header value is invalid

https://github.com/apache/logging-log4j2/pull/4360

`HttpURLConnectionManager.send` evaluates each `<Property>` header per event and calls
`HttpURLConnection.setRequestProperty`. The JDK rejects a CR or LF in a header value with
`IllegalArgumentException("Illegal character(s) in message header value")`; before the fix that escaped `send`,
`HttpAppender.append` reported `Unable to send HTTP in appender` and the event was dropped. The fix catches the
exception per header, logs a `WARN` through the status logger, skips that header and sends the event.

## Commands

```bash
cd log4j-pr-4360-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`log4j2.xml`: `Http` appender to an in-JVM `com.sun.net.httpserver` on a random port, headers
`X-Tenant=$${ctx:tenant}` and `X-Static=static-ok`. Three events, tenant `acme`, `evil\r\nX-Injected: 1`, `acme`.
The server records each body and its `X-Tenant`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control event-1 (`acme`) | received | received, X-Tenant=acme | PASS |
| 2.26.1 | event-2, CRLF in X-Tenant | received, header skipped | not received; `ERROR Unable to send HTTP in appender [Http]` | FAIL |
| 2.26.1 | control event-3 (`acme`) | received | received, X-Tenant=acme | PASS |
| 2.x HEAD ea28f5a78b | control event-1 | received | received, X-Tenant=acme | PASS |
| 2.x HEAD ea28f5a78b | event-2, CRLF in X-Tenant | received, header skipped | received, X-Tenant absent; `WARN Skipping HTTP header X-Tenant because its value is invalid` | PASS |
| 2.x HEAD ea28f5a78b | control event-3 | received | received, X-Tenant=acme | PASS |

Requests received: 2 of 3 on 2.26.1, 3 of 3 on 2.x HEAD.
