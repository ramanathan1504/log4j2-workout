# PRs #4240, #4230, #4232 — the three that are not part of the pattern

These three are grouped because none of them raise the question you asked about.
Two are from an established contributor, one is a bot.

---

## PR #4240 — Port `MessageFactory`-namespaced logger registry from `2.x`

https://github.com/apache/logging-log4j2/pull/4240

| | |
|---|---|
| Author | `vpelikh` (Vasily Pelikh) |
| Prior merged PRs in this repo | **3** — #4157, #4154, #4152 |
| Base | `main` |
| Size | +20 −45, 2 files |
| CI | green |

**Verdict: ✅ normal contributor work.**

`vpelikh` has seven PRs here, three merged, all of them `2.x → main` ports of
changes that were already reviewed on `2.x`. That is sustained, unglamorous
maintenance work on the branch nobody volunteers for — the opposite of profile
building.

This one is a **net deletion** (+20 −45), which is itself a signal: it is
removing code on `main` in favour of the shape `2.x` already settled on. Their
earlier #4157 ported `InternalLoggerRegistry` (the #3418 / #3681 ports); this is
the follow-on.

Worth checking, since it is a port rather than a new idea:

- that the `2.x` original is actually merged and unchanged since — a port of a
  commit that was later amended on `2.x` is the usual way these go wrong;
- that no `main`-only behaviour is being dropped along with the deleted lines,
  since `main` diverged from `2.x` in this area.

Neither needs a bench run. `./bench pr 4240 --diff` against the `2.x` commit it
ports is the check.

---

## PR #4230 — `[main]` Revamp the Extending, Plugins, Architecture, and Programmatic configuration pages

https://github.com/apache/logging-log4j2/pull/4230

| | |
|---|---|
| Author | `vpelikh` |
| Base | `main` |
| Size | **+6622 −2272**, 32 files |
| CI | green |

**Verdict: ✅ normal work, but it is 6600 lines of prose.**

Documentation only, from the same established contributor. Nothing here touches
the version matrix, so the bench has no opinion on it.

The only thing worth saying: at this size, a review is a reading job, not a
reviewing job, and the failure mode is that it gets rubber-stamped. If you take
it on, the mechanical checks are the ones worth doing — that every `xref`
resolves to a page *and* an anchor, and that the site renders. That is the same
discipline `CLAUDE.md` records for this repo's own docs, and it is exactly the
thing that a 6600-line docs PR breaks silently.

---

## PR #4232 — Bump the maven-minor-updates group with 6 updates

https://github.com/apache/logging-log4j2/pull/4232

| | |
|---|---|
| Author | `dependabot[bot]` |
| Base | `2.x` |
| Size | +15 −15, 9 files |
| CI | green |

**Verdict: ✅ routine.**

One thing connects it to this batch: if **#4234** merges, its test deliberately
pins `ZstdConstants.ZSTD_CLEVEL_MAX` so that a commons-compress upgrade which
widens the range fails the build. That makes a future dependabot bump like this
one a red build. See [`4234-zstd-compress-action.md`](4234-zstd-compress-action.md).

---

## ── paste-ready comment (only for #4230, if you want one) ──

Thanks for taking this on — it is a lot of ground.

Given the size, could you confirm the site builds and that every `xref`
resolves to both a page and an anchor? Cross-reference breakage is the failure
mode that survives review on a change this large, and it does not show up in CI.
