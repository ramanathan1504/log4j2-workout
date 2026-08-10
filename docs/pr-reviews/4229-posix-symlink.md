# PR #4229 — do not apply POSIX file attributes through symbolic links

https://github.com/apache/logging-log4j2/pull/4229

| | |
|---|---|
| Author | `jmestwa-coder` |
| Prior merged PRs in this repo | **0** (3 open, all security-shaped) |
| Base | `2.x` |
| Size | +121 −1, 5 files |
| Linked issue | **none** |
| CI | green |

## Verdict: ⚠️ reasonable hardening, but not the vulnerability the description implies.

## Is it really needed?

The technical claim is accurate. `FileUtils.defineFilePosixAttributeView` looked
the view up without `NOFOLLOW_LINKS`, so `setPermissions` / `setOwner` / `setGroup`
landed on the link *target*; and `walkFileTree` does hand symlinks to `visitFile`,
so the `followLinks="false"` default documented on `AbstractPathAction` did not
prevent the action from touching them. Both statements check out against the tree.

Where I would push back is on the framing. The PR body reads:

> a link planted in `basePath` whose name matches the `PathCondition` glob
> redirects the chmod/chown onto any file the process can reach

To plant that link, the attacker needs **write access to your log directory**. An
attacker with write access to the directory your application is actively logging
into has a much larger problem available to them than a chmod redirect. This is
not a privilege boundary Log4j is defending; it is defence-in-depth against a
mis-shared directory.

That matters for how it gets merged: as **hardening**, with a normal changelog
entry, not as a security fix with a CVE-shaped narrative. Given this author has
three open security-flavoured PRs and no merged history, being explicit about
that line is worth doing now.

## The change itself is right

```java
-        final PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
+        final PosixFileAttributeView view =
+                Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
```

plus the visitor skip:

```java
if (attrs.isSymbolicLink()) {
    LOGGER.trace("Not defining POSIX attribute on symbolic link {}", file);
    return FileVisitResult.CONTINUE;
}
```

Both are the correct mechanism. The `CONTINUE` rather than a throw is the right
choice — a planted link should not abort a rollover.

## Behaviour change to call out

Anyone who **deliberately** keeps symlinks in their log directory and expects
`PosixViewAttribute` to apply permissions through them loses that today. Unusual,
but it is a silent behaviour change and the changelog entry
(`fix_posix_view_attribute_symlink.xml`) does not mention it. It reads:

> Stop `PosixViewAttribute` from applying permissions and ownership through symbolic links

which is accurate but does not tell a reader that their working setup may change.

## Test quality

`FileUtilsTest.testDefineFilePosixAttributeViewDoesNotFollowSymbolicLinks`
wraps the call in a bare `try { … } catch (final IOException expected) { }`, then
asserts the target is untouched. That assertion passes whether or not the fix is
present *if* the platform happens to reject the operation for an unrelated
reason — the test cannot distinguish "NOFOLLOW_LINKS worked" from "this
filesystem refuses link chmod anyway". It is a weak test in the way that
security tests most often are.

`PosixViewAttributeActionTest.testSymbolicLinksAreNotFollowed` is the better of
the two — it asserts both halves (regular file *was* updated, outsider *was not*),
so it would fail if the visitor skip over-reached and skipped everything.

Worth also confirming the file already imports `assertEquals`; the diff adds only
`assumeTrue` to the static imports in `FileUtilsTest`. CI is green, so it does —
just noting it as the kind of thing to check when a test-only hunk is small.

## Repro

The bench already reaches this action — `configs/xml/rollover-advanced.xml` has a
`PosixViewAttribute` block, and it is configured with **exactly the arguments the
PR says are ineffective**:

```xml
<PosixViewAttribute basePath="${dir}/posix" maxDepth="1"
                    followLinks="false"
                    filePermissions="rw-r-----">
```

`appender-file-variants.xml` has one too, and both have json/properties/yaml
siblings. So this is a bench cell, not a hand-rolled script.

The repro is that config plus a planted link, which the bench does not do on its
own:

```bash
# 1. baseline against a release, before any --install
mkdir -p logs/rollover-advanced/posix
printf secret > /tmp/outsider.txt && chmod 600 /tmp/outsider.txt
ln -s /tmp/outsider.txt logs/rollover-advanced/posix/app-99.log

./bench run core-java --config xml/rollover-advanced --log4j 2.26.1 rollover

stat -f '%Sp' /tmp/outsider.txt
#   before the PR: -rw-r-----   (the chmod followed the link)
#   after  the PR: -rw-------   (untouched)
```

The link name has to match whatever the action's `PathCondition` accepts — check
the `IfFileName glob` in that config block before choosing `app-99.log`.

**Not yet run.** Unlike #4234 this one writes outside the repository (`/tmp`) and
depends on the umask of the running user, so it is worth doing deliberately
rather than as part of a sweep.

---

## ── paste-ready comment ──

The technical observation is correct on both counts — `defineFilePosixAttributeView`
did resolve the view without `NOFOLLOW_LINKS`, and `walkFileTree` does hand
symlinks to `visitFile` regardless of the `followLinks="false"` default, so the
attributes landed on the target. Requesting the view with `NOFOLLOW_LINKS` and
skipping links in the visitor is the right mechanism, and `CONTINUE` rather than
aborting the rollover is the right call.

I would frame this differently in the changelog, though. Planting the link
requires write access to the directory the application is actively logging into
— at which point the attacker has better options than redirecting a chmod. So
this reads to me as defence-in-depth hardening rather than a privilege-boundary
fix, and I would rather the entry said so than have it read as a security
advisory.

Two smaller points:

- It is a silent behaviour change for anyone who deliberately keeps symlinks in
  their log directory and expects permissions to be applied through them. Could
  the changelog entry mention that?
- `testDefineFilePosixAttributeViewDoesNotFollowSymbolicLinks` swallows the
  `IOException` and then asserts the target is unchanged, so it passes whether or
  not the fix is present on any filesystem that refuses link chmod for its own
  reasons. `PosixViewAttributeActionTest.testSymbolicLinksAreNotFollowed` is the
  stronger test — it asserts both that the regular file *was* updated and that the
  outsider *was not*, so it would also catch the visitor skipping too much. Could
  the `FileUtilsTest` one assert on the outcome rather than tolerate the exception?
