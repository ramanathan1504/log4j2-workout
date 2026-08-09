# PR #4228 — Extend plugin processor message filtering and add `[Log4j]` prefixes

https://github.com/apache/logging-log4j2/pull/4228

| | |
|---|---|
| Author | `DragonFSKY` |
| Prior merged PRs in this repo | **0** (2 open: #4199, #4228) |
| Base | `2.x` |
| Size | +125 −38, 6 files |
| Linked issue | **#4225 — filed by `snicoll` (Spring), labelled `waiting-for-user`** |
| CI | green |

## Verdict: ⚠️ does not fix the reported problem. Send it back to the issue.

## Is it really needed?

The issue is real and comes from a credible reporter — `snicoll` is a Spring
maintainer, and "Consider making plugin builds less chatty" is a legitimate
complaint from someone who builds against Log4j constantly.

But look at the label: **`waiting-for-user`**. The maintainers have not agreed on
what the fix should be. And note that you already commented on that issue
yourself. This PR arrives with a design already chosen, before the design
discussion finished.

## The blocking finding: the default verbosity is unchanged

The issue asks for *less chatty* builds. This PR keeps
`minAllowedMessageKind = Diagnostic.Kind.NOTE` as the default in
`GraalVmProcessor`, matching `PluginProcessor.java:98`. The filter is:

```java
if (kind.ordinal() <= minAllowedMessageKind.ordinal()) {
```

`Diagnostic.Kind` ordinals are `ERROR(0) < WARNING(1) < MANDATORY_WARNING(2) <
NOTE(3) < OTHER(4)`. With the default at `NOTE(3)`, everything except `OTHER` is
still printed — including the per-module

```
GraalVmProcessor: writing GraalVM metadata for N Java classes to `…`.
```

which is emitted at `Diagnostic.Kind.NOTE` on every module that has plugins.
**That is the message snicoll is complaining about, and it still prints.**

So the PR's net effect on an unmodified build is: the same number of lines, each
now four characters longer because of the `[Log4j] ` prefix. A user who wants the
reported problem solved must now discover and pass
`-Alog4j.plugin.processor.minAllowedMessageKind=WARNING`.

That may well be the right design — opt-in quiet, rather than changing a default
— but it is the opposite of what the issue asked for, and the PR body does not
argue for it. It asserts "preserves the default `NOTE` threshold" as though that
were a virtue rather than the crux of the disagreement.

## Second finding: the prefix duplicates information already in the message

Most of the messages already name the processor:

```java
String.format("%s: writing GraalVM metadata for %d Java classes to `%s`.", PROCESSOR_NAME, …)
```

After the change these read:

```
[Log4j] GraalVmProcessor: writing GraalVM metadata for 12 Java classes to `…`.
```

Harmless, but if the goal is less noise, adding a prefix to every line is moving
in the wrong direction. Either the prefix or the `PROCESSOR_NAME` should go.

## Third: `MESSAGE_PREFIX` is duplicated

`private static final String MESSAGE_PREFIX = "[Log4j] ";` is now declared
separately in both `GraalVmProcessor` and `PluginProcessor`. Since the whole point
is a consistent brand on the output, that constant belongs in one place.

## What I checked and found fine

- `init()` calls `printMessage` before `minAllowedMessageKind` is assigned from
  options, but `super.init(processingEnv)` runs first so `processingEnv` is
  populated, and the default `NOTE` lets a `WARNING` through. The unrecognised-option
  warning does print. Correct.
- The `@SupportedOptions` addition on `GraalVmProcessor` is necessary — without it
  javac warns about an unrecognised `-A` option.
- The filter semantics exactly mirror `PluginProcessor.java:136,146`. Consistent.
- The refactor of the direct `processingEnv.getMessager().printMessage(…)` calls
  onto the helper is mechanical and correct throughout.

## Repro

`./bench coverage` will tell you whether any bench app compiles plugins through
the annotation processor — `custom-plugins` is the candidate. The observable is
build output, not runtime output, so this is a `mvn` observation rather than a
bench cell:

```bash
cd ~/apache/logging-log4j2
./mvnw -pl log4j-core clean compile 2>&1 | grep -c "writing GraalVM metadata"
# same count before and after the PR — that is the point
```

---

## ── paste-ready comment ──

> Thanks for the patch, and the refactor onto a single `printMessage` helper is
> clean.
>
> I do not think this closes #4225, though. The issue asks for the plugin build to
> be **less chatty**, and this change keeps `minAllowedMessageKind` at
> `Diagnostic.Kind.NOTE` by default. With `ERROR(0) < WARNING(1) <
> MANDATORY_WARNING(2) < NOTE(3) < OTHER(4)` and the filter at `kind.ordinal() <=
> minAllowedMessageKind.ordinal()`, the per-module
>
> ```
> GraalVmProcessor: writing GraalVM metadata for N Java classes to `…`.
> ```
>
> is `NOTE` and still prints. So an unmodified build emits the same number of
> lines as before, each now longer by the `[Log4j] ` prefix — unless the user
> discovers and passes `-Alog4j.plugin.processor.minAllowedMessageKind=WARNING`.
>
> That may be the design the maintainers want (opt-in quiet rather than a changed
> default), but it is the opposite of what was reported, and the issue is still
> labelled `waiting-for-user`, so the design question is open. Could we settle that
> on #4225 first? @ppkarwasz — should the metadata-written message drop to a lower
> kind, or is opt-in the intended answer?
>
> Two smaller things whichever way that goes:
>
> - `MESSAGE_PREFIX = "[Log4j] "` is now declared independently in both
>   `GraalVmProcessor` and `PluginProcessor`. For a constant whose whole purpose is
>   consistent branding, that should live in one place.
> - Most messages already start with `PROCESSOR_NAME`, so they now read `[Log4j]
>   GraalVmProcessor: …`. If the goal is less noise, one of the two should go.
