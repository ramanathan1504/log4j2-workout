# PR #4264 — Log4j 1 bridge warnings link to the staging site

https://github.com/apache/logging-log4j2/pull/4264

The PR is mostly documentation and Javadoc. Its one runtime change is the text of the warning that `DOMConfigurator` and `PropertyConfigurator` log through `LogLog` when they are called without `log4j1.compatibility`: the link moved from `logging.staged.apache.org` to `logging.apache.org`. `Repro` calls both configurators with no Log4j 1 compatibility; `run.sh` captures StatusLogger output at `WARN` and extracts the URL from each warning.

## Commands

```bash
cd log4j-pr-4264-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `DOMConfigurator` warning link | `https://logging.apache.org/...` | `https://logging.staged.apache.org/log4j/2.x/migrate-from-log4j1.html#log4j1.compatibility` | FAIL |
| 2.26.1 | `PropertyConfigurator` warning link | `https://logging.apache.org/...` | `https://logging.staged.apache.org/...` | FAIL |
| 2.x HEAD ea28f5a78b | `DOMConfigurator` warning link | `https://logging.apache.org/...` | `https://logging.apache.org/log4j/2.x/migrate-from-log4j1.html#log4j1.compatibility` | PASS |
| 2.x HEAD ea28f5a78b | `PropertyConfigurator` warning link | `https://logging.apache.org/...` | `https://logging.apache.org/...` | PASS |

Notes: the documentation and Javadoc parts of the PR have no runtime effect and are not covered.
