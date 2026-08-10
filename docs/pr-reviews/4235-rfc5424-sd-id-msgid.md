# PR #4235 — sanitize SD-ID and MSGID in `Rfc5424Layout`

https://github.com/apache/logging-log4j2/pull/4235

| | |
|---|---|
| Author | `jmestwa-coder` |
| Prior merged PRs in this repo | **0** (3 open: #4198, #4229, #4235 — all security-shaped) |
| Base | `2.x` |
| Size | +120 −4, 3 files |
| Linked issue | none — references merged PR #4073 |
| CI | green |

## Verdict: ✅ the strongest PR in this batch. Take it.

## Is it really needed?

Yes, and unlike most of this batch it is not self-selected busywork — it
**finishes work the project already started**. #4073 sanitised `SD-PARAM-NAME` and
escaped `SD-PARAM-VALUE`. Two neighbouring fields of the same record were left
raw, and I confirmed both against the tree:

- `Rfc5424Layout.java:354` — `appendMessageId` appends
  `StructuredDataMessage.getType()` verbatim
- `Rfc5424Layout.java:566` — `formatStructuredElement` appends the SD-ID verbatim

Neither `setType` nor the `StructuredDataId` constructors validate characters —
they only check length. So a newline in the type terminates the syslog record and
whatever follows is parsed as a **separate** syslog message; a `]` in the SD-ID
closes the element early and the remainder is read as a second, caller-controlled
structured element.

The author's before/after in the PR body demonstrates exactly that, and it is
consistent with the existing rule: the same `?` replacement, applied to the same
production. `isParamNameCharacterValid` at `Rfc5424Layout.java:670` already uses
`c > 32 && c <= 126 && c != '=' && c != ']' && c != '"'`, and
`SD_NAME_EXCLUDED_CHARACTERS = "=]\""` is the same set expressed as a string.

RFC 5424 does give SD-ID and PARAM-NAME the same `SD-NAME` production, and MSGID
the wider printable-US-ASCII range, so the split into `sanitizeSdId` /
`sanitizeMsgId` is right rather than arbitrary.

## Things I checked and found sound

- `sanitizePrintableUsAscii` allocates lazily — no `StringBuilder` when the input
  is already clean. Same shape as the existing `escapeParamValue` right below it.
  Correct for a layout on the hot path.
- `@` survives sanitisation (`64`, not in the excluded set), so
  `name@32473` enterprise IDs are untouched. The tests confirm:
  `[validId@32473]` passes through unchanged.
- `formatStructuredElement` compares `mdcSdId.toString().equals(id)` against the
  **unsanitised** `id`, which is correct — the comparison is about identity, not
  about output.
- Lengths are deliberately left alone, which is right: `StructuredDataId` lets
  callers raise the 32-character limit, so truncating here would break working
  configurations.
- Tests are parameterised, cover the pass-through case as well as the injection
  cases, and the `formatExpectedMessage` overload was extended rather than
  duplicated.

## The one thing to raise

The PR describes a **record-injection** hole in a security-relevant field —
`StructuredDataMessage` is what audit logging uses — and it was filed as a public
PR with a public proof-of-concept, not through `security@apache.org`.

Whether that matters depends on the threat model, and here I think it genuinely
does not: the SD-ID and type are supplied by the *application*, not by a remote
user, so exploiting this requires an application that already passes untrusted
input as a structured-data identifier. That is a bug in the application. So
"hardening", not "vulnerability", and public is fine.

But it is worth saying so explicitly on the PR, because the author has three open
security-shaped PRs and a habit is being formed. Better to tell them the rule now
than after they publish something that does need the private channel.

## Non-blocking

- Consider whether `StructuredDataId` and `StructuredDataMessage.setType` should
  validate at construction time rather than leaving every layout to sanitise on
  output. That is a larger change and correctly out of scope here, but it is the
  actual root cause.
- `main` should get the same treatment; this is `2.x`-only.

## Repro

The bench reaches this already: `configs/xml/layout-remaining.xml` and
`configs/xml/appender-network.xml` both configure RFC5424/Syslog, and
`MessageScenario.java` already emits a `StructuredDataMessage` — which is the
message type this PR is about. `scripts/repro.sh:118` even greps for
`<Rfc5424Layout|<SyslogLayout` when deciding a repro's dependencies.

What is missing is a **hostile** structured-data message. The scenario emits a
well-formed one, so today's runs cannot show the injection.

The repro is a config using `layout-remaining`'s RFC5424 appender plus one
crafted event:

```java
new StructuredDataMessage(
    "a] [forged@1 user=\"root",   // SD-ID  -> closes the element early
    "login ok",                    // message
    "Audit\n<13>1 - - - - -")      // type -> MSGID, newline ends the record
```

Before: the emitted record splits into two syslog messages, the second entirely
caller-controlled. After: both fields are `?`-replaced and it stays one record.

**Not yet built.** It needs a scenario addition in `apps/core-java` rather than
just a config, because the hostile values have to come from Java. That is a
larger change than the other two repros, and it is arguably a bench improvement
worth having on its own — `MessageScenario` currently only proves the happy path
for `StructuredDataMessage`.

---

## ── paste-ready comment ──

This is a good find, and I like that it follows the rule #4073 already
established rather than inventing a new one — same `?` replacement, and the
SD-ID/PARAM-NAME split matches RFC 5424 giving both fields the same `SD-NAME`
production while MSGID keeps the wider printable-US-ASCII range.

I checked the two call sites and they are as you describe: `appendMessageId`
appends `StructuredDataMessage.getType()` raw and `formatStructuredElement`
appends the SD-ID raw, and neither `setType` nor the `StructuredDataId`
constructors validate characters — only length. Leaving the lengths alone is the
right call, since `StructuredDataId` lets callers raise the 32-character limit.

A few notes:

- `sanitizePrintableUsAscii` allocating lazily is the right shape for this path
  and matches `escapeParamValue` directly below it. Good.
- `@` is preserved, so `name@32473` enterprise IDs pass through — the
  `validId@32473` test case covers that.
- The root cause is arguably that `StructuredDataId` and
  `StructuredDataMessage#setType` validate length but not characters, leaving
  every layout to sanitise on output. Out of scope here, but worth an issue.
- `main` needs the same change.

One process note, since you have a few security-flavoured PRs open: for anything
where untrusted input can reach the affected field, the ASF process is to mail
`security@apache.org` first rather than open a public PR with a working
proof-of-concept — see https://logging.apache.org/security.html. I do not think
this one crosses that line, because the SD-ID and type come from the application
rather than from a remote user, so a public PR is right here. Flagging it so the
next one goes to the right place.
