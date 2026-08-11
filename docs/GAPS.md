# Open gaps

Everything not done, why, and what closing it would take. Nothing here is
hidden elsewhere — this is the complete list.

---

## 1. Log4j coverage

### `log4j-plugin-processor` unreachable on 3.x

The one shippable 3.x module the bench does not reach (21 of 22).

**Why:** `@Plugin` moved package — `org.apache.logging.log4j.core.config.plugins`
on 2.x, `org.apache.logging.log4j.plugins` on 3.x — along with `@PluginFactory`
and `@PluginAttribute`. The same plugin sources cannot compile against both
lines, so `apps/custom-plugins` is 2.x-only and the processor it uses is never
exercised on 3.x.

**To close:** a second source set for the 3.x annotations, activated by the
`log4j-3x` profile. Roughly five duplicated plugin classes. Worth doing only if
3.x plugin authoring matters more than the duplication costs.

**Not a bench defect** — it is a fact about writing Log4j plugins, and any
third-party plugin targeting both lines faces it. Recorded in `FEATURE-MATRIX`
§17 and §19.

### ~~`jakarta-web` and `javax-web` skip in sweeps~~ — CLOSED

Both used to start a servlet container, print an endpoint and serve until
interrupted, so no matrix cell could finish one.

**Closed** the way this entry proposed: each launcher gained a `selfTest()` that
drives its own bench endpoints over real HTTP and exits with a status, and
`INTERACTIVE_APPS` is now empty. Both sweep to `PASS`.

The subtlety worth keeping: `extra_jvm_args_for` has to pass
`-Dbench.selfTest=true`, or the launcher serves anyway and the cell converts a
stated `SKIP` into a 300-second `FAIL` — worse than the behaviour it replaced.

### No full `--all` sweep recorded

`.bench/coverage.tsv` holds partial sweeps only.

**Why:** ~37,000 cells. Measured at about six hours with `--scenario` and roughly
a week without. Run to ~700 cells and stopped deliberately — a broad matrix is a
tripwire, not a microscope, and every finding in §17 came from a targeted run.

**To close:** `./bench matrix --all --scenario messages --reuse-builds`, overnight.
Worth doing **once**, as a baseline to diff a specific Log4j candidate against.
The CI slice is the regression gate for everything else.

---

## 2. Issues drafted but not filed

In `docs/issue-drafts/`. Four filed already — see `docs/ISSUES.md`.

| Draft | Evidence | Blocked on |
|---|---|---|
| `locateContext` drops the `ServletContext` entry | captured both sides | **duplicate of #2314** (open since Feb 2024). New material is the appserver+web trigger — draft comment in `docs/SLACK.md`, not posted |
| Interrupting the configuring thread disables every appender | **none** | a deterministic reproduction. It is a race; observed once, two unrelated healthy appenders went silent after a worker was interrupted mid-configuration |

The selector draft's control is a sibling app on a different servlet API, not the
identical deployment minus `log4j-appserver`. Stronger evidence would be removing
appserver from the failing deployment.

---

## 3. Samples for `logging-log4j-samples`

Seven modules built and green — 21 tests against `581d9a8` / Log4j 2.26.1.
Eight proposed and not built. Full analysis in `log4j-samples/GAP-ANALYSIS.md`.

### Feasible, simply not written

| Module | Note |
|---|---|
| `migration-1x` | `log4j-1.2-api` is in `log4j-bom`. Scaffolded then removed rather than committed half-built |
| `garbage-free` | needs no extra dependency |
| `web` | `tomcat-embed-core` and `jetty-servlet` **are** managed; a servlet container sample is just substantially larger |

### Blocked on an upstream dependency decision

The samples parent manages no version for these, and hard-coding one would break
the convention every existing module follows.

| Module | Needs |
|---|---|
| `jdbc-appender` | a JDBC driver — H2 is not in `dependencyManagement` |
| `smtp` | GreenMail, and `jakarta.mail` |
| `network-appenders` | Kafka, syslog and an SMTP sink as containers — likely unsuitable for that repository at all |

**Nothing has been offered upstream.** Each module is verified by copying into a
scratch checkout of the clone, building, and reverting it.

---

## 4. Known-unresolved behaviour

### Cassandra 5 unreachable

`log4j-cassandra` ships DataStax driver 3.11, which negotiates native protocol v4
at most. Against a 5.0 container the connection attempt neither completed nor
errored — the run stalled with no status output. Cassandra **4.1** works, and is
what `infra/docker-compose.yml` pins.

**To close upstream:** `log4j-cassandra` rebuilt against DataStax driver 4.x.
That is an upstream change, not a bench one.

---

## 5. Infrastructure

- The published documentation site has **no URL**. `docs/site/index.html` is
  self-contained and opens in any browser; the Antora sources are alongside it.
  Publishing failed with `init 404` — a service problem, not a content one.

---

## What is *not* a gap

Stated because each looks like one:

- **Eleven of nineteen apps cannot run on 3.x**, so eight do. Ten of the eleven
  because their Log4j modules were never published for it — `log4j-1.2-api`,
  `log4j-jakarta-web`, `log4j-spring-boot`, `log4j-jpa`, the JUL/JCL/SLF4J
  bridges, SMTP, JMS. Upstream reality. The eleventh is `custom-plugins`, for
  the annotation package move in §1. `APPS_2X_ONLY` in `packs/log4j/pack.sh` is the list.
- **`SerializedLayout` is not exercised.** Deprecated, and refuses to build
  without `log4j2.enableSerialization`.
- **`${jndi:}` renders unresolved.** Deliberate. The bench never sets
  `log4j2.enableJndiLookup`; see `HANDOVER.md` §9.
- **`appender-composite` emits three errors every run.** Deliberate — `Failover`
  cannot be demonstrated without a primary that fails.
