# PR #4119 — `RegexFilter`: `patternFlags` removed, builder added

https://github.com/apache/logging-log4j2/pull/4119

## Mechanism

On 2.26.0 `RegexFilter` is built by `@PluginFactory createFilter(regex, @PluginElement("PatternFlags") String[], …)`.
A `String[]` plugin element can never be populated from configuration, so `patternFlags` never worked from a
config file. The PR switches to a `@PluginBuilderFactory` builder without the attribute, keeps the deprecated
`createFilter` for binary compatibility, and makes it **ignore** its `patternFlags` argument. Inline flags such as
`(?s)` remain the supported way.

## Commands

```bash
cd log4j-pr-4119-repro
./run.sh 2.26.0 2.26.0
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2x-head-ea28f5a78b
```

Each case runs in its own JVM with `src/main/resources/<case>.xml`. Every config has `RegexFilter` on the root
logger in front of a `File` appender; the app logs `first\nline two`:
`inline` `regex="(?s).*line.*"` (control), `attr` `patternFlags="DOTALL"`, `element` `<PatternFlags>DOTALL</PatternFlags>`,
`noregex` no `regex`, and `api`, which calls `RegexFilter.createFilter(".*line.*", {"DOTALL"}, false, ACCEPT, DENY)` directly.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.0 | `inline` (control) | event kept | kept | PASS |
| 2.26.0 | `attr` | rejected | `ERROR … invalid element or attribute "patternFlags"`, event dropped | PASS |
| 2.26.0 | `element` | rejected | `ERROR … invalid element or attribute "PatternFlags"`, event dropped | PASS |
| 2.26.0 | `noregex` | error reported | `ERROR A regular expression must be provided for RegexFilter`, no filter, event kept | PASS |
| 2.26.0 | `api` `createFilter(…, {"DOTALL"})` | DENY (flags ignored, per PR) | ACCEPT (DOTALL honoured) | FAIL |
| 2.x HEAD ea28f5a78b | `inline` (control) | event kept | kept | PASS |
| 2.x HEAD ea28f5a78b | `attr` | rejected | same ERROR, event dropped | PASS |
| 2.x HEAD ea28f5a78b | `element` | rejected | same ERROR, event dropped | PASS |
| 2.x HEAD ea28f5a78b | `noregex` | error reported | `ERROR No \`regex\` provided for \`RegexFilter\`` + `Could not create plugin …`, event kept | PASS |
| 2.x HEAD ea28f5a78b | `api` `createFilter(…, {"DOTALL"})` | DENY (flags ignored, per PR) | DENY | PASS |

## Notes

- Configuration behaviour is unchanged: `patternFlags` is rejected from config on both sides. Only the wording of
  the missing-regex error changes.
- The one runtime difference is in the deprecated Java API: on 2.26.0 `createFilter` honoured `DOTALL`; on 2.x it
  silently ignores it, so a programmatic caller's filter now matches differently without any warning. The
  "FAIL" on 2.26.0 means "differs from the PR's contract", not a bug in 2.26.0. Worth a deprecation WARN or a
  changelog line naming the programmatic case.
