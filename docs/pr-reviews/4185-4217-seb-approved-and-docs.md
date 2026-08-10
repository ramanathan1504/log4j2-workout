# PRs #4185 and #4217 — the two you have already driven

Both from `SebTardif` (3 merged PRs — established contributor). Both already have
your fingerprints on them. Grouped because in each case the review question is
"is it finished?", not "is it any good?".

---

## PR #4185 — null-check `LoggerDynamicMBean.addAppender` and `AppenderDynamicMBean.setLayout`

https://github.com/apache/logging-log4j2/pull/4185

| | |
|---|---|
| Base | `2.x` · +145 −0, 6 files · CI green |
| Your review | **APPROVED** ("LGTM!") |
| Last updated | **2026-08-09** — today |

### Verdict: ⚠️ your approval is stale. Re-review before merging.

**The branch grew after you approved it.** Your `LGTM!` covered the
`LoggerDynamicMBean.addAppender` null-check. The author then commented:

> Folded the `AppenderDynamicMBean` `setLayout` null-check (from closed #4219)
> into this branch, plus tests and changelog entries both pointing at #4185.

That is exactly what you asked for when you closed #4219 — so the author did the
right thing. But it means **there is now code on this branch that no review has
covered**, and GitHub still shows your approval, which will read as "reviewed" to
whoever merges it.

The added hunk, `AppenderDynamicMBean.java:187`:

```java
if (layout == null) {
    cat.error("Could not instantiate layout class [" + params[0] + "] for appender ["
            + getAppenderName(appender) + "].");
    return "Could not instantiate layout class.";
}
```

Consistent in shape with the `addAppender` guard you already approved
(`LoggerDynamicMBean.java:67`), which returns `void` and so just logs and returns.
Both are plain defensive null-checks on a reflective instantiation that can
legitimately return null — low risk, and `log4j-1.2-api` JMX is a backwater where
a null-check cannot regress much.

Two things worth a glance before re-approving:

- Returning the **string** `"Could not instantiate layout class."` from `invoke()`
  as if it were a result. The surrounding method's convention decides whether that
  is right or whether it should throw an `MBeanException`. Worth one look, since
  a JMX client cannot distinguish that string from a successful return value.
- Two changelog entries both pointing at #4185 — correct, since #4219 is closed,
  but confirm the ids match what the changelog tooling expects.

### Your own note on the PR

> This is your 4th PR, and we truly appreciate your ongoing support. If possible,
> could you consider picking up some items from the issue list?

Worth knowing that they did: **#4220 targets #3816**, an issue filed by `vy`. That
is them acting on your request — though as
[`4220-instant-pattern-precision.md`](4220-instant-pattern-precision.md) explains,
they picked an issue whose author had already stated a preferred approach that
the PR does not follow. Not a failure of willingness.

---

## PR #4217 — docs: Routing Appender security considerations for high-cardinality keys

https://github.com/apache/logging-log4j2/pull/4217

| | |
|---|---|
| Base | `2.x` · +46 −0, 2 files (`delegating.adoc` + changelog) · CI green |
| Linked issue | #4181 |
| Your involvement | you pushed back; author updated |

### Verdict: ✅ docs-only, and your pushback is what made it worth merging.

Your comment did the substantive work here:

> The section only covers availability. The other half of what @ppkarwasz asked
> for on #4181 is the threat model side... I tried it on 2.26.1 and with
> `fileName="logs/${ctx:userId}.log"` and a key of `../../../../tmp/x` it writes
> `/tmp/x.log` and doesn't report an error. One thing to watch: the key has to be
> a whole path segment for that to happen. `logs/user-${ctx:userId}.log` just
> fails to open, so the example matters.

That is a reproduction, not an opinion — and the path-segment caveat is the kind
of detail that makes documentation correct rather than merely alarming.

The author's response covers all of it: split into **Resource allocation** and
**Threat model**, notes that default routes build subordinate appenders at
runtime so lookups can still carry untrusted data, adds both examples including
the contrast case, and links the configuration-sources threat model.

### What is left

Only the mechanical check this repo's own rules call for, since the change is
AsciiDoc in the Antora site:

```bash
cd ~/apache/logging-log4j2
./mvnw -pl src/site site   # or the project's site build target
```

Render the page and confirm every `xref` resolves — **page and anchor**. The PR
adds a link to the security threat-model page, which is a cross-module xref and
the kind that resolves in preview but breaks in the built site.

Beyond that: confirm `ppkarwasz` agrees #4181 is now fully answered, since it was
their ask and the issue should close with the merge.

### Repro

Already done — by you, on 2.26.1, in the PR thread. Worth capturing under
`docs/evidence/` if #4181 or this page is ever revisited, since the
whole-path-segment distinction is easy to lose.

---

## ── paste-ready comment for #4185 ──

Re-reviewing since the branch changed after my approval — folding the
`AppenderDynamicMBean` `setLayout` check in from #4219 was the right call, and
thanks for the changelog entries.

The new guard is consistent with the `addAppender` one, and both are safe
defensive checks on a reflective instantiation that can legitimately return
null.

One question before I re-approve: `setLayout` returns the string
`"Could not instantiate layout class."` from `invoke()`. A JMX client cannot
tell that apart from a successful result — should it throw an `MBeanException`
instead, or is returning a diagnostic string the established convention for this
MBean? Happy either way, I just want it to be deliberate.

## ── paste-ready comment for #4217 ──

The update covers what I raised — splitting resource allocation from the threat
model, and the whole-path-segment contrast between
`fileName="logs/${ctx:userId}.log"` and `logs/user-${ctx:userId}.log` is the
detail that makes this accurate rather than just alarming. Thanks.

One thing before merge: could you confirm the built site renders and the new
cross-page link to the configuration-sources threat model resolves to both the
page and the anchor? Cross-module `xref`s tend to look fine in preview and break
in the built site.

@ppkarwasz — does this fully answer #4181, or is there more of the threat model
you wanted covered?
