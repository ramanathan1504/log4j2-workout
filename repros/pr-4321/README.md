# PR #4321 — Improve the missing logging provider diagnostic

https://github.com/apache/logging-log4j2/pull/4321

When `ProviderUtil` finds no `Provider`, it logged only `Log4j API could not find a logging provider.` The PR adds two lines to the same status error: that Simple Logger is used, and a link to `manual/installation.html#impl`. The repro is an application with only `log4j-api` on the classpath; `run.sh` captures stderr and greps it.

## Commands

```bash
cd log4j-pr-4321-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `f07ea190ca`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: provider missing is reported | error line present | `ERROR Log4j API could not find a logging provider.` (2 stderr lines) | PASS |
| 2.26.1 | diagnostic names the fallback and the docs | link to `installation.html#impl` | no fallback line, no link | FAIL |
| 2.x HEAD ea28f5a78b | control: provider missing is reported | error line present | same error line (4 stderr lines) | PASS |
| 2.x HEAD ea28f5a78b | diagnostic names the fallback and the docs | link to `installation.html#impl` | `Log4j API will use Simple Logger by default.` + `https://logging.apache.org/log4j/2.x/manual/installation.html#impl` | PASS |

Both runs fall back to `org.apache.logging.log4j.simple.SimpleLogger`.
