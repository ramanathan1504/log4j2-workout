# PR #4234 — Add `ZstdCompressAction` to support configurable Zstandard compression levels

https://github.com/apache/logging-log4j2/pull/4234

| | |
|---|---|
| Author | `katstack` (Byeong-Gil Ahn) |
| Prior merged PRs in this repo | **0** — this is their first |
| Base | `2.x` |
| Size | +349 −4, 5 files |
| Linked issue | **none** — references merged PR #1514, merged PR #2921, discussion #2950 |
| CI | green |

## Verdict: 🛑 regression. Do not merge as-is.

## Is it really needed?

Partly. The stated gap is real: `FileExtension.ZSTD` currently routes through
`CommonsCompressAction("zstd", …)`, which has no compression-level parameter, so
`compressionLevel` is silently ignored for `.zst` rollovers. Making it
configurable is a reasonable feature.

But note what the author's own references say: **#2921 ("Move compression into
new `log4j-compress` module") is already merged on `main`**, and on `main`
`compressionLevel` has been replaced by a generic `compressionOptions` map. The
author acknowledges this in the PR body. So this adds a new **public** plugin
class (`ZstdCompressAction`) to `2.x` that has no counterpart on `main` — a new
2.x-only public API, in a branch that is in maintenance, which then has to be
migrated or deprecated. That is a maintainer decision, not a contributor one, and
it was not asked for in an issue.

No issue was filed, no maintainer asked for this. It is a self-selected feature
on a first contribution — the classic shape of portfolio work.

## The blocking finding

`compressionLevel` is a **shared** rollover attribute, not a zstd one.

`DefaultRolloverStrategy.java:100-101,146-147`:

```java
@PluginBuilderAttribute("compressionLevel")
private String compressionLevelStr;
...
final int compressionLevel = Integers.parseInt(trimmedCompressionLevelStr, Deflater.DEFAULT_COMPRESSION);
```

and it is documented at `DefaultRolloverStrategy.java:218` and `:376` as:

> The compression level, **0 (less) through 9 (more)**; applies only to ZIP files.

That value is then handed to *whatever* extension the file pattern selects
(`DefaultRolloverStrategy.java:686,690`):

```java
compressAction = fileExtension.createCompressAction(renameTo, compressedName, true, compressionLevel);
```

**Today**, a config with `compressionLevel="0"` and a `.zst` file pattern works —
`CommonsCompressAction` ignores the level entirely.

**After this PR**, `FileExtension.ZSTD.createCompressAction` maps only `-1` to the
zstd default and passes everything else straight into the new constructor, which
validates against `[1, 22]` and throws:

```java
throw new IllegalArgumentException("Zstd compression level must be in the range [1, 22], got: 0");
```

So `compressionLevel="0"` — a documented, legal, currently-working value — now
throws `IllegalArgumentException` **from inside the rollover**, not at
configuration time. The rollover aborts. The user gets no compressed file and,
depending on where the throw lands, a log file that stops rolling.

`compressionLevel="0"` is not a hypothetical. It is the documented **minimum** for
the attribute, and it is exactly what someone who switched a working `.zip`
rollover to `.zst` would leave in place.

Same class of break for any negative value other than `-1`, and for anything
above 22.

### Why the PR's own reasoning does not cover this

The PR body argues carefully about why `-1` cannot mean "zstd fast level -1",
and it handles `-1`. It never considers that the *other* out-of-range values were
also previously accepted and ignored. The validation is correct for a
zstd-specific attribute; it is wrong for an attribute shared with zip and gz.

## Non-blocking

- `checkCompressionLevel` runs twice on the instance path — once in the
  constructor, once in the static `execute(…)` it delegates to.
- The test that pins `ZstdConstants.ZSTD_CLEVEL_MAX` will fail the build on a
  commons-compress upgrade that widens the range. The PR calls this deliberate
  ("fails loudly"); it is also a build break on a routine dependabot bump. Worth a
  maintainer opinion either way.
- `ZstdConstants` is being imported into `FileExtension`, which pulls a
  commons-compress type into a class that previously referenced compression
  backends only by string. Check that this does not turn an optional dependency
  into a hard one at class-load time for users who never use zstd.

## Repro — built and run

`configs/xml/repro-zstd-level.xml`, three appenders so one run separates "zstd is
broken" from "level 0 is broken":

| Appender | Suffix | `compressionLevel` | Role |
|---|---|---|---|
| `ZstdLevelZero` | `.zst` | `0` | **the regression case** |
| `ZstdDefault` | `.zst` | unset (`-1`) | control — the sentinel #4234 does map |
| `ZipLevelZero` | `.zip` | `0` | control — `0` is legal and honoured for zip |

Standalone repro: `repros/pr-4234/` (zip, per-version output, matrix).

### Baseline — measured 2026-08-08, before any `--install`

```bash
./bench repro 4234 --pr --config xml/repro-zstd-level --scenario rollover \
  --log4j 2.24.1 --log4j 2.25.5 --log4j 2.26.0 --log4j 2.26.1
```

| Log4j | `ZstdLevelZero` | Result |
|---|---|:---:|
| `2.24.1` | 5 × `.zst` written | ✅ PASS |
| `2.25.5` | 5 × `.zst` written | ✅ PASS |
| `2.26.0` | 5 × `.zst` written | ✅ PASS |
| `2.26.1` | 5 × `.zst` written | ✅ PASS |

Archives verified as genuinely compressed, not empty shells:

```
$ zstd -t logs/repro-zstd-level/zstd-level-zero/app-1.log.zst
logs/…/app-1.log.zst: 8320 bytes
$ unzip -t logs/repro-zstd-level/zip-level-zero/app-1.log.zip
No errors detected in compressed data.
```

**So `compressionLevel="0"` on a `.zst` rollover works on every released 2.x.**
That is the property this PR breaks. Note the polarity: for this repro PASS is
the *baseline*, and the PR is expected to turn `ZstdLevelZero` into a failure
while the two controls keep passing.

### The "after" half — not yet run

Requires publishing the PR branch over `2.27.0-SNAPSHOT`:

```bash
./bench pr 4234 --checkout --install
./bench run core-java --config xml/repro-zstd-level rollover
```

Baseline was deliberately taken against releases first, so this is now safe to
run. Expected: `ZstdLevelZero` throws `IllegalArgumentException: Zstd
compression level must be in the range [1, 22], got: 0` during rollover, while
`ZstdDefault` and `ZipLevelZero` still pass.

---

## ── paste-ready comment ──

> Thanks for the detailed write-up — the reasoning about the `-1` sentinel is
> sound, and the gap you identified (that `compressionLevel` is silently ignored
> for `.zst`) is real.
>
> There is a compatibility problem, though. `compressionLevel` is not a
> zstd-specific attribute — it is the shared `DefaultRolloverStrategy` attribute,
> documented in that class as *"The compression level, 0 (less) through 9 (more)"*
> and passed unchanged to whichever `FileExtension` the file pattern selects.
>
> Today a configuration like this works, because `CommonsCompressAction` ignores
> the level:
>
> ```xml
> <RollingFile name="R" fileName="logs/app.log" filePattern="logs/app-%i.log.zst">
>   <SizeBasedTriggeringPolicy size="1KB"/>
>   <DefaultRolloverStrategy max="3" compressionLevel="0"/>
> </RollingFile>
> ```
>
> After this PR, `FileExtension.ZSTD.createCompressAction` maps only `-1`, so `0`
> reaches `checkCompressionLevel` and throws
> `IllegalArgumentException: Zstd compression level must be in the range [1, 22], got: 0`
> — from inside the rollover, not at configuration time. The rollover fails for a
> configuration that is valid and working on every released 2.x.
>
> `0` is the documented minimum for the attribute, so this is likely to be a real
> user's config after switching a `.zip` pattern to `.zst`.
>
> Could the mapping in `FileExtension.ZSTD` clamp-and-warn instead of letting
> out-of-range values reach the constructor? Something like: values outside
> `[1, ZSTD_CLEVEL_MAX]` fall back to the zstd default with a `StatusLogger` warning,
> preserving today's "ignored" behaviour rather than turning it into a failure.
>
> Separately, and this is a question for the maintainers rather than for you:
> `ZstdCompressAction` would be a new public class on `2.x` only, and #2921 has
> already replaced `compressionLevel` with `compressionOptions` on `main`. Worth
> confirming the 2.x-only API addition is wanted before investing more in this.
