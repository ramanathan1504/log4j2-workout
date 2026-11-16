# PR #4358 — Honor the legacy basic authentication encoding property

https://github.com/apache/logging-log4j2/pull/4358

`BasicAuthorizationProvider` read `log4j2.configurationAuthorizationEncoding` with `getCharsetProperty(name)`, whose default is the platform charset, so the result was never null and the fallback to the legacy `logging.auth.encoding` was dead; that fallback also discarded its return value. The fix reads the first property with a `null` default and assigns the legacy property's value, defaulting to UTF-8 (RFC 7617). The repro serves the configuration from an in-JVM `com.sun.net.httpserver` (`log4j2.configurationFile=http://127.0.0.1:<port>/log4j2.xml`, `allowedProtocols=http`), with user `user` and password `päss`, and decodes the `Authorization` header the server received to see which charset encoded `ä`.

## Commands

```bash
cd log4j-pr-4358-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `c0de1b8628`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `logging.auth.encoding=ISO-8859-1` | ISO-8859-1 | UTF-8, `Basic dXNlcjpww6Rzcw==` | FAIL |
| 2.26.1 | control: `log4j2.configurationAuthorizationEncoding=ISO-8859-1` | ISO-8859-1 | ISO-8859-1, `Basic dXNlcjpw5HNz` | PASS |
| 2.26.1 | control: no property, UTF-8 platform | UTF-8 | UTF-8 | PASS |
| 2.26.1 | no property, `-Dfile.encoding=ISO-8859-1` | UTF-8 | ISO-8859-1, `Basic dXNlcjpw5HNz` | FAIL |
| 2.x HEAD ea28f5a78b | `logging.auth.encoding=ISO-8859-1` | ISO-8859-1 | ISO-8859-1, `Basic dXNlcjpw5HNz` | PASS |
| 2.x HEAD ea28f5a78b | control: `log4j2.configurationAuthorizationEncoding=ISO-8859-1` | ISO-8859-1 | ISO-8859-1 | PASS |
| 2.x HEAD ea28f5a78b | control: no property, UTF-8 platform | UTF-8 | UTF-8 | PASS |
| 2.x HEAD ea28f5a78b | no property, `-Dfile.encoding=ISO-8859-1` | UTF-8 | UTF-8, `Basic dXNlcjpww6Rzcw==` | PASS |

Notes: the last row is a second behaviour change. The configuration was fetched and applied in every run (`config=remote`).
