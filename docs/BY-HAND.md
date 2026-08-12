# Working an issue or a PR by hand

Two playbooks, no assistant involved. Copy the commands, read the output, decide.

- **[A. Reviewing a pull request](#a-reviewing-a-pull-request)** — someone proposes a change to Log4j; does it do what it claims, and does it break anything else?
- **[B. Reproducing and fixing an issue](#b-reproducing-and-fixing-an-issue)** — someone reports a bug; does it reproduce, on which versions, and does your fix close it?

Both assume the one-time setup in [`../README.md`](../README.md#setup) is done:
JDK 17, Maven, `git config core.hooksPath .githooks`, and `./bench list` runs.
Both also stay in the terminal: `gh` reads the report and the diff, `./bench`
runs them, `gh` posts back. [`GH-COMMANDS.md`](GH-COMMANDS.md) is the full
command reference; the commands below are the subset each step needs.

Everything here writes to `repros/` and `logs/` only. Nothing in this repository
is ever pushed to an Apache project.

---

## The commands you need

```bash
oss issue <n> --repo apache/logging-log4j2     # read an upstream issue
./bench list                                   # apps, configs, versions, scenarios
./bench pr    <n> [--diff] [--checkout]        # read a PR, and put it on your classpath
./bench run  <app> --config <cfg> [scenario]   # one app, one config, one version
./bench matrix --apps <a> --configs <c> \
               --javas 17 --scenario <s>       # the same test across every version
./bench repro <number> [--pr] ...              # standalone zip to attach upstream
```

Reading an issue is one API call, so it lives in the core (`oss issue`) and works
against any repository. What stays here is what needs something to actually run.

`pr` is a thin wrapper over `gh` ([`../scripts/gh-pr.sh`](../scripts/gh-pr.sh)).
It defaults to `apache/logging-log4j2` and is read-only, except
`./bench pr --checkout`, which switches the **Log4j clone** to the PR branch and
leaves this repository alone.

Two rules that save the most time:

1. **Always pass `--scenario` to a `matrix` sweep.** Without it, every cell runs
   all seven scenarios and `rollover` writes 2000 lines per cell. Hours become a
   week.
2. **A clean exit proves nothing.** Log4j catches appender exceptions, reports
   them through `StatusLogger`, and lets the JVM exit 0. When a run looks fine
   but stored or sent less than you expected, turn the status logger up:

   ```bash
   BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' \
     ./bench run nosql --config xml/appender-nosql
   ```

---

## A. Reviewing a pull request

> This playbook covers **running** a PR — which app reaches the module, which
> config exercises it, how to sweep it. For **judging** one — whether it should
> be merged at all, whether the fix overshoots the bug, and how to follow it
> after the author pushes — see [`PR-REVIEW.md`](PR-REVIEW.md). Reviews already
> written are in [`pr-reviews/`](pr-reviews/); `./bench followup` says what has
> moved since.

### A1. Read the PR and decide what it touches

```bash
./bench pr 4240                    # metadata, files and modules touched, checks, reviews
./bench pr 4240 --diff             # the patch itself
```

From the diff, work out three things before running anything:

| Question | Where the answer lives |
|---|---|
| Which Log4j module? | the changed paths in the diff |
| Which app here puts that module on a classpath? | `./bench coverage` |
| Which config exercises it? | `./bench list --configs`, or `docs/FEATURE-MATRIX.md` |

If no app reaches the module, that is the finding — say so on the PR, and note
the gap in `docs/GAPS.md`.

### A2. Build the branch locally

The bench resolves Log4j from your local Maven repository, so a local install of
the PR branch becomes a version the bench can select.

```bash
./bench pr 4240 --checkout --install         # 2.x → publishes 2.27.0-SNAPSHOT
./bench pr 4240 --3x --checkout --install    # 3.x → publishes 3.0.0-SNAPSHOT
```

It refuses to run if the Log4j clone has uncommitted changes, rather than
switching branches out from under them. By hand, it is:

```bash
cd ~/apache/logging-log4j2
git fetch origin pull/<PR>/head:pr-<PR>
git switch pr-<PR>
mvn install -DskipTests                 # publishes 2.27.0-SNAPSHOT
```

For a 3.x pull request, use `~/apache/log4j-main`, which publishes
`3.0.0-SNAPSHOT`. Set `BENCH_LOG4J_CLONE` or pass `--clone PATH` if your clones
live elsewhere.

### A3. Establish the "before"

Get a baseline on the last release **before** you overwrite the snapshot, or run
it from a second terminal on a released version:

```bash
cd ~/apache/log4j2-workout
./bench run core-java --config xml/layout-pattern-full --log4j 2.26.1
```

### A4. Run the PR

`2.27.0-SNAPSHOT` is the default, so this is now the PR build:

```bash
./bench run core-java --config xml/layout-pattern-full
```

Compare the two outputs directly. `logs/` keeps each run.

### A5. Check the PR did not break the rest of the axis

The same test on every version — releases either side plus your PR build:

```bash
./bench matrix --apps core-java --configs xml/layout-pattern-full \
  --javas 17 --scenario exceptions
```

Then widen only where the diff plausibly reaches:

```bash
./bench matrix --apps core-java,db,spring-boot-maven --javas 8,17,21 \
  --scenario messages
```

A `SKIP` is information, not a failure — the bench prints the reason (no 3.x
release path, JDK below the app's class file target, and so on). Read the
reason; do not treat it as a pass or a problem.

### A6. Attach evidence to the PR

```bash
./bench repro <PR> --pr \
  --scenario exceptions \
  --config xml/layout-pattern-full \
  --log4j 2.26.1 --log4j 2.27.0-SNAPSHOT
```

This writes `repros/pr-<PR>/` with a standalone Maven project (no parent POM, no
reference to this workspace), a filled-in verification matrix in `README.md`, and
the per-version logs. Post the matrix; attach the zip.

### A7. Write the review

Say what you ran, on what versions and JDKs, and what you saw — not just a
verdict. Anything the PR does not cover but should goes in the review as a
question, not as a change request you make yourself.

```bash
gh pr comment 4240 -R apache/logging-log4j2 --body-file repros/pr-4240/README.md
gh pr review  4240 -R apache/logging-log4j2 --comment --body-file notes.md
```

Then put the clone back: `git -C ~/apache/logging-log4j2 switch main`, and
rebuild the snapshot before your next unrelated run — `~/.m2` still holds the
PR's build until you do.

---

## B. Reproducing and fixing an issue

### B1. Read the report, then reproduce on the version named

```bash
oss issue 4143 --repo apache/logging-log4j2   # the report, with the version and config in it
```

Pick the app and config nearest the reporter's setup, then run exactly the
version they named:

```bash
./bench run core-java --config xml/layout-jsontemplate --log4j 2.24.1
```

Nothing obvious? Raise the status level (see rule 2 above). The Cassandra
finding was found this way: the appender had already failed at startup, and the
per-event NPE was burying the one line that named the cause.

Still nothing? The config is probably wrong for the report, not the report wrong
for Log4j. Check `./bench list --configs` and `docs/FEATURE-MATRIX.md` for one
that actually exercises the feature — a config that never loads the layout under
discussion reproduces nothing and looks like a pass.

### B2. Find the boundaries on the version axis

```bash
./bench matrix --apps core-java --configs xml/layout-jsontemplate \
  --javas 17 --scenario exceptions
```

Read off the first version that fails and the last that passes. That pair is the
single most useful thing you can put in the issue.

Then check the other axes, one at a time — a bug that only appears on JDK 8, or
only under the properties format, is a different bug:

```bash
./bench matrix --apps core-java --configs xml/layout-jsontemplate \
  --javas 8,17,21 --scenario exceptions
./bench matrix --apps core-java \
  --configs xml/layout-jsontemplate,json/layout-jsontemplate,yaml/layout-jsontemplate \
  --javas 17 --scenario exceptions
```

### B3. Write the reproduction

```bash
./bench repro <issue> \
  --scenario exceptions \
  --config xml/layout-jsontemplate \
  --log4j 2.24.1 --log4j 2.25.4 --log4j 2.26.1 --log4j 2.27.0-SNAPSHOT
```

`repros/issue-<n>/log4j-issue-<n>-repro.zip` is a standalone project — extract it
outside this repository and run `./run.sh` once yourself before attaching it.
Its dependencies are derived from the config it ships, so a `JsonTemplateLayout`
repro pulls in `log4j-layout-template-json` rather than silently falling back to
the default configuration.

### B4. File it, or draft it first

New findings get written to Apache's bug template under `docs/issue-drafts/`
before anything is filed — read the draft once as text, then file it from the
file rather than from an inline heredoc you cannot re-read:

```bash
gh issue create -R apache/logging-log4j2 \
  --title "<one line that names the class and the symptom>" \
  --body-file docs/issue-drafts/<draft>.md --label bug

# an existing report instead: post the verification matrix as a comment
gh issue comment 4143 -R apache/logging-log4j2 --body-file repros/issue-4143/README.md
```

The zip cannot be attached from the CLI — GitHub has no API for issue
attachments. Drag it into the comment box.

`docs/ISSUES.md` records what was raised; `docs/GAPS.md` records what is still
open. See `docs/issue-drafts/README.md` for the template and the four drafts
already there.

### B5. Fix it in the Log4j clone

The fix goes upstream, never here:

```bash
cd ~/apache/logging-log4j2
git switch main && git pull --ff-only
git switch -c fix-<issue>
# edit, add a test in the Log4j module's own test source set
mvn install -DskipTests
```

### B6. Verify the fix from the bench

Same command as B1, now against your patched snapshot:

```bash
cd ~/apache/log4j2-workout
./bench run core-java --config xml/layout-jsontemplate      # default = 2.27.0-SNAPSHOT
```

Then re-run the sweep from B2. Every version that failed before should now show
the fix's version passing, and **every version that passed before must still
pass** — that half is the point of the sweep.

```bash
./bench matrix --apps core-java --configs xml/layout-jsontemplate \
  --javas 8,17,21 --scenario exceptions
```

Widen once more before you open the PR, to catch collateral damage:

```bash
./bench matrix --apps core-java,db,spring-boot-maven,log4j1-bridge \
  --javas 17 --scenario messages
```

### B7. Open the upstream PR

Follow the Log4j project's own contribution process. Attach the `--pr`
reproduction from A6 as the evidence that the fix closes the issue.

---

## Changes to this repository

Work in this bench — a new config, a new app, a draft — goes through a pull
request; `main` refuses direct pushes, and it is the only long-lived branch.
Fork first if you are not a maintainer here.

```bash
git switch main && git pull
git switch -c my-change
git push -u origin my-change
gh pr create --base main
```

Squash on the way in, which keeps the history linear. CI runs ~200 cells in ~13
minutes on every PR into `main`, so batch related work into one branch rather
than opening a PR per fix.

```bash
gh pr checks                                  # CI on the current branch
gh run watch                                  # follow it to completion
gh run view <id> --log-failed                 # only what failed
gh pr merge <n> --squash --delete-branch
```

A docs-only PR reports no run at all — CI skips `**.md` and `docs/**`. That is
the filter working, not a stuck check. The rest is in
[`GH-COMMANDS.md`](GH-COMMANDS.md) §3–§4.

---

## When something looks wrong

| Symptom | First thing to check |
|---|---|
| Run exits 0 but stores/sends nothing | status logger at TRACE (rule 2) |
| A 3.x column passes suspiciously | 3.x reads `log4j.configuration.location`, not `log4j.configurationFile`; the wrong name silently loads `DefaultConfiguration` |
| Properties config fails on 3.x | the Log4j 2 properties format does not exist on 3.x |
| Cassandra appender stores nothing | start `cassandra-init`, not `cassandra` — it creates the keyspace the appender cannot |
| Edits to app sources have no effect | `BENCH_REUSE_BUILDS=1` is set; it reuses a cached classpath. Only use it for sweeps |
| A matrix cell says SKIP | read the printed reason — it is pruning an invalid cell, not failing |

The full list is `docs/HANDOVER.md` §13 and `docs/FEATURE-MATRIX.md` §17.