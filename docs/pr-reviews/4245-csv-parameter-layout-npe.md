# PR #4245 — treat null parameters as empty in `CsvParameterLayout`

https://github.com/apache/logging-log4j2/pull/4245

| | |
|---|---|
| Author | `arimu1` (dev_Hakaze) |
| Prior merged PRs in this repo | **0** |
| Base | `2.x` |
| Size | +30 −1, 3 files |
| Linked issue | **#4243 — filed by you (`ramanathan1504`) on 2026-08-05 18:53Z** |
| PR opened | 2026-08-06 01:49Z — **6h 56m** after your issue |
| CI | green |

## Verdict: ✅ take it, but answer one design question first.

## Is it really needed?

Yes. This is your own #4243, and you already have the reproduction in the repo:

- `configs/xml/repro-csv-npe.xml`
- `repros/issue-4243/` — zip, matrix, per-version output
- `docs/issue-drafts/csv-parameter-layout-npe.md`

Your captured matrix says `2.24.1` ❌ and `2.26.1` ❌, with the NPE swallowed by
`StatusLogger`. The bug is real and trivially reachable — `logger.info("hello")`
produces a `SimpleMessage`, whose `getParameters()` returns `null`.

## The fix is correct

```java
-            getFormat().printRecord(buffer, parameters);
+            getFormat().printRecord(buffer, parameters == null ? Constants.EMPTY_OBJECT_ARRAY : parameters);
```

`org.apache.logging.log4j.util.Constants.EMPTY_OBJECT_ARRAY` exists at
`log4j-api/…/util/Constants.java:121` and `Constants` is a public final class in
log4j-api, already used throughout core. The import is fine.

The test is honest — it builds a real `Log4jLogEvent` with a `SimpleMessage` and
asserts the serialized form.

## The one question worth asking before merging

The fix makes a parameter-less event produce **an empty CSV record** — the record
separator and nothing else. The test asserts precisely that:

```java
Assert.assertEquals(layout.getFormat().getRecordSeparator(), result);
```

So `logger.info("hello")` under a `CsvParameterLayout` now writes a blank line
instead of throwing. That is defensible — the layout logs parameters, and there
are none — but it means a mixed application writes a CSV file interleaved with
blank lines, one per parameter-less call, which is arguably worse than the
current loud failure for anyone parsing that file.

The alternatives a maintainer might prefer:

1. Empty record (this PR).
2. Skip the event entirely — return `Strings.EMPTY`, no line written.
3. Write the formatted message as a single-column record.

This is a maintainer call, not a contributor one, and the PR picks (1) without
raising that it is a choice. Worth flagging so it is decided rather than defaulted
into.

## Also worth checking: the 3.x copy

Your own `docs/ISSUES.md` already records this:

> - 2.x — `log4j-core/.../core/layout/CsvParameterLayout.java:98`
> - 3.x — `log4j-csv/.../csv/layout/CsvParameterLayout.java:104`
>
> The 3.x copy moved module and gained a recycler; the null check is still absent.

This PR is `2.x` only. If it merges, `main` still has the NPE. Worth saying so on
the PR so it does not close the issue prematurely.

## Repro

Already built. Re-verify the baseline before installing the PR:

```bash
cd repros/issue-4243/log4j-issue-4243-repro && ./run.sh 2.26.1
```

Then verify the fix, and — the half that gets skipped — that nothing else broke:

```bash
./bench pr 4245 --checkout --install
./bench matrix --apps core-java --configs xml/repro-csv-npe --javas 17 --scenario messages
```

Always pass `--scenario`. Without it every cell runs all seven.

---

## ── paste-ready comment ──

Thanks — this matches the diagnosis in #4243 and the fix is right.
`Constants.EMPTY_OBJECT_ARRAY` is the correct constant and the test builds a
real `Log4jLogEvent`, which is what I would want to see here.

Two things before this goes in.

**1. Is an empty record the output we want?** The test pins the behaviour to
"record separator and nothing else", so `logger.info("hello")` under a
`CsvParameterLayout` now writes a blank line rather than throwing. For an
application that mixes parameterised and non-parameterised calls, that produces
a CSV file interleaved with blank lines. The alternatives would be to skip the
event entirely (return `Strings.EMPTY`, write no line) or to emit the formatted
message as a single column. All three are defensible — I would just like it to
be a decision rather than a default. @ppkarwasz, any preference?

**2. `main` has the same bug.** The layout moved to `log4j-csv` and gained a
recycler on `main`, but the null check is still absent
(`log4j-csv/.../csv/layout/CsvParameterLayout.java`). This PR is `2.x`-only, so
#4243 should stay open, or a companion PR should follow.

I have a standalone reproduction for this (plain `log4j-api` + `log4j-core` +
`commons-csv`, no parent POM) that fails on 2.24.1 and 2.26.1 — happy to attach
it to #4243 if that is useful for the regression check.
