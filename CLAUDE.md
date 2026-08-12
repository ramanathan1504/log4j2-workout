# Working in this repository

A real-application bench for Apache Log4j, used for maintainer work: when an
issue or pull request arrives on `apache/logging-log4j2`, run it here against a
real application on every affected version, then emit a standalone reproduction
to attach to it.

**Nothing in this repository is ever pushed to an Apache project.** Findings are
drafted here and filed by hand; fixes go in the Log4j clone, never here.

## Orientation

**The documentation is not in this repository.** Every operator document — how to
work an issue by hand, how to judge a contributor pull request, the command
reference, the collected traps, the coverage catalogue and the 56 findings —
lives in the knowledge base, indexed and searchable, under `Reference/` and
`Projects/log4j/`. Nothing here duplicates it.

| Need | Where |
|---|---|
| Any of the above | the knowledge base — `Reference/operating-this-bench`, `Reference/working-an-issue-or-a-pr-by-hand`, `Reference/reviewing-a-contributor-pull-request`, `Reference/command-reference`, `Reference/log4j-feature-matrix-complete-coverage-catalog` |
| Reviews already written, and what moved since | `oss followup`, `oss hub` |
| Facts about a pull request, in any repository | `oss pr <n> --repo <owner/name>` |
| What this pack contains | `./bench list`, and `packs/log4j/pack.sh` |

The reason they left: a document that must change in the same commit as the code
belongs beside the code, and one that outlives the code belongs where it can be
found in a year. These were the second kind, and keeping them here meant they
were only findable by someone who already knew this repository existed.

`./bench` is the entry point while the engine still lives here. `./bench help`
lists what it does.

## Where this repo sits, of the three

**`oss` knows → this runs → the archive remembers.**

In `oss`'s own words: this repository is a **runner** extension, and what it runs
against is a **pack**. `packs/log4j/` is the Log4j one; `packs/example/` is there
to be copied. The engine does not know what it is testing.

| Repo | Owns | Reach for it when |
|---|---|---|
| [`oss`](https://github.com/ramanathan1504/oss-cli) | facts about any repo, from the GitHub API, cached by head SHA. No clone, any project, any language. | you want PR facts, conventions or a verdict without building anything |
| **this one** | execution — real apps, real JVMs, the version × config × app matrix | the question needs something to actually run |
| `knowledge-creator` | the archive: harvests threads and notes into DEVONthink, topic-first, indexed | you want it findable in a year |

One test decides where new work belongs: *does it need to execute code against a
real app?* If yes it is here; if it only needs to be retrievable later it is
`knowledge-creator`; if it is neither, it is `oss`.

That question went unasked once and cost a rebuild: `./bench redgreen` was
written from scratch while `knowledge-creator/log4j-pr-review.sh` had done the
build/spotless half for months. The two are now one command, `./bench review`.
`--file` hands the finished write-up to `knowledge-creator/pr-review-file.py`,
which is the only piece that crosses a repo boundary at runtime.

## Rules that cost real time when missed

- **A clean exit proves nothing.** Log4j catches appender exceptions, reports
  them through `StatusLogger`, and exits 0. When something stored or sent less
  than expected, raise the level before assuming the config is wrong:
  `BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE'`.
- **Always pass `--scenario` to a `matrix` sweep.** Without it every cell runs
  all seven scenarios and `rollover` writes 2000 lines — against a JDBC appender
  that is 2000 inserts, per cell. Hours versus a week. Measured.
- **Never `BENCH_REUSE_BUILDS=1` while editing app sources.** It reuses a cached
  classpath and runs stale classes. It is for sweeps only.
- **Log4j 3 reads `log4j.configuration.location`,** not `log4j.configurationFile`.
  The 2.x name does not fail — it silently loads `DefaultConfiguration`, so a
  whole 3.x column can pass while testing nothing.
- **The Log4j 2 properties format does not exist on 3.x.**
- **Cassandra needs the `cassandra-init` compose service,** not `cassandra`. The
  appender cannot create its own keyspace, so the bare node stores nothing
  silently. Per-app requirements like this one live in the knowledge base
  (`Reference/per-app-notes`); keep them there rather than growing this list.
- **`./bench repro <arg>` treats its first argument as the issue number** — there
  is no `--help`. `./bench repro --help` scaffolds and runs `repros/issue---help/`.
- A matrix `SKIP` is information, with the pruning rule printed. It is neither a
  pass nor a failure.
- **`gh pr list --limit N` is newest-`OPEN`,** not "the last N". It drops
  everything merged or closed in the same window, and says nothing about doing
  so. Answering "what arrived recently" with it returned ten of twenty-four once.
  Use `--state all --search "created:>=<date>"` when the window is what you mean.
- **A repro can report PASS having done nothing.** `commons-compress` dispatches
  to a codec backend, and a missing one fails on the *rollover thread* as a WARN,
  which the "no StatusLogger error" check reads as success. Fixed in
  `scripts/repro.sh` for zstd and xz; the shape recurs wherever a plugin resolves
  a backend at runtime. Verify the artefact (`zstd -t`, `unzip -t`), not the exit
  code — and see `Reference/reviewing-a-contributor-pull-request` §3.

## Numbers: read them from the source, not the prose

The markdown drifted from the code more than once. When stating a count, derive
it:

| Count | Source of truth |
|---|---|
| Apps, versions, configs | `./bench list --apps` / `--versions` / `--configs` |
| Which apps are 2.x-only | `APPS_2X_ONLY` in `packs/log4j/pack.sh` (eleven; so eight of nineteen run on 3.x) |
| Module reach | `./bench coverage`, which reads the source clone |

`./bench list --configs` prints 74 files, but one (`templates/bench-custom.json`)
is a JsonTemplateLayout template, not a configuration — 73 configs, 27 of them in
XML, which is the superset.

Three of those are `repro-*`, written for one named PR and hand-run. `matrix`
skips them with a reason rather than sweeping them, because one is built to fail.

## Local clones and the version axis

| Clone | Publishes | Used by |
|---|---|---|
| `~/apache/logging-log4j2` | `2.27.0-SNAPSHOT` | the default `--log4j`, `./bench coverage` |
| `~/apache/log4j-main` | `3.0.0-SNAPSHOT` | `--log4j 3.0.0-SNAPSHOT`, `coverage --3x` |

`mvn install -DskipTests` in either publishes into `~/.m2`, which is where
`./bench` resolves those versions. `./bench pr <n> --checkout --install` does it
for a pull request — **take the baseline against a release first**, because a
baseline measured after the overwrite measures the pull request twice.

## Git workflow

`main` is the only long-lived branch, and carries branch protection. A direct
push is refused with `GH006`, even under `--no-verify`.

```
fork or feature branch --squash--> main
```

There used to be a `development` branch in front of `main`, merged across in a
second step. It was removed once the repository went public and every change
began arriving as a pull request: the pull request is the gate, and a second
long-lived branch behind it only added a merge to perform and somewhere for the
two to drift. They had already drifted by fifty merge commits when it went.

- `git config core.hooksPath .githooks` — once per clone; git will not do it.
- feature → `main`: **squash**, which keeps it linear.
- `git branch --merged` under-reports here — squash merges mean branch commits
  never become ancestors. Ask `gh pr list --state merged` instead.
- CI runs ~200 cells in ~13 minutes on every PR into `main`. It skips `**.md`,
  `docs/**`, `.githooks/**`, `.gitignore`, `.gitattributes`, `infra/output/**`,
  `log4j-samples/**` — a docs-only PR reports **no run at all**, which is the
  filter working, not a stuck check.
- Because each PR costs that run, **batch related work into one branch** rather
  than opening a PR per fix.

## Upstream discipline

- Draft findings to Apache's bug template and get the text approved before
  `gh issue create`. Drafts live in the knowledge base (`Reference/issue-drafts`),
  not here. File from `--body-file`, never an
  inline heredoc you cannot re-read.
- Always pass `-R apache/logging-log4j2`. Omitting it targets *this* repository,
  which is the easiest way to file a bug in the wrong place.
- **Never write to `apache/logging-log4j2` to test anything.** Not a pending
  review, not a draft, not something deleted a second later — a GitHub delete
  does not reach the mailing-list archive or an email already sent. Verify write
  paths against a repo you own, a fixture, or a mocked POST. Read-only `gh` is
  fine and is what the tooling is built on. This cost a real incident:
  `Reference/unauthorised-writes-to-apachelogging-log4j2` in the knowledge base.
- Fixes go in the Log4j clone on their own branch, with a test in that module's
  own test source set.
- Verifying a fix has two halves: every version that failed must now pass, **and
  every version that passed must still pass**. The second half is the reason to
  sweep rather than re-run the one failing cell.

## Before saying something works

The habit that has caught the most here is checking the claim, not the intent:

- Shell scripts: `bash -n`, then run the error paths, not just the happy one.
- `gh` commands: run them before writing them down. `gh search issues --state
  all` is invalid; that was only found by running it.
- AsciiDoc: render every page and check each `xref` resolves — page *and*
  anchor — before claiming the site is fine.
- `bench`: `./bench help` after touching the header. `usage()` reads the header
  block, so a malformed one prints the script.

## Outputs

| Path | Contains |
|---|---|
| `logs/<config>/` | what the appenders produced — where a finding is confirmed |
| `.bench/` | cached classpaths, sweep logs, the cell ledger — disposable, `./bench clean`. Two exceptions it keeps: `hub/` (the daily reports) and `reviews/` (evidence); `clean --all` takes those |
| `repros/<kind>-<n>/` | the zip, the verification matrix, the per-version logs |
| `docs/evidence/` | captured logs referenced by findings |

Scratch work goes in the session scratchpad, not in the repository.
