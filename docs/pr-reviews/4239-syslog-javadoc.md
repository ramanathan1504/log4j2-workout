# PR #4239 — Fix `Log4j1SyslogLayout` builder javadoc attributes

https://github.com/apache/logging-log4j2/pull/4239

| | |
|---|---|
| Author | `kalayciburak` (Burak Kalaycı) |
| Prior merged PRs in this repo | **0** |
| Base | `2.x` |
| Size | +8 −3, 1 file |
| Linked issue | **#4237 — filed by `ppkarwasz`, a Log4j PMC member** |
| CI | green |

## Verdict: ✅ take it.

## Is it really needed?

This is the one small PR in the batch that clears the bar without argument, and
the reason is the issue author. `ppkarwasz` is a Log4j maintainer, and #4237 is a
maintainer saying "this javadoc documents an option that does not exist, someone
please fix it." A first-time contributor picking up a maintainer-filed
documentation issue is exactly the intended on-ramp, not profile padding.

The claim is accurate: `Log4j1SyslogLayout.Builder` listed `includeNewLine` and
`escapeNL`, which belong to `org.apache.logging.log4j.core.layout.SyslogLayout`
and are not implemented here. The replacement list — `facility`,
`facilityPrinting`, `header`, `messageLayout`, `charset` — matches the builder's
actual fields.

## Notes

- Javadoc-only. Zero runtime risk, nothing to reproduce, no bench cell applies.
- The `{@link org.apache.logging.log4j.core.layout.SyslogLayout}` reference is to
  a class in `log4j-core`, from `log4j-1.2-api`. That module already depends on
  core, so the link will resolve — but it is worth a javadoc build to confirm,
  since an unresolvable `@link` is a warning that some configurations escalate.
- No changelog entry. Correct — javadoc-only changes do not need one under this
  project's rules.
- The PR's "Test" section says *"Module previously compiled `Log4j1SyslogLayout`
  after the edit"*, which is oddly phrased and does not say a javadoc build was
  run. Given the new `@link`, asking for that is reasonable.

## Repro

None applicable.

---

## ── paste-ready comment ──

> Thanks — this matches #4237 and the replacement list is right: `facility`,
> `facilityPrinting`, `header`, `messageLayout` and `charset` are the builder's
> actual attributes, and `includeNewLine` / `escapeNL` belong to
> `org.apache.logging.log4j.core.layout.SyslogLayout`, not to this one.
>
> One thing to confirm before merge: the new
> `{@link org.apache.logging.log4j.core.layout.SyslogLayout}` points from
> `log4j-1.2-api` into `log4j-core`. The dependency is there so it should resolve,
> but could you run a javadoc build (`./mvnw -pl log4j-1.2-api javadoc:javadoc`)
> and confirm there is no unresolved-link warning? The PR notes only that the
> module compiles.
>
> No changelog entry needed for a javadoc-only change, so that part is correct as-is.
