# Reviewing a contributor pull request

[`BY-HAND.md`](BY-HAND.md) covers *running* a PR: which app reaches the module,
which config exercises it, how to sweep it. This covers **judging** one — deciding
whether it should be merged at all, and following it afterwards until it is.

The two halves matter in this order. A PR that reproduces perfectly and fixes the
wrong problem is still a no.

```
select → judge → verify → post → follow up
  ↑                                    │
  └──────── author pushes ─────────────┘
```

---

## 1. Select — know what you are looking at, and what you are not

```bash
gh pr list -R apache/logging-log4j2 --limit 10
```

**This is newest-`OPEN`.** It silently excludes everything closed or merged in
the same window, and `--limit` truncates by count, not by date. Asking "what
arrived in the last two weeks" and answering with this returns a different set,
and there is nothing in the output that says so. That mistake is what made an
early pass here cover ten PRs when the window held twenty-four.

Ask the question you actually mean:

```bash
gh pr list -R apache/logging-log4j2 --state all --limit 60 \
  --search "created:>=2026-07-25" \
  --json number,title,author,state,createdAt \
  --jq 'sort_by(.createdAt)|reverse|.[]|"\(.number)\t\(.state)\t\(.createdAt[0:10])\t\(.author.login)\t\(.title[0:58])"'
```

The closed ones are not noise — they are the clearest statement of the bar. Two
closures in one recent window: a duplicate of an existing dependabot PR, and a
change that belonged on an existing branch rather than its own. Both are live
rejection reasons to check a new PR against before commenting.

---

## 2. Judge — three questions, in this order

### a. Who filed the linked issue, and when?

```bash
./bench pr 4246                      # the PR
gh issue view 4241 -R apache/logging-log4j2 \
  --json number,author,createdAt,labels --jq '"\(.number) by \(.author.login) at \(.createdAt)"'
```

The gap between the issue and the PR is informative. Hours, on an issue you filed
yourself, from an account with no history, means the PR was written *from your
issue text* — so **its passing tests prove only that the author read your issue
carefully.** They assert the specification you wrote. That is not corroboration.

The reverse also happens: an issue filed by a PMC member often already states a
preferred fix. Read the whole thread before reviewing the code, or you will
review an implementation the project has already declined.

### b. What is the author's history?

```bash
gh pr list -R apache/logging-log4j2 --author "$LOGIN" --state all --limit 30 \
  --json number,state,createdAt,title --jq '.[]|"\(.number) \(.state) \(.createdAt[0:10]) \(.title[0:60])"'
```

Useful, and **routinely misread**. Zero merged PRs does not mean low value. The
best-diagnosed change in one recent batch — a correct root-cause analysis of an
unsatisfiable loop condition, with a stack sample from a real affected startup —
came from an account with no merged work at all.

The screen that actually separates them:

> **Did this come from hitting the bug, or from finding the issue?**

Hitting it looks like: a stack sample, a production symptom, a number they
measured, an unprompted note about their own change's blast radius. Finding it
looks like: an issue filed hours earlier by someone else, restated as a diff.

### c. Does the fix match the bug, or overshoot it?

The most common real defect in an otherwise good PR. Check every implementation
of the thing being changed, not only the one that motivated it:

```bash
cd ~/apache/logging-log4j2
grep -rn "shutdownInternal" --include="*.java" . | grep -v "/test/"
```

One recent PR fixed a per-event NPE by making a base class drop events when not
running. Reading all four subclasses showed the NPE existed in exactly one of
them — the other three already guarded and threw a typed exception that
`ignoreExceptions="false"` depends on. The fix removed a working behaviour from
three managers to fix a defect in one.

Also worth asking on any behaviour change:

- Does it change something a **user's working config** depends on? An attribute
  documented as legal today that starts throwing is a blocker regardless of how
  correct the new validation is.
- Is it a **security fix, or hardening**? If exploiting it needs write access to
  the log directory, it is hardening — take it, but do not let the changelog read
  like an advisory. If untrusted input genuinely reaches it, it belongs at
  `security@apache.org` before a public PR with a proof of concept.

---

## 3. Verify — a clean exit proves nothing

### Start with the mechanical gates

```bash
./bench redgreen 4218
```

Four gates, in order, in a throwaway `git worktree` — your clone stays on `2.x`
and `~/.m2` is never overwritten, so this is safe to run before you have
baselined anything:

| Gate | Asks |
|---|---|
| BUILD | does the PR branch compile, and do its own tests pass |
| SPOTLESS | is it formatted the way the project enforces |
| **RED** | base + the PR's **test files only** → must **fail** |
| GREEN | base + tests + the PR's **main files** → must **pass** |

**RED is the one that earns its keep.** It automates the hand-check that caught
#4218: revert the fix, keep the tests, and see whether anything goes red. That
PR's first revision shipped three tests that all passed without the production
change — they asserted that a configuration loads, not that a stream closes.
Nothing but running it says so. On the current head the same gate reports
`Tests run: 4, Failures: 1`, which is the author's red-green claim confirmed
rather than taken on trust.

Read the RED verdict carefully, because it has four outcomes and only one of
them is "the tests are fine":

- **fail, as required** — an assertion failed. What you want.
- **compile error** — valid red, but weaker: the test pins an API's existence,
  not a behaviour. A test that only fails to compile would still pass if the
  method were reintroduced doing nothing.
- **PASS** — the tests do not test the fix. This is a blocking finding, and the
  comment to write is "can you make at least one of them fail without the
  production change?"
- **inconclusive** — the build broke before any test ran, so there is no
  evidence either way. Never read this as red.

That last outcome is why the gate insists on a surefire `Tests run: … Failures:`
line rather than trusting maven's exit code. An early version of the script ran
under JDK 22, Log4j's enforcer rejected it with `[17,18)` in `log4j-bom` before
compiling anything, and the non-zero exit reported a cheerful green tick on RED.
Same shape as the `commons-compress` repro below that passed on four versions
having compressed nothing: **the failure you are looking for and an unrelated
failure exit the same way.**

The gates are necessary, not sufficient. All four green means the change is
mechanically sound, and says nothing about whether it fixes the right thing —
that is §2, and no script gets there. The script prints the §2 checklist when it
finishes for exactly that reason.

### Then reproduce it here

Reproduce against **releases first**. `--install` overwrites `2.27.0-SNAPSHOT`,
so a baseline taken afterwards measures the PR twice.

```bash
./bench run core-java --config xml/<cfg> --log4j 2.26.1 <scenario>
./bench repro <n> --pr --config xml/<cfg> --scenario <s> \
  --log4j 2.24.1 --log4j 2.25.5 --log4j 2.26.0 --log4j 2.26.1
```

Then, and only then:

```bash
./bench pr <n> --checkout --install
./bench run core-java --config xml/<cfg> <scenario>
cd ~/apache/logging-log4j2 && git switch 2.x && mvn install -DskipTests   # restore
```

That last line is not optional. Until it runs, `2.27.0-SNAPSHOT` *is* the PR
branch and every later bench run silently tests it.

### Build a control into the config

The single highest-value habit here. A config with two appenders differing in
**one** attribute turns "it seems slow" into a measurement:

| Appender | `fileName` | `initialize()` |
|---|---|---:|
| `DirectCronA` | none | 2.996 s |
| `CronWithFileName` | set | 0.000118 s |

The control is what makes the number mean something, and it is what catches an
over-reaching fix that speeds up the broken case by breaking the working one.

### Confirm the evidence is real

Log4j catches appender exceptions, reports them through `StatusLogger`, and exits
0. Worse, some failures surface as a **WARN from a rollover thread**, which a
"no StatusLogger error" check reads as a pass.

That is not hypothetical — `scripts/repro.sh` once shipped `commons-compress`
without the codec backend it dispatches to, so a `.zst` reproduction built
cleanly, exited 0, reported **PASS on four versions**, and had compressed
nothing. Always confirm the artefact:

```bash
BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' ./bench run ...
zstd -t logs/<cfg>/<dir>/app-1.log.zst      # the file, not the exit code
unzip -t logs/<cfg>/<dir>/app-1.log.zip
```

Ask of every green result: *what would this look like if it were wrong?* If the
answer is "the same", it is not evidence yet.

---

## 4. Write it up, then post

One file per PR under [`pr-reviews/`](pr-reviews/), ending in a paste-ready
comment. Draft first, post second — the same discipline as
[`issue-drafts/`](issue-drafts/), for the same reason: a comment you cannot
re-read before sending is one you cannot check.

Say what you verified and how, name file and line, and separate *blocking* from
*non-blocking* so the author knows what actually gates the merge. Then:

The paste-ready block is the section under `── paste-ready comment ──`, and it
is **not** the whole file — everything above it is notes to yourself. Write that
block as plain markdown with no `>` prefix, or it posts as a blockquote and reads
as though you were quoting someone else.

`--comment` extracts exactly that block, so the rest cannot leak:

```bash
./bench followup --comment <n>                        # read it first
./bench followup --comment <n> | gh pr comment <n> -R apache/logging-log4j2 --body-file -
```

Never inline a heredoc you cannot re-read. Always pass `-R` — omitting it targets
*this* repository.

Record it, and mark it posted:

```bash
./bench followup --sync <n>      # after posting, or after any re-read
```

---

## 5. Follow up — the half that usually goes missing

A review is not finished when the comment is posted. The author pushes a fix, a
maintainer replies, CI turns red — and none of that reaches you unless you go
looking. `./bench pr <n>` cannot help: it is a snapshot, with nothing to compare
against.

[`pr-reviews/ledger.tsv`](pr-reviews/ledger.tsv) records the head SHA each PR was
at when you reviewed it. Everything below is a diff against that.

```bash
./bench followup                 # every reviewed PR, one line each
./bench followup --changed       # only the ones that moved
./bench followup --mine          # only where the last word is not yours
./bench followup 4234            # one PR, in full
```

```
  4185  stale-approval SebTardif        pushed,reply:SebTardif
  4234  blocked        katstack         —
```

The badges answer the questions a second visit is actually asking:

| Badge | Means |
|---|---|
| `pushed` | head moved — **your verdict describes code that is no longer there** |
| `reply:<who>` | someone else spoke last; a maintainer may have decided something |
| `merged` / `closed` | it resolved; if you had a blocking finding, check it was addressed |
| `ci-fail` | red, which may be your finding reproducing in their own CI |

When something moved, re-read before trusting the file:

```bash
./bench pr 4185 --diff
./bench followup --sync 4185     # only once you actually have
```

**`--sync` is deliberately manual.** If it ran automatically it would erase the
one signal it exists to show. A ledger rewritten to silence `followup` is a
ledger that has stopped tracking anything.

### The case this exists for

Approving a PR, and the branch growing afterwards. GitHub keeps showing your
green check while the head advances, so whoever merges reads it as covering code
no review ever saw. `followup` reports that as `pushed` against a
`stale-approval` verdict — the one state where the tooling disagrees with
GitHub's own UI, and is right to.

---

## Quick reference

| Step | Command |
|---|---|
| The real window, not newest-open | `gh pr list --state all --search "created:>=<date>"` |
| Read a PR | `./bench pr <n>` · `--diff` · `--files` |
| Who filed the issue | `gh issue view <n> -R apache/logging-log4j2 --json author,createdAt` |
| Author history | `gh pr list --author <login> --state all` |
| Baseline, against a release | `./bench run <app> --config <cfg> --log4j 2.26.1 <scenario>` |
| Standalone repro | `./bench repro <n> --pr --config <cfg> --scenario <s> --log4j …` |
| Install the branch | `./bench pr <n> --checkout --install` |
| Restore afterwards | `git switch 2.x && mvn install -DskipTests` |
| Read just the comment | `./bench followup --comment <n>` |
| Post it | `./bench followup --comment <n> \| gh pr comment <n> -R apache/logging-log4j2 --body-file -` |
| What moved since | `./bench followup [--changed\|--mine] [<n>]` |
| Re-record after re-reading | `./bench followup --sync <n>` |

Related: [`BY-HAND.md`](BY-HAND.md) · [`GH-COMMANDS.md`](GH-COMMANDS.md) ·
[`pr-reviews/`](pr-reviews/) · [`HANDOVER.md`](HANDOVER.md)
