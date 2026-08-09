# PR #4229 — reproduction, run by hand

https://github.com/apache/logging-log4j2/pull/4229 — *do not apply POSIX file
attributes through symbolic links*

No linked issue. No external service. POSIX filesystem required.

---

## What is being demonstrated

`PosixViewAttribute` is a post-rollover action that chmods the files it matches.
The PR's claim is that it does so **through symlinks**, so a link planted in the
directory it walks redirects the chmod onto the link's target.

Two independent causes, and the PR fixes both:

- `FileUtils.defineFilePosixAttributeView` resolved the view without
  `LinkOption.NOFOLLOW_LINKS`, so `setPermissions` landed on the target
- `walkFileTree` hands symlinks to `visitFile` regardless of `followLinks="false"`,
  so the documented default never prevented the visit

The repro shows a file **outside the log tree** changing permissions because of a
link **inside** it.

---

## The config already exists

`configs/xml/rollover-advanced.xml` — and it is already configured with exactly
the arguments the PR says are ineffective:

```xml
<PosixViewAttribute basePath="${dir}/posix" maxDepth="1"
                    followLinks="false"
                    filePermissions="rw-r-----">
  <IfFileName glob="app-*.log.gz"/>
</PosixViewAttribute>
```

Nothing needs adding. `followLinks="false"` is already set — that is the point.

The only thing missing is a planted link, which `setup.sh` provides. It names the
link `app-99.log.gz` **because that is what the `IfFileName` glob accepts** — a
link the condition rejects is never visited and the run proves nothing.

---

## Steps

### 1. Plant the link

```bash
cd ~/apache/log4j2-workout
./repros/pr-4229/setup.sh plant
```

Creates `$TMPDIR/log4j-pr-4229-outsider.txt` at mode `600`, and links
`logs/rollover-advanced/posix/app-99.log.gz` to it.

Confirm it prints `-rw-------`. That is the value the run must not change.

### 2. Baseline, against a release. First.

```bash
./bench run core-java --config xml/rollover-advanced --log4j 2.26.1 rollover
./repros/pr-4229/setup.sh check
```

**Expect `-rw-r-----`** — the action's `filePermissions`, applied to a file it was
never pointed at. That is the bug.

If it still reads `-rw-------`, the action did not reach the link. Check, in
order: that the rollover actually fired (`ls logs/rollover-advanced/posix/`),
that the glob matched, and that `maxDepth="1"` covers where the link sits. A
clean exit proves nothing here — `PosixViewAttribute` failures are reported
through `StatusLogger`:

```bash
BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' \
  ./bench run core-java --config xml/rollover-advanced --log4j 2.26.1 rollover
```

### 3. Install the PR

Your clone has a staged change to `AsyncTraceContextBenchmark.java`; stash it
before switching branches.

```bash
cd ~/apache/logging-log4j2
git stash push -m "pre-4229" log4j-perf-test/src/main/java/org/apache/logging/log4j/perf/jmh/AsyncTraceContextBenchmark.java

cd ~/apache/log4j2-workout
./bench pr 4229 --checkout --install
```

### 4. After

```bash
./repros/pr-4229/setup.sh clean
./repros/pr-4229/setup.sh plant
./bench run core-java --config xml/rollover-advanced rollover
./repros/pr-4229/setup.sh check
```

**Expect `-rw-------`** — untouched.

Also check the rollover itself still completed: the PR returns `CONTINUE` on a
symlink rather than throwing, so a planted link must not abort the action.

```bash
ls logs/rollover-advanced/posix/
```

Real files should still have picked up `rw-r-----`. If they did not, the visitor
skip is over-reaching and skipping everything, which the upstream
`PosixViewAttributeActionTest` is written to catch.

### 5. Restore

```bash
./repros/pr-4229/setup.sh clean
cd ~/apache/logging-log4j2 && git switch 2.x && git stash pop && mvn install -DskipTests
```

The `mvn install` matters — until you run it, `2.27.0-SNAPSHOT` *is* PR #4229 and
every later bench run silently tests it.

---

## Reading the result

Reproducing this confirms the mechanism, not the severity. Planting that link
requires write access to the directory the application is actively logging into.
An attacker who has that already has better options than redirecting a chmod.

So the finding is **hardening**, and the thing worth raising on the PR is the
framing of the changelog entry rather than the change itself.

Full reasoning and a paste-ready comment:
[`docs/pr-reviews/4229-posix-symlink.md`](../../docs/pr-reviews/4229-posix-symlink.md)
