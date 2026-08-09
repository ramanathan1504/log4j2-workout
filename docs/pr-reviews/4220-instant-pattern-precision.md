# PR #4220 — `InstantPatternLegacyFormatter` precision for legacy `n` patterns

https://github.com/apache/logging-log4j2/pull/4220

| | |
|---|---|
| Author | `SebTardif` |
| Prior merged PRs in this repo | **3** (#4125, #4127, #4172) — established |
| Base | `2.x` |
| Size | +73 −1, 4 files |
| Linked issue | **#3816 — filed by `vy`, a Log4j PMC member** |
| CI | green |
| Reviews | **none — this is the only one of Seb's four nobody has looked at** |

## Verdict: ⚠️ correct, but it implements the approach the issue author argued *against*. Raise that before reviewing the code.

## The thing to settle first

Issue #3816 is not a bare bug report. `vy` filed it *with a proposed resolution*,
and named the alternative they did not want:

> In `3799799ca77b1724e57214927d76ef0d1cff055a`, @ppkarwasz fixes this by
> converting the legacy pattern back to `DateTimeFormatter`-compatible (i.e.,
> modern) variant before passing it to `InstantPatternDynamicFormatter`.
> **I am personally in favor of copying `InstantPatternDynamicFormatter::patternPrecision`
> to `InstantPatternLegacyFormatter`, and adapting it, since this will ensure
> legacy code remains an island.**

PR #4220 does **pattern conversion** — `pattern.replace('n', 'S')` — which is the
approach `vy` explicitly declined, in a cruder form than the `ppkarwasz` commit
the issue already references.

So there are three things a reviewer should establish before debating the
`replace` call:

1. Did `ppkarwasz`'s commit land? It is **not present** in the local `2.x` clone
   at `04c93c1d33`, and `git cat-file` cannot resolve the SHA — it appears to
   have lived on the #3789 branch and never merged. Worth confirming upstream.
2. Does `vy` still prefer the "legacy code remains an island" approach? If so
   this PR is the wrong shape regardless of whether it works.
3. If conversion *is* acceptable, should it reuse the existing commit rather than
   a new one-line replace?

This is the same failure mode that closed **your own #4213** — duplicating work
already done elsewhere — and it is worth flagging kindly, because the author
almost certainly did not read the issue thread to the end.

## The code itself is narrowly correct

```java
this.precision = new InstantPatternDynamicFormatter(pattern.replace('n', 'S'), locale, timeZone).getPrecision();
```

A global, unconditional character replace looks alarming. I checked whether it
can misfire, and it largely cannot — for one specific reason worth stating in the
PR, because it is the whole safety argument:

**The rewritten pattern is used only to compute `precision`.** The next three
lines keep the original:

```java
this.pattern = pattern;
this.locale = locale;
this.timeZone = timeZone;
this.formatter = createFormatter(pattern, locale, timeZone);   // original
```

So the blast radius is a wrong `ChronoUnit`, never wrong output.

On the quoting concern: `InstantPatternDynamicFormatter` does parse single-quoted
literals (lines 254–295), and an `n` inside quotes stays a literal after becoming
an `S`. Literals do not contribute to precision either way, so
`"HH:mm:ss 'min'"` → `"HH:mm:ss 'miS'"` computes the same precision. Safe, but
by coincidence rather than by construction.

The semantics are right: legacy `n` is length-dependent (Log4j's own
`FixedDateFormat` extension), `DateTimeFormatter`'s `n` is always nano-of-second,
and `S` is length-dependent — so `nnnnnn` → `SSSSSS` → `MICROS` is the correct
mapping, and matches what the issue says is broken ("microseconds are currently
classified as nanoseconds").

## Impact is narrower than "bug" suggests

`getPrecision()` feeds `InstantPatternThreadLocalCachedFormatter:67` and
`InstantPatternFormatter:138` — it drives **cache invalidation**. Reporting
`NANOS` when the pattern actually resolves to `MICROS` is *over*-conservative:
the cache invalidates more often than needed. That is a throughput cost, not
stale or wrong timestamps.

Worth saying on the PR, because it affects urgency and the changelog wording.

## Test quality — good

The addition to `NamedInstantPatternTest.compatibilityOfLegacyPattern`:

```java
assertThat(legacyFormatter.getPrecision()).isEqualTo(formatter.getPrecision());
```

is the best part of the PR. It is parameterised over every `NamedInstantPattern`,
so it is a broad regression net rather than two hand-picked cases, and it would
catch a future divergence between the legacy and modern paths.

## Non-blocking

- A comment explaining *why* the replace is safe (precision-only) belongs next to
  it. Without it, the next reader has to re-derive the argument I just did.
- A pattern mixing both letters — `"HH:mm:ss,SSSnnnnnn"` — maps to nine `S` and
  therefore `NANOS`. Contrived, but it is the one case where the naive replace is
  arguably wrong.

## Repro

None built. The observable is a `ChronoUnit` returned from an internal class, not
appender output, so a bench cell adds nothing over the unit tests the PR already
ships. `docs/FEATURE-MATRIX.md` timestamp coverage exercises the *rendering* of
these patterns, which this PR deliberately does not change.

---

## ── paste-ready comment ──

> Thanks for this — and the addition to `NamedInstantPatternTest` is the part I
> like most, since it holds the legacy and modern paths to the same precision
> across every named pattern rather than two hand-picked cases.
>
> Before we go into the code, one process point. #3816 was filed by @vy with a
> preferred resolution already stated:
>
> > I am personally in favor of copying `InstantPatternDynamicFormatter::patternPrecision`
> > to `InstantPatternLegacyFormatter`, and adapting it, since this will ensure
> > legacy code remains an island.
>
> and it notes that @ppkarwasz had already fixed it by pattern conversion in
> `3799799`. This PR takes the conversion route — the one @vy declined. I could
> not find `3799799` in `2.x`, so I think it never landed, but the design question
> is still open and worth settling before the implementation is reviewed.
>
> @vy — do you still want the precision logic copied into the legacy formatter, or
> is conversion acceptable now?
>
> On the code, in case conversion is the answer: `pattern.replace('n', 'S')` reads
> alarming, but it is safe, and I think the PR should say why. The rewritten
> pattern is used **only** for `precision` — `this.pattern` and `createFormatter(...)`
> both keep the original — so the worst case is a wrong `ChronoUnit`, never wrong
> output. Quoted literals are precision-neutral, so `'min'` → `'miS'` computes the
> same thing. A comment to that effect would save the next reader deriving it.
>
> One last thing for the changelog: `getPrecision()` drives cache invalidation
> (`InstantPatternThreadLocalCachedFormatter`), and classifying micros as nanos is
> over-conservative — so the symptom is reduced caching, not incorrect timestamps.
> Worth wording it that way so users can judge urgency.
