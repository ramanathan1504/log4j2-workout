# Triaging a Log4j security advisory

Scanner advisories against Log4j (Sonatype, Snyk, Mend, GitHub, a customer's
SCA report) arrive again and again, and most of them turn out to be known
non-findings. Use this flow to reach a verdict once, back it with a measurement,
and give the same answer every time the advisory comes back.

```
advisory ─▶ 1 pin the claim ─▶ 2 official record? ─▶ 3 threat model ─▶ 4 FAQ
                                                                          │
           7 file it here ◀── 6 verdict + response ◀── 5 measure ◀────────┘
```

Each advisory gets one folder, `security/advisories/<advisory-id>/`, copied
from [`advisories/_template/`](advisories/_template/README.md).
[`sonatype-2026-006746`](advisories/sonatype-2026-006746/README.md) is the
worked sample.

The authoritative sources are listed below. Re-read them each time, because
they change:

| Source | URL |
|---|---|
| Threat model | <https://logging.apache.org/security.html#threat-common> |
| Security FAQ (known non-findings) | <https://logging.apache.org/security/faq.html> |
| Known vulnerabilities | <https://logging.apache.org/security.html#vulnerabilities> |
| VDR, machine-readable | <https://logging.apache.org/cyclonedx/vdr.xml> |
| Reporting | <https://logging.apache.org/security.html#reporting> |

---

## 1. Pin the claim

Write down exactly what the advisory says, without interpreting it yet:

- advisory ID, CVE if any, publish date, score and vector
- the artifact (`log4j-api`, `log4j-core`, …) and the version range, or
  "not stated"
- **the vulnerable methods it lists.** These tell you what the advisory is
  really about. Scanners often leave out the prose and give only the methods.
- the references, especially the upstream issues and PRs

Treat any PoC repository listed as a reference as untrusted. Read it if you
need to, but don't run it. You'll write your own probe in step 5.

## 2. Is there an official record?

```bash
curl -s https://logging.apache.org/cyclonedx/vdr.xml | grep -oE "<id>CVE-[0-9-]+</id>"
gh issue view <n> -R apache/logging-log4j2 --comments
gh pr list -R apache/logging-log4j2 --state all --search "<n> in:body"
```

If the CVE appears in the VDR, the project accepted the issue as a
vulnerability, and the VDR gives the fixed version. Go to step 6. If it doesn't
appear, the advisory is the vendor's own classification. Continue to step 3.

## 3. Map it onto the threat model

Ask one question: **which input does the attacker have to control?** Then look
the input up in this table:

| The attacker must control… | Threat model says | In scope? |
|---|---|---|
| a log message, a log parameter's `toString()`, a `ThreadContext` value, an exception's message or stack trace | content, untrusted (`#threat-common-sources-content`) | **yes** |
| a configuration file, a configuration property, an environment variable | configuration, trusted (`#threat-common-sources-configuration`) | no |
| a logger name, a marker, a level, a layout pattern in source code | structural identifier, trusted (`#threat-common-sources-structural`) | no |
| the sink: the database, the SMTP server or the HTTP endpoint the appender writes to | sink, trusted; an *active* sink is out of scope (`#threat-common-sinks`) | no |
| code running in the same JVM | adversary capability not listed (`#threat-common-adversary`) | no |
| bytes that the **application** passes to `ObjectInputStream` | "Log4j does not deserialize data from any source as part of its normal operation" | no, the application owns that trust decision |

The threat model sets this rule: *a report that needs a capability the model
doesn't list is not a vulnerability.* If the only path runs through a "no" row,
the verdict is a non-finding. Step 5 still applies, because it measures what the
application actually exposes.

## 4. Check the FAQ

These are the known non-findings. If the advisory matches one, cite its anchor
in your verdict.

| Advisory shape | FAQ anchor |
|---|---|
| CRLF or log injection through `PatternLayout`, Syslog or RFC 5424 | `#crlf-injection` |
| a file appender writes to an arbitrary path | `#path-traversal` |
| deserialization, `Serializable`, `FilteredObjectInputStream`, an allow-list bypass | `#deserialization` |

## 5. Measure it

A verdict without a measurement is an opinion. Write a small standalone probe
under `probe/` in the advisory's folder:

- **Use a canary, not a gadget.** Use a harmless class that only records that it
  ran, and that sits outside every allow-list. The probe proves reachability,
  never exploitation.
- **Run it across versions.** Include the last release, the next patch snapshot
  and the next minor snapshot (`oss run list --versions`). You need both halves:
  the version where the behaviour stops, and confirmation that nothing else
  changed.
- **Include a control.** Show that the guard works on the direct path, so a
  bypass result means something.
- **Include the application's view.** Show what a plain `ObjectInputStream`,
  the default config or the default layout does. This is usually the case that
  decides the verdict.
- **Include the mitigation.** Run the probe again with the documented good
  practice turned on (`PROBE_JVM_ARGS`), so the advice is measured, not
  asserted.
- Keep the probe's output under `output/` as evidence.

## 6. Verdict and response

Choose exactly one verdict:

| Verdict | Meaning | What to do |
|---|---|---|
| **Vulnerability** | It's in scope (step 3) and reachable (step 5) | Report it privately to `security@logging.apache.org` in plain text, one finding per email, with a subject that starts with `[SECURITY]`. **Never file it as a public issue or PR.** |
| **Hardening** | It's out of scope, but a Log4j control doesn't do what its Javadoc says | It's safe to fix in public. Upstream usually already has a PR. Link to it. |
| **Non-finding** | It's out of scope and matches the FAQ | Cite the FAQ anchor. Suppress the advisory with a justification. |

Write the scanner suppression as a VEX statement, using a CycloneDX
`analysis.justification`:

| Situation in *your* application | Justification |
|---|---|
| the vulnerable method is never called (no `ObjectInputStream` on log events) | `code_not_reachable` |
| it's reachable only through trusted configuration | `requires_configuration` |
| a JVM-wide `jdk.serialFilter` or `ObjectInputFilter` is set | `protected_at_runtime` |
| the input reaches the JVM only through an authenticated peer | `protected_at_perimeter` |

## 7. File it

Fill in the advisory folder's `README.md` from the template. Put the verdict on
the first line, followed by the evidence table and the response text. The next
time the same advisory, or a renamed copy of it, shows up, the answer is a link
to that file.

---

## Good practice, measured where possible

The threat model draws the line between what Log4j does and what the
application has to do. These are the application's side of that line:

- **Don't deserialize untrusted data,** with or without Log4j classes in the
  stream. `FilteredObjectInputStream` is defence in depth, not a trust boundary
  (FAQ `#deserialization-filtered-object-input-stream`).
- **If you must deserialize, set a JVM-wide allow-list filter,** for example
  `-Djdk.serialFilter=…;!*`. It applies to every stream, including streams
  opened inside a library. The sample measures it blocking the bypass on
  2.26.1.
- **Ship events with a structured layout over TLS** (`JsonTemplateLayout`),
  not `SerializedLayout`. `SerializedLayout` has been deprecated since 2.9.
- **Use a structured layout whenever something parses the logs.**
  `PatternLayout` doesn't sanitize, by design (FAQ `#crlf-injection`).
- **Never build file paths or routes from lookups that can carry user data**
  (FAQ `#path-traversal-problematic-contexts`).
- **Treat configuration as code.** Anyone who can write it is already trusted.
  Protect the file, not the parser.
- **Stay on the latest 2.x release,** and check the VDR rather than the
  scanner's version range.
