# PR #4235 — reproduction

https://github.com/apache/logging-log4j2/pull/4235 — *sanitize SD-ID and MSGID in
`Rfc5424Layout`*

**Status: injection reproduced on every released 2.x.** Ran 2026-08-08 against
2.24.1, 2.25.5, 2.26.0 and 2.26.1. No linked issue, no external service, no
syslog listener needed.

```bash
cd log4j-pr-4235-repro
./run.sh 2.26.1        # or any version
```

Standalone — `log4j-api` and `log4j-core` only, no parent POM, no reference to the
bench. `Rfc5424Layout` lives in `log4j-core`, so there is no extra artifact.

---

## What it does

Logs **two** events through an `Rfc5424Layout`, then reads the file back and
counts records:

| | SD-ID | type → MSGID |
|---|---|---|
| control | `Audit@32473` | `Audit` |
| hostile | `a] [forged@1 user="root` | `Audit\n<13>1 - - - - -` |

Both hostile values are ordinary application data as far as Log4j is concerned —
neither `StructuredDataMessage#setType` nor the `StructuredDataId` constructors
validate characters, only length.

A `File` appender rather than a `SyslogAppender`, deliberately: the finding is
about what the **layout writes**, not how it is transported, and a file makes
record boundaries inspectable with `cat`. `SyslogAppender format="RFC5424"` wraps
exactly this layout.

---

## Result — 2.26.1

Two events in. **Three syslog records out:**

```
 1 | <134>1 2026-08-08T17:17:06.700+05:30 localhost repro 9901 Audit [Audit@32473 user="alice"] login ok
 2 | <134>1 2026-08-08T17:17:06.702+05:30 localhost repro 9901 Audit
 3 | <13>1 - - - - - [a] [forged@1 user="root user="root"] login ok
```

Record 3 was never logged by the program. It is the tail of the message *type*,
promoted to a syslog record with its own priority (`<13>` = user.notice) and its
own version. A collector reading this stream ingests it as a separate event.

The `]` injection is visible in the same line: `[a] [forged@1 …` — the element
closed after `a`, and `[forged@1 …` reads as a second, caller-controlled
structured element.

## Version matrix

| Log4j | Records from 2 events | Forged `<13>1` record | Injection |
|---|:---:|:---:|:---:|
| `2.24.1` | 2 | ✅ | ✅ reproduced |
| `2.25.5` | 3 | ✅ | ✅ reproduced |
| `2.26.0` | 3 | ✅ | ✅ reproduced |
| `2.26.1` | 3 | ✅ | ✅ reproduced |

Per-version output under `output/`.

**2.24.1 differs, and in a way that strengthens the finding.** There the layout
emitted no separator between the two events, so both records ran together on one
line — meaning the *only* line break in the file is the one the caller supplied:

```
 1 | …Audit [Audit@32473 user="alice"] login ok<134>1 …repro 10048 Audit
 2 | <13>1 - - - - - [a] [forged@1 user="root user="root"] login ok
```

Whatever changed in `newLine` handling between 2.24.1 and 2.25.5 is not this
PR's concern and I did not chase it. Recording it because the matrix is not
uniform and the difference is real.

---

## Verifying the fix

```bash
cd ~/apache/logging-log4j2
git stash push -m "pre-4235" log4j-perf-test/src/main/java/org/apache/logging/log4j/perf/jmh/AsyncTraceContextBenchmark.java

cd ~/apache/log4j2-workout
./bench pr 4235 --checkout --install          # publishes the PR as 2.27.0-SNAPSHOT

cd repros/pr-4235/log4j-pr-4235-repro
./run.sh 2.27.0-SNAPSHOT
```

**Expect: 2 records**, with `?` where the newline and the `]` were. Roughly:

```
Audit?<13>1?-?-?-?-?- [a??[forged@1?user??root] login ok
```

The control record must be **byte-identical** to before — `[Audit@32473
user="alice"]`. `@` is `64`, inside the allowed range, so enterprise IDs must
survive. If the control changed, the sanitiser is too aggressive.

Baselines above were taken against releases, so installing is safe now. Afterwards:

```bash
cd ~/apache/logging-log4j2 && git switch 2.x && git stash pop && mvn install -DskipTests
```

Until that last `mvn install`, `2.27.0-SNAPSHOT` *is* PR #4235 and every later
bench run silently tests it.

---

## Reading the result

Reproducing this confirms the mechanism. It does **not** make it a vulnerability
in Log4j: the SD-ID and type come from the application, not from a remote user, so
exploiting it needs an application that already passes untrusted input as a
structured-data identifier — which is an application bug. That is why a public PR
is the right venue here rather than `security@apache.org`.

Full reasoning and a paste-ready comment:
[`docs/pr-reviews/4235-rfc5424-sd-id-msgid.md`](../../docs/pr-reviews/4235-rfc5424-sd-id-msgid.md)
