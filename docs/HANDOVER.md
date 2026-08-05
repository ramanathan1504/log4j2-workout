# Operating this bench

Everything needed to use this repository without asking anyone. Written to be
read once end to end, then used as reference.

Facts here were checked against the working tree, not remembered. Where something
is unverified or unfinished it says so.

---

## 1. What this is

A real-application bench for Apache Log4j. When an issue or pull request arrives,
run it here against a real application on every affected version, then emit a
standalone reproduction to attach to the issue.

**The idea the whole thing rests on:** Log4j catches appender exceptions, reports
them through `StatusLogger`, and lets the JVM exit 0. A run that produces no
error therefore proves nothing. A broken JDBC appender, an SMTP appender that
never sends, and a Kafka appender pointed at a dead broker all look exactly like
success from the application's side.

Every application here asserts on an **outcome**: rows read back, mail consumed,
a queue drained, a topic polled, bytes on disk.

Two deliberate exceptions, so you are not confused when you meet them:

- `configs/xml/appender-composite.xml` emits exactly three errors on purpose —
  `Failover` cannot be demonstrated without a primary that fails. All three name
  `BrokenPrimary`. Judge that config by whether `logs/composite/failover.log`
  filled up.
- `configs/xml/lookups.xml` leaves `${jndi:}` deliberately unresolved. See §9.

---

## 2. Layout

```
bench                     the CLI — the only entry point
apps/                     18 application modules, 19 run targets
configs/                  70 configurations, shared by every app
  xml/ json/ yaml/ properties/    the same configs in four formats
  log4j1/                 log4j.xml and log4j.properties for the 1.x bridge
  templates/              JsonTemplateLayout event templates
infra/                    docker-compose.yml, CQL and SQL init scripts
docs/
  FEATURE-MATRIX.md       the catalogue: coverage, gaps, and §17 findings
  HANDOVER.md             this file
  site/                   Antora sources + a self-contained index.html
  evidence/               raw captures backing specific findings
log4j-samples/            candidate modules for logging-log4j-samples
repros/                   generated reproductions, one folder per issue
.bench/                   caches and the matrix ledger (gitignored)
logs/                     runtime output (gitignored)
```

---

## 3. Daily commands

```bash
./bench list                      # apps, configs, versions, scenarios
./bench list --apps               # just the app targets
./bench list --configs            # all 70 configs
./bench list --versions           # the 7 Log4j versions
./bench coverage                  # module reach, recomputed from source
```

Run one app under one configuration:

```bash
./bench run core-java --config xml/layout-pattern-full
./bench run core-java --config json/filter-all --log4j 2.26.1
./bench run core-java --config xml/baseline-console --java 21 exceptions
```

`--config` accepts a bare name (`filter-all` means the XML one), a
`format/name` pair, or a comma-separated list, which builds a **composite**
configuration that Log4j merges.

Sweep an axis:

```bash
./bench matrix --scenario messages                     # every version
./bench matrix --apps core-java,db --javas 17,21
./bench matrix --all --scenario messages --reuse-builds
```

**Always pass `--scenario` to a sweep.** Without it every cell runs all seven
scenarios, and `rollover` writes 2000 lines — against a JDBC appender that is
2000 inserts, per cell. It is the difference between a sweep taking hours and
taking a week. Measured, not estimated.

---

## 4. Reviewing an issue

1. Reproduce it here first, on the version reported:

   ```bash
   ./bench run core-java --config xml/layout-jsontemplate --log4j 2.24.1
   ```

2. If nothing obvious happens, raise the status level. Log4j swallows appender
   failures, so a clean run means little:

   ```bash
   BENCH_JVM_ARGS='-Dlog4j2.debug=true -Dlog4j2.StatusLogger.level=TRACE' \
     ./bench run nosql --config xml/appender-nosql
   ```

   This is how the Cassandra finding was found. The appender had already failed;
   the per-event NPE was hiding the one line that named the cause.

3. Bisect the version axis:

   ```bash
   ./bench matrix --apps core-java --configs xml/layout-jsontemplate \
     --javas 17 --scenario exceptions
   ```

4. Write your notes wherever suits. A markdown file is enough.

---

## 5. Producing a reproduction

```bash
./bench repro 4143 \
  --scenario exceptions \
  --config xml/layout-jsontemplate \
  --log4j 2.24.1 --log4j 2.26.1
```

Produces `repros/issue-4143/`:

| | |
|---|---|
| `log4j-issue-4143-repro.zip` | standalone Maven project — no parent POM, no reference to this workspace. Attach this. |
| `README.md` | a filled-in verification matrix, ready to paste into the issue |
| `output/<version>.log` | the full run against each version |

Dependencies are derived from the configuration it ships, so a
`JsonTemplateLayout` repro pulls in `log4j-layout-template-json` rather than
silently falling back to the default configuration and reproducing nothing.

Pass `--pr` for a pull request instead of an issue. Generation writes only to
`repros/`; the bench itself is never mutated.

**Verified**: the zip was extracted outside the repository and run — it builds
and reproduces without the bench present.

---

## 6. What needs Docker, and what does not

Most apps embed their infrastructure — H2, GreenMail, Tomcat, ActiveMQ Artemis,
an in-process JNDI provider and a Spring Cloud Config server all run inside the
bench JVM. Only two app targets need containers:

```bash
docker compose -f infra/docker-compose.yml up -d mongodb couchdb cassandra-init
docker compose -f infra/docker-compose.yml up -d kafka syslog mailhog
```

**Cassandra needs `cassandra-init`, not `cassandra`.** The appender cannot create
its own keyspace: its DataStax driver pulls in Netty, whose `InternalLoggerFactory`
acquires a Log4j logger *while the driver is initialising*, which configures
Log4j, which starts the Cassandra appender, which connects to a keyspace no
in-JVM bootstrap has reached yet. `cassandra-init` applies
`infra/cql/cassandra-init.cql` once the node is healthy. Skip it and the appender
stores nothing, silently.

Shut everything down with `docker compose -f infra/docker-compose.yml down -v`.

---

## 7. What the matrix refuses to run

`./bench matrix` prunes its cross product first. A cell that cannot pass is a
`SKIP` **with the reason printed**, never a failure — a failing cell that could
never have passed is noise, and it buries the failures that matter. One 698-cell
sweep produced 98 failures, none of them Log4j defects.

| Rule | Prunes |
|---|---|
| `min_java_for` | everything is compiled at release 17 except `java8-baseline` |
| `min_log4j_for` | `jms` needs 2.25.0+; `log4j-jakarta-jms` did not exist before it |
| `is_2x_only` | ten apps have no 3.x release path |
| `INTERACTIVE_APPS` | `jakarta-web`, `javax-web` serve until interrupted |
| `requires_config_for` | an app asserting on an appender its config lacks |
| `requires_app_for` | a config needing infrastructure its app never starts |

### Environment switches

| | |
|---|---|
| `BENCH_JVM_ARGS` | ad-hoc `-D` flags; unrecognised CLI args go to the *app*, not the JVM |
| `BENCH_CELL_TIMEOUT` | per-cell wall clock, default 300s |
| `BENCH_REUSE_BUILDS` / `--reuse-builds` | reuse the cached classpath per (app, version) |
| `BENCH_SPRING_SELFTEST=0` | run the Spring app as an interactive server |

**Never set `BENCH_REUSE_BUILDS=1` while editing app sources.** It reuses a
cached classpath and will run stale classes — precisely what the always-build
default prevents. It is for sweeps, where nothing between cells changes sources.

---

## 8. Coverage, stated precisely

**Log4j 2.x — complete.** 41 of 41 shippable modules on a classpath, 293 plugins
catalogued, all 41 pattern converters, every layout except the deprecated
`SerializedLayout`, four config formats plus both 1.x formats, JDK 8/17/21/22.

**Log4j 1.x — the bridge**, which is all there is to cover. `log4j-1.2-api` plus
both 1.x configuration formats.

**Log4j 3.x — 21 of 22 shippable modules**, catalogued in `FEATURE-MATRIX` §19.
Nine of nineteen app targets run on 3.x; the other ten cannot, because the Log4j
modules they exercise were never published for it.

The one unreachable module is `log4j-plugin-processor`, and the reason is worth
knowing: **`@Plugin` moved package between the lines** —
`org.apache.logging.log4j.core.config.plugins` on 2.x,
`org.apache.logging.log4j.plugins` on 3.x. Plugin sources cannot compile against
both without two source sets.

### Known open

- `jakarta-web` and `javax-web` skip in sweeps. Both serve until interrupted, so
  they cannot finish a cell. The fix is a self-test like `SelfTestRunner` in
  `apps/spring-boot-maven`; nobody has written it. They *are* verified — by hand,
  which is where the appserver/`${web:}` finding came from.
- No full `--all` sweep is recorded. ~37,000 cells, about six hours with
  `--scenario` and roughly a week without. The CI slice is the regression gate; a
  full sweep is a baseline exercise, not routine.

---

## 9. Security posture

The bench **never** enables `log4j2.enableJndiLookup`. The `${jndi:}` lookup that
CVE-2021-44228 abused stays off everywhere, and renders unresolved in
`configs/xml/lookups.xml`. That is deliberate: the config demonstrates all
eighteen lookups *including* the one you must not turn on.

`log4j2.enableJndiJdbc` and `log4j2.enableJndiJms` are scoped to single apps whose
JNDI providers are in-process only — a HashMap-backed `BenchInitialContextFactory`
and Artemis's env-map factory — with no network reach.

`log4j2.Script.enableLanguages` is set globally, because exercising
`ScriptFilter`, `ScriptPatternSelector`, `ScriptCondition` and
`ScriptAppenderSelector` is the point. **A real deployment should not**: it lets
anyone who can write the configuration run code.

---

## 10. Git workflow

The repository is public. `main` and `development` both carry GitHub branch
protection: pull request required, `enforce_admins` on, force pushes and
deletions disabled. A direct push is refused with `GH006` even under
`--no-verify`.

```
feature branch  ->  PR  ->  development  ->  PR  ->  main
```

```bash
git switch development && git pull
git switch -c my-change
git push -u origin my-change
gh pr create --base development
```

Per clone, once — git will not do it for you:

```bash
git config core.hooksPath .githooks
```

`.githooks/pre-push` refuses direct pushes locally so the mistake costs a second
rather than a round trip. It is a convenience; the server is the control.

**Merge strategy matters.**

| Direction | Use | Why |
|---|---|---|
| feature → `development` | `--squash` | `development` keeps linear history |
| `development` → `main` | `--merge` | `--rebase` duplicates commits under new SHAs and the branches diverge until a sync conflicts. That happened; it is why linear history is off for `main`. |

`required_approving_review_count` is **0** on purpose: at 1 a solo maintainer
cannot approve their own PR and the branch deadlocks. Direct pushes are still
refused.

Note that `git branch --merged` **under-reports** here — squash merges mean
branch commits never become ancestors of `main`. Check the PR state instead.

---

## 11. CI

`.github/workflows/bench.yml` runs ~200 cells in ~13 minutes on every pull
request into `development`: four Log4j 2 config formats plus both 1.x formats,
JDK 8/17/21, Log4j 2.24.1 and 2.26.1, across thirteen apps needing no external
infrastructure.

It does **not** run on `development` → `main` syncs — nothing reaches `main`
without passing on the way in. It skips `**.md`, `docs/**`, `.githooks/**`,
`.gitignore`, `.gitattributes`, `infra/output/**` and `log4j-samples/**`.

⚠️ If this is ever made a **required** status check, a skipped run never reports
and docs-only PRs block forever. The fix then is a lightweight always-pass job,
not removing the filter.

---

## 12. Contributing samples upstream

`log4j-samples/` holds candidate modules for `apache/logging-log4j-samples`, in
that project's layout. Seven are built and green — 21 tests. `GAP-ANALYSIS.md`
lists what remains and what each is blocked on.

**Nothing in this repository is ever pushed upstream.** To verify a module:

```bash
cd ~/apache/logging-log4j-samples && git pull --ff-only    # do not skip
cp -r <workout>/log4j-samples/<module> .
# add <module> to <modules> in that pom.xml
./mvnw --projects <module> --also-make test
rm -rf <module> && git checkout -- pom.xml
```

The pull is not optional. A first pass here was verified against a parent four
commits stale, on a Log4j version upstream had already moved off.

House conventions, taken from the existing modules: parent is
`org.apache.logging.log4j.samples:log4j-samples:${revision}`; dependencies carry
**no versions**; test deps are `assertj-core` and `junit-jupiter-api`; every
module has a **JUnit test that asserts behaviour**, not a `main` that prints; ASF
header `~`-prefixed in POMs and `////` in `.adoc`; new modules need an `xref` in
the root README and an entry in the root `<modules>`.

---

## 13. Traps, collected

Each cost real time. In rough order of how much.

**Log4j behaviour** — the full set is `FEATURE-MATRIX` §17, 56 entries. The ones
you will meet first:

- A clean exit proves nothing. Log4j catches appender exceptions and exits 0.
- Log4j 3 reads `log4j.configuration.location`, **not** `log4j.configurationFile`.
  Passing the 2.x name does not fail — it quietly uses `DefaultConfiguration`, so
  an entire 3.x column can pass while testing nothing.
- The Log4j 2 properties format **does not exist on 3.x**.
- `java.util.logging.manager` cannot be set from Java. JUL reads it once, before
  application code runs. Too late is silent, not an error.
- `RollingFileManager`'s compressing executor is non-daemon, so a short-lived app
  hangs on exit unless it calls `LogManager.shutdown()`.
- `Delete`'s sibling conditions are order-sensitive: `IfAccumulatedFileCount` is
  stateful and counts the active file too.

**This repository**

- `bench matrix` exits non-zero when cells fail. `SKIP` is success.
- Editing `bench` while a sweep runs can corrupt it — bash reads scripts
  incrementally. Use `git worktree` instead.
- A stalled sweep looks exactly like a slow one. Compare cell counts between
  checks; the monitor now does.

**Tooling**

- zsh does **not** word-split unquoted variables. `./bench $cmd` where
  `cmd="list --apps"` passes one argument, not two.
- `git add -A` once committed a container's output file. `infra/output/` is now
  ignored.

---

## 14. Upstream: filed, and drafted

**Filed** — both open on `apache/logging-log4j2`:

- **#4241** — `AbstractDatabaseManager` keeps accepting writes after a failed
  startup, and is never shut down. The same `isRunning()` flag is ignored by
  `write()` and honoured by `shutdown()` — backwards in both directions. Affects
  2.x and 3.x.
- **#4242** — `log4j-cassandra` leaks the DataStax `Cluster` on failed startup,
  so the JVM never exits. 2.x only.
- **#4243** — `CsvParameterLayout` throws NPE on any event without parameters.
  Affects 2.x and 3.x.
- **#4244** — `Log4j2EventListener`'s `@ConditionalOnProperty` has no effect;
  the listener always runs. 2.x.

**Drafted, not filed** — `docs/issue-drafts/`. Written to the project's bug
template, each with a reproduction and, where one is defensible, a suggested fix.
Nothing in this repository ever touches an upstream project.

| Draft | Evidence | State |
|---|---|---|

| `@ConditionalOnProperty` has no effect on `Log4j2EventListener` | captured run, 2.26.1 | ready |
| `locateContext` drops the `ServletContext` entry | captured both sides | decide defect vs intended trade-off first |
| Interrupting the configuring thread disables every appender | **none — a race, seen once** | hold until reproducible on demand |

`docs/issue-drafts/README.md` carries the pre-filing checklist: re-verify against
both `2.x` and `main`, search for duplicates, attach a repro zip.

---

## 15. If something looks wrong

| Symptom | First thing to check |
|---|---|
| Run is clean but nothing happened | Raise the status level — see §4 |
| 3.x cell passes suspiciously fast | `log4j.configuration.location`, not `log4j.configurationFile` |
| Appender stores nothing | Is its container up? Cassandra needs `cassandra-init` |
| Sweep stops advancing | Compare cell counts; an app that serves forever burns the cell timeout |
| Stale behaviour after editing sources | `BENCH_REUSE_BUILDS` is set |
| Push rejected | Correct — use a PR. `--no-verify` will not help; the server refuses too |
| `main` "behind" by N commits | Squash merges; compare `git diff`, not commit counts |
