# PR #4456 — RFC 5424 TLS frame length counted in UTF-8 instead of the layout charset

https://github.com/apache/logging-log4j2/pull/4456

`Rfc5424Layout` with `useTlsMessageFormat="true"` wraps each record in `TlsSyslogFrame`, which prefixed `MSG-LEN` computed with `getBytes(UTF_8)`, while `AbstractStringLayout` encodes the framed string with the layout `charset`. For a non-UTF-8 charset and non-ASCII text, the advertised octet count exceeds the bytes sent, so an RFC 5425 receiver reads into the next frame. The fix adds `TlsSyslogFrame(String, Charset)` and passes `getCharset()`. The repro writes one framed event (`café naïve à la carte`, 3 non-ASCII chars) through two `File` appenders, ISO-8859-1 and UTF-8, and compares the parsed `MSG-LEN` with the bytes after the space.

## Commands

```bash
cd log4j-pr-4456-repro
./run.sh 2.26.1 2.26.1
./run.sh <dir with log4j-api and log4j-core jars built from 2.x> 2.x-HEAD-ea28f5a78b
```

The second run resolves third-party dependencies at 2.26.1 and replaces every `log4j-*` jar with the `2.27.0-SNAPSHOT` jar built from `2.x` HEAD `ea28f5a78b`, which contains this change.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | ISO-8859-1 | MSG-LEN = payload bytes | MSG-LEN=87, payload=84 | FAIL |
| 2.26.1 | UTF-8 (control) | MSG-LEN = payload bytes | 87 / 87 | PASS |
| 2.x HEAD ea28f5a78b | ISO-8859-1 | MSG-LEN = payload bytes | 84 / 84 | PASS |
| 2.x HEAD ea28f5a78b | UTF-8 (control) | MSG-LEN = payload bytes | 87 / 87 | PASS |

Rerun 2026-11-17 after the merge (`c058e9baab`), jars built with `-pl log4j-core-test -am package` in a worktree at `origin/2.x` `ea28f5a78b`: same results, 2.26.1 FAIL 87/84, 2.x PASS 84/84.
