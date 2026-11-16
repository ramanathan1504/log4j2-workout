# PR #4267 — custom e-mail headers for the SMTP appender

https://github.com/apache/logging-log4j2/pull/4267

The PR adds a `Property[] headers` plugin element to `SmtpAppender.Builder` (issue #3704). Each value is compiled into a
`PatternLayout` serializer and rendered against the triggering event in `sendMultipartMessage(..., appendEvent)` of both the
`javax.mail` `SmtpManager` (log4j-core) and the Jakarta one (log4j-jakarta-smtp). Values have control characters replaced by
spaces, are MIME-encoded and folded; names that are not RFC 5322 field names are dropped with a StatusLogger `ERROR`; a repeated
name adds the header twice. Before the PR, nested `Property` elements were accepted silently and had no effect on the message.

The repro runs an in-JVM SMTP server on an ephemeral port, configures `SMTP` with six nested `Property` elements
(`X-Static`, `X-Trace-Id=%X{traceId}`, `X-Tag` twice, `X-Message=%m`, `X:Invalid`), logs one `ERROR` whose message is
`safe\r\nX-Evil: injected`, and parses the headers of the raw message it received (`output/<label>/message.eml`).

## Commands

```bash
log4j-pr-4267-repro/run.sh 2.26.1 2.26.1
log4j-pr-4267-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4267 pr-head-e2dabc33c0
MAIL_PROFILE=jakarta log4j-pr-4267-repro/run.sh 2.26.1 2.26.1-jakarta
MAIL_PROFILE=jakarta log4j-pr-4267-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4267 pr-head-e2dabc33c0-jakarta
```

The default profile puts `com.sun.mail:javax.mail:1.6.2` on the classpath (manager `o.a.l.l.core.net.SmtpManager`);
`-Pjakarta` replaces it with `log4j-jakarta-smtp` (manager `o.a.l.l.smtp.SmtpManager`, printed in each result).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 (javax) | `X-Static` | `[fixed-value]` | `[]` | FAIL |
| 2.26.1 (javax) | `X-Trace-Id` from `%X{traceId}` | `[TraceValue1]` | `[]` | FAIL |
| 2.26.1 (javax) | `X-Tag` repeated | `[first, second]` | `[]` | FAIL |
| 2.26.1 (javax) | `X-Message` from `%m` with CRLF | `[safe  X-Evil: injected]` | `[]` | FAIL |
| 2.26.1 (javax) | no `X-Evil` / `X` header | `[]` | `[]` | PASS |
| PR head `e2dabc33c0` (javax) | `X-Static` | `[fixed-value]` | `[fixed-value]` | PASS |
| PR head `e2dabc33c0` (javax) | `X-Trace-Id` | `[TraceValue1]` | `[TraceValue1]` | PASS |
| PR head `e2dabc33c0` (javax) | `X-Tag` repeated | `[first, second]` | `[first, second]` | PASS |
| PR head `e2dabc33c0` (javax) | `X-Message` with CRLF | `[safe  X-Evil: injected]` | `[safe  X-Evil: injected]` | PASS |
| PR head `e2dabc33c0` (javax) | no `X-Evil` / `X` header; `X:Invalid` rejected | `[]`, status ERROR | `[]`, `ignores the header with the invalid name 'X:Invalid'` | PASS |
| 2.26.1 (jakarta) | all four headers | present | `[]` for each | FAIL |
| PR head `e2dabc33c0` (jakarta) | all six cases | as above | identical to the javax column | PASS |

Raw: `output/<label>/result.txt`, `output/<label>/message.eml`, `output/<label>/status.txt`.

Notes: having `javax.mail` and `log4j-jakarta-smtp` on one classpath makes the Jakarta manager fail with
`jakarta.mail.NoSuchProviderException: smtp` on 2.26.1 and the PR head alike, so the profiles keep them apart; that is pre-existing
and not caused by the PR. MIME encoding/folding of non-ASCII or over-long values was not exercised.
