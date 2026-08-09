# Triage of the ten most recent PRs on `apache/logging-log4j2`

Read on 2026-08-08. Nothing here has been posted anywhere. Each row links to a
file with the full reasoning and a paste-ready comment at the bottom.

## Scope of this review — read this first

The first pass was `gh pr list --limit 10`, which is the newest **open** PRs —
not "the last two weeks". The real window since 2026-07-25 holds **24 PRs**.
Three more have since been reviewed (#4226, #4227, #4224), bringing this file to
**thirteen**.

`SebTardif`'s four open PRs (#4185, #4217, #4218, #4220) are now covered too, so
this file spans **seventeen PRs** — every non-maintainer, non-bot PR in the
window. Seb is **not part of the pattern**: 3 merged (#4125, #4127, #4172), and
you have already driven three of the four yourself.

## The important corrective: zero-merged ≠ profile building

The three added in the second pass break the pattern, and that matters more than
the pattern does:

- **#4226** is the **best-diagnosed PR in the entire batch** — a correct
  root-cause analysis of an unsatisfiable loop exit condition, with a stack
  sample from a real affected startup. Measured here at ~3 s of CPU per appender.
  Nobody produces that from trawling the issue feed.
- **#4227** opens with *"This changes the names of direct write files … it is
  user visible on upgrade, so it deserves a decision rather than a rubber
  stamp."* That is a contributor flagging their own blast radius.
- **#4224** is a genuine good-first-issue on a maintainer-filed typo (#3369) —
  and you already reviewed it and pushed to the branch.

So the screen to apply is **not** "has this account merged anything before". It
is: *did the PR come from hitting the bug, or from finding the issue?* #4226 and
#4227 are plainly the former. `arimu1`'s two are plainly the latter.

## The pattern you noticed is real, and it is measurable

Counted over the full 24-PR window rather than this file's ten: **seven distinct
accounts with zero merged contributions opened ten PRs in twelve days** —
`arimu1`, `kalayciburak`, `jmestwa-coder`, `katstack`, `DragonFSKY`,
`tupelo-schneck`, `Hashim1999164`.

## What the closed PRs say about the bar

Worth checking any of these ten against before commenting:

- **#4213** (yours) — closed because `vy` pointed out dependabot #4206 already
  covered it. *Duplicate of existing work* is a live rejection reason here.
- **#4219** (`SebTardif`) — closed by you, folded into #4185 as the same
  `instantiateByClassName` pattern. *Should have been one PR* is another.

Both apply directly to #4234, which adds a 2.x-only public class in an area
#2921 already reworked on `main`.

The sharpest case is `arimu1`:

| | |
|---|---|
| Issue [#4241](https://github.com/apache/logging-log4j2/issues/4241) filed by **you** | 2026-08-05 07:12Z |
| Issue [#4243](https://github.com/apache/logging-log4j2/issues/4243) filed by **you** | 2026-08-05 18:53Z |
| PR #4245 "fixes #4243" | 2026-08-06 01:49Z — **6h 56m** after you filed it |
| PR #4246 "fixes #4241" | 2026-08-06 01:59Z — **18h 46m** after you filed it |

Two PRs, ten minutes apart, against two unrelated issues you had filed the day
before, from an account with no prior history in the repo. That is issue-farming
behaviour: watching the new-issue feed and claiming freshly-filed, well-specified
bugs. Your issues were good bug reports — they contained the diagnosis, the file,
and the line — so they were cheap to convert into a PR.

**This does not by itself make the PRs wrong.** #4245 is close to correct. But it
does mean the burden of review sits entirely on you, and that "the tests pass" is
not evidence of anything, because you wrote the specification they are testing.

The second pattern is `jmestwa-coder`: three open PRs
([#4198](https://github.com/apache/logging-log4j2/pull/4198),
[#4229](https://github.com/apache/logging-log4j2/pull/4229),
[#4235](https://github.com/apache/logging-log4j2/pull/4235)), all
security-shaped, none linked to an issue, none routed through
`security@apache.org`. That is a security-researcher profile being assembled in
public. Of the three, #4235 is the one I would actually take.

## The table

| PR | Author | Prior merged | Verdict | One line |
|---|---|:---:|:---:|---|
| [#4246](4246-database-manager-failed-startup.md) | `arimu1` | 0 | ⚠️ **needs changes** | Real bug (yours), but the `write()` early-return silently swallows the `AppenderLoggingException` NoSQL already threw — breaks `ignoreExceptions="false"` |
| [#4245](4245-csv-parameter-layout-npe.md) | `arimu1` | 0 | ✅ **take, with one question** | Correct one-line fix for your #4243. Open question is whether an empty CSV record is the wanted output, or the event should be dropped |
| [#4240](4240-4230-4232-established-authors.md) | `vpelikh` | 3 | ✅ **normal work** | Established contributor, 3 merged ports. `main` port of an already-reviewed 2.x change |
| [#4239](4239-syslog-javadoc.md) | `kalayciburak` | 0 | ✅ **take** | Javadoc-only, fixes `ppkarwasz`'s own #4237. Smallest possible PR, but the maintainer asked for exactly this |
| [#4235](4235-rfc5424-sd-id-msgid.md) | `jmestwa-coder` | 0 | ✅ **strongest in the batch** | Finishes the sanitisation #4073 started. Genuine record-injection hole, correct fix, real tests |
| [#4234](4234-zstd-compress-action.md) | `katstack` | 0 | 🛑 **regression — do not merge as-is** | `compressionLevel="0"` is documented and legal today; after this PR it throws `IllegalArgumentException` **at rollover time** and the rollover fails |
| [#4232](4240-4230-4232-established-authors.md) | dependabot | bot | ✅ **routine** | Version bumps |
| [#4230](4240-4230-4232-established-authors.md) | `vpelikh` | 3 | ✅ **normal work** | 6622-line docs revamp, established author |
| [#4229](4229-posix-symlink.md) | `jmestwa-coder` | 0 | ⚠️ **hardening, not a vuln** | Attacker already needs write access to your log directory. Worth taking as hardening; should not be framed as a security fix |
| [#4228](4228-plugin-processor-chatty.md) | `DragonFSKY` | 0 | ⚠️ **does not fix the reported issue** | #4225 asks for *less chatty* builds; this leaves the default at `NOTE`, so the build is exactly as chatty as before |
| [#4227](4227-directwrite-cron-naming.md) | `tupelo-schneck` | 0 | ✅ **sound; needs a compat call** | Verified the `LOG4J2-3339` removal does *not* regress non-cron appenders. Author flagged the user-visible naming change themselves |
| [#4226](4226-cron-startup-delay.md) | `tupelo-schneck` | 0 | ✅ **best PR in the batch** | **Measured: 2.996 s + 2.756 s vs 0.000118 s for the control.** ~3 s of CPU per appender on every startup |
| [#4224](4224-rootlogger-withfilter.md) | `Hashim1999164` | 0 | ✅ **you already handled it** | Real gap: `RootLogger.Builder` has `withtFilter` but no `withFilter`. You reviewed and pushed to the branch |
| [#4220](4220-instant-pattern-precision.md) | `SebTardif` | 3 | ⚠️ **wrong approach, per the issue author** | `vy` filed #3816 stating a preference **against** pattern conversion; this PR converts. **Nobody has reviewed it** |
| [#4218](4218-loggercontextadmin-stream-leak.md) | `SebTardif` | 3 | ✅ **fix verified; test will flake** | Your CHANGES_REQUESTED was answered. FD-counting assertion is process-wide and vacuous on Windows |
| [#4185](4185-4217-seb-approved-and-docs.md) | `SebTardif` | 3 | ⚠️ **your approval is stale** | You approved, *then* the author folded #4219's `setLayout` check in. New code, no review covering it |
| [#4217](4185-4217-seb-approved-and-docs.md) | `SebTardif` | 3 | ✅ **docs; your pushback fixed it** | Only the `xref`-renders check remains |

## Suggested order of attention

0. **#4185** — a stale approval on a branch that grew. Whoever merges will read
   your green check as covering code it does not. Cheapest fix in the batch.
1. **#4234** — the only one that would break working user configs. Block it.
2. **#4246** — real bug, but the fix trades a loud failure for a silent one.
3. **#4228** — send it back to the issue; the design question is unanswered.
4. **#4235** — read it properly, it is the best PR here.
5. #4245, #4239 — small, take them.
6. #4229 — take as hardening, relabel.
7. #4240, #4230, #4232 — normal.

## Repro status

| PR | Repro | State |
|---|---|---|
| **#4226 / #4227** | `configs/xml/repro-cron-directwrite.xml` → `repros/pr-4226/` | ✅ **measured on 2.26.1.** 2.996 s + 2.756 s vs 0.000118 s for the control appender. #4227's naming half **cannot be shown today** — it is Sunday, so period start == today |
| **#4235** | `repros/pr-4235/` — standalone zip, verified from a clean extract | ✅ **injection reproduced** on 2.24.1 / 2.25.5 / 2.26.0 / 2.26.1. Two events produce three syslog records; record 3 is a forged `<13>1` the program never logged |
| **#4234** | `configs/xml/repro-zstd-level.xml` → `repros/pr-4234/` | ✅ **baseline run.** `compressionLevel="0"` + `.zst` PASSes on all four releases, archives verified with `zstd -t`. After-half needs `--install` |
| #4245 | `repros/issue-4243/` | ✅ already built by you — fails on 2.24.1, 2.26.1 |
| #4229 | `repros/pr-4229/` — `setup.sh` (plant/check/clean) + existing `rollover-advanced.xml` | 📝 script written and exercised; the bench run itself is yours to make |
| #4246 | `repros/pr-4246/` + `configs/xml/repro-jpa-failed-startup.xml` | 📝 manual steps written. Uses JPA, so **no database service needed** |
| #4239, #4240, #4230, #4232 | — | n/a |
| #4228 | build-output observation, not a runtime repro | n/a |

Every repro takes its baseline against **releases** before any `--install`, so
installing a PR branch afterwards is safe. Each README ends with the restore step
— until that `mvn install -DskipTests` runs, `2.27.0-SNAPSHOT` *is* the PR branch
and every later bench run silently tests it.

Correction to an earlier draft of these notes: I claimed the bench reached
neither `FileExtension.ZSTD`, `PosixViewAttribute`, nor RFC5424. **All three are
covered** — `rollover-full.xml` has a Zstd appender, `rollover-advanced.xml` and
`appender-file-variants.xml` have `PosixViewAttribute`, and `layout-remaining.xml`
and `appender-network.xml` have Syslog/RFC5424. Nothing belongs in
`docs/GAPS.md` from this review.

## Verifying any of this on the bench

Every finding below was derived by reading the diff against
`~/apache/logging-log4j2` at `04c93c1d33`. To confirm one on a running app:

```bash
./bench pr <n> --diff                       # re-read the patch
./bench pr <n> --checkout --install         # publishes the PR as 2.27.0-SNAPSHOT
```

**Take the baseline against a release first** — `--install` overwrites
`2.27.0-SNAPSHOT`, so a baseline measured afterwards measures the PR twice.
