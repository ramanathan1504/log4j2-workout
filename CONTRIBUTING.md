# Contributing

This is a bench: nineteen real applications, run against real JVMs across a
version × config × app matrix, used to answer *does this actually behave the way
the issue says?* about Apache Log4j.

**Nothing here is ever pushed to an Apache project.** Findings are drafted here
and filed by hand, upstream, by a person. If a change you make would post,
comment or push anywhere outside this repository, it is out of scope — see
`docs/UPSTREAM-INCIDENT.md` for why that rule exists in this shape.

## Fork, then open a pull request

`main` is protected and takes no direct pushes from anyone, including the
maintainer.

```bash
gh repo fork ramanathan1504/log4j2-workout --clone
git switch -c what-it-does
gh pr create --base main
```

Target **`main`** — it is the only long-lived branch here. Merges are squashed,
which keeps the history linear and readable.

CI runs roughly 200 matrix cells in about 13 minutes on every pull request into
`main`, so **batch related work into one branch** rather than opening a pull
request per fix. A documentation-only change reports no run at all — that is the
path filter working, not a stuck check.

## What a good change looks like

State what you ran and what it printed. On a bench, that is the whole
contribution: "should fix the rollover" and "reproduced on 2.24.1 through 2.26.1,
and the archive now verifies with `zstd -t`" cost the same to write.

Two traps this repository has been bitten by, both worth knowing before you
report a pass:

- **A clean exit proves nothing.** Log4j catches appender exceptions, reports
  them through `StatusLogger`, and exits 0. Raise the level before concluding
  anything: `BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE'`.
- **A repro can report PASS having done nothing.** A missing codec backend fails
  on the *rollover thread* as a WARN, which a "no StatusLogger error" check reads
  as success. Verify the artefact — `zstd -t`, `unzip -t` — not the exit code.

## Adding to the bench

The engine is `bench`; what it tests is `packs/`. To point the same machinery at
a different project, copy `packs/example/` rather than editing the engine:

```bash
BENCH_PACK=example ./bench list
```

Run `./bench help` after touching any script header — `usage()` reads that block,
so a malformed one prints the script.

## Licence

Apache 2.0. New scripts carry the standard header.

By opening a pull request you agree that your contribution is licensed under the
same terms.

## Reporting something insecure

Do not open a public issue. Use GitHub's **Report a vulnerability** button under
the Security tab.

Note that a finding *about Log4j itself* is not a vulnerability in this
repository — it belongs upstream, filed by hand, following
`docs/PR-REVIEW.md` and the Apache process.
