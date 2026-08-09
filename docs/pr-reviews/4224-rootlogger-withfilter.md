# PR #4224 — Add missing `RootLogger.Builder withFilter` alias

https://github.com/apache/logging-log4j2/pull/4224

| | |
|---|---|
| Author | `Hashim1999164` |
| Prior merged PRs in this repo | **0** |
| Base | `2.x` |
| Size | +38 −1, 4 files |
| Linked issue | **#3369** |
| CI | green |
| **You already reviewed this** | commented, and pushed a Bnd + changelog commit to their branch |

## Verdict: ✅ already handled by you. Nothing to add but a confirmation.

## Is it really needed?

Yes, and the claim is exactly accurate. `LoggerConfig.java`:

| Builder | `withtFilter` (typo) | `withFilter` (alias) |
|---|---|---|
| `LoggerConfig.Builder` | line 321 | **line 327** ✅ |
| `LoggerConfig.RootLogger.Builder` | line 1135 | **absent** ❌ |

So the outer builder got its corrected alias in 2.25.0 and the nested
`RootLogger.Builder` did not. Anyone building a root logger programmatically
still has to type the typo. That is #3369, filed long before this contributor
arrived — a real good-first-issue, not an invented one.

Both are deprecated wrappers around `setFilter`, so the addition is a
compatibility alias, not new API surface.

## This is the on-ramp working

Of the ten zero-merged accounts in this window, this is the one where the process
went the way it is supposed to: an old, maintainer-acknowledged typo issue, a
small correct patch, and a maintainer (you) who reviewed it, ran the tests,
pushed the missing Bnd and changelog bits, and gave the contributor a checklist
for next time. Their reply took the feedback.

Nothing here needs the scepticism the rest of the batch needs.

## The one thing left open

Your own comment flags it:

> Public APIs: If you touch a public API, remember to bump the package version in
> `package-info.java`

and the PR does touch `log4j-core/src/main/java/org/apache/logging/log4j/core/config/package-info.java`,
so that appears handled — but it is worth confirming the bump matches what Bnd
expects for **adding** a method to an exported package (a minor bump, not a
micro), since that is the kind of thing that fails late in a release rather than
in PR CI.

The checklist item you gave them about `./mvnw spotless:apply` is also worth
verifying landed, since a spotless failure would show up in CI and CI is green —
so it presumably did.

## Repro

None applicable — this is a missing method, not a behaviour. `LoggerConfigTest
.testRootLoggerBuilderWithFilterAlias` covers it, and the absence is verifiable by
reading `LoggerConfig.java:1132-1136` and noting there is no `withFilter`
sibling.

No bench cell applies: the bench exercises configuration files, and this is a
programmatic-builder API. The `programmatic` scenario builds configurations in
code via `ConfigurationBuilder`, which is a different API than
`LoggerConfig.RootLogger.Builder`.

---

## ── paste-ready comment ──

*(You have already commented on this PR and pushed to the branch, so this is a
close-out note rather than a review.)*

> Confirmed the gap is exactly as described: `LoggerConfig.Builder` has both
> `withtFilter` (line 321) and the corrected `withFilter` (line 327), while
> `RootLogger.Builder` has only `withtFilter` (line 1135). Both are deprecated
> wrappers around `setFilter`, so this is a compatibility alias rather than new
> API surface, and it closes #3369.
>
> One last thing before merge: since this adds a method to an exported package,
> please double-check the `package-info.java` bump is a **minor** version
> increment rather than a micro — Bnd baseline failures on that tend to surface at
> release time rather than in PR CI.
