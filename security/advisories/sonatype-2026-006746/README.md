# sonatype-2026-006746: "Log4j – Remote Code Execution (RCE)"

**Verdict: non-finding.** Exploiting it requires the application to deserialize
untrusted bytes, which the threat model places outside Log4j. The allow-list gap
the advisory describes is hardening, fixed for 2.26.2.

Triaged 2026-11-04 using the flow in [`security/README.md`](../../README.md).

## 1. The claim

| Field | Value |
|---|---|
| Advisory | <https://guide.sonatype.com/vulnerability/sonatype-2026-006746>, published 2026-08-26 |
| CVE | none |
| Score | 9.2 Critical, `CVSS:4.0/AV:N/AC:H/AT:P/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N`. EPSS 0%, not in KEV |
| Artifact / versions | not stated |
| Vulnerable methods | `core.impl.Log4jLogEvent$LogEventProxy` (`marshall`, `message`, `readResolve`, `writeObject`), `core.util.FilteredObjectInputStream` and `util.FilteredObjectInputStream` (`isAllowedByDefault`, `resolveClass`) |
| Upstream references | apache/logging-log4j2#4255 (closed, its body removed, archived at archive.ph/Xowgn), #4274, plus third-party PoC repositories (not run) |

The method list shows what the advisory is about.
`java.rmi.MarshalledObject` is on `FilteredObjectInputStream`'s allow-list.
`LogEventProxy` carries the message inside one, and `readResolve()` calls
`MarshalledObject.get()`, which opens a new, **unfiltered**
`ObjectInputStream`. So anything nested in the message gets past the allow-list.

## 2. Official record

- In the VDR (<https://logging.apache.org/cyclonedx/vdr.xml>, 24 CVEs as of
  2026-11-04): **no**.
- In #4255, a PMC member (`ppkarwasz`) classified it as "an independent
  discovery of a known security non-finding". They called the details accurate,
  but said the precondition doesn't change: the application has to feed
  untrusted bytes to an `ObjectInputStream`.
- Hardening merged on 2026-09-30 for milestone **2.26.2** (not released as of
  2026-11-04):
  - #4271 serializes the `LogEvent` message without `MarshalledObject`
  - #4365 applies the outer `ObjectInputFilter` to nested streams
  - #4269 rejects dynamic proxies in `FilteredObjectInputStream`
  - #4270, #4272 and #4274 cover test helpers, `SortedArrayStringMap`,
    `ObjectArrayMessage` and `ParameterizedMessage`

## 3. Threat model

- The attacker must control: the bytes the application reads with
  `ObjectInputStream.readObject()`.
- Threat-model row: application deserialization. "Log4j does not deserialize
  data from any source as part of its normal operation." The project also
  offers "no guarantee that deserializing a stream containing classes from
  these projects is safe."
- In scope: **no.** Log4j 2 has had no socket server since 2.9.0, the version
  after CVE-2017-5645. Nothing in Log4j reads these streams. Only an application
  that chose to read them does.

## 4. FAQ

- `#deserialization`: "CWE-502: Deserialization of Untrusted Data in Log4j"
- `#deserialization-filtered-object-input-stream`: the filter is defence in
  depth, not a security boundary. Allow-list bypasses are treated as hardening.

## 5. Measurement

The probe uses `Canary`, a harmless `Message` that isn't on any allow-list and
only records that its `readObject` ran. No gadget is involved.

```bash
probe/run.sh 2.24.1 2.25.5 2.26.1 2.26.2-SNAPSHOT 2.27.0-SNAPSHOT
PROBE_JVM_ARGS='-Djdk.serialFilter=org.apache.logging.log4j.**;java.**;!*' probe/run.sh 2.26.1 2.26.2-SNAPSHOT
```

`INSTANTIATED` means the canary's `readObject` ran. `BLOCKED` means it didn't.

| Case | 2.24.1 | 2.25.5 | 2.26.1 | 2.26.2-SNAPSHOT | 2.27.0-SNAPSHOT |
|---|:---:|:---:|:---:|:---:|:---:|
| `filtered-direct`: a canary read by `FilteredObjectInputStream` (control) | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED |
| `filtered-nested`: a log event carrying a canary, read by `FilteredObjectInputStream` (the advisory's path) | **INSTANTIATED** | **INSTANTIATED** | **INSTANTIATED** | BLOCKED | BLOCKED |
| `plain-ois`: the same event read by a plain `ObjectInputStream` (the application's view) | INSTANTIATED | INSTANTIATED | INSTANTIATED | INSTANTIATED | INSTANTIATED |
| any of the three, with `jdk.serialFilter` set | — | — | BLOCKED | BLOCKED | — |

Raw output: [`output/default.txt`](output/default.txt) and
[`output/jdk-serialfilter.txt`](output/jdk-serialfilter.txt). Measured on
OpenJDK 17.0.14.

What the table shows:

- **The advisory's mechanism is real** on 2.26.1 and earlier, and it's closed
  on the 2.26.2 line.
- **The fix doesn't change the verdict.** A plain `ObjectInputStream`
  instantiates the canary on every version, fixed or not, because that's what
  Java deserialization does. Whoever calls `readObject()` on untrusted bytes is
  exposed with or without Log4j on the classpath.
- **The good practice works on the "vulnerable" version.** A JVM-wide
  `jdk.serialFilter` blocks all three paths on 2.26.1, because it also applies
  to the stream that `MarshalledObject.get()` opens. On 2.26.1 the message
  degrades to `SimpleMessage`. On 2.26.2 the read is rejected.

## 6. Response

Scanner suppression (VEX): `code_not_reachable` if the application never calls
`readObject()` on serialized Log4j events. This is the normal case:
`SerializedLayout` is deprecated, and there's no socket server. Use
`protected_at_runtime` if it does deserialize and `jdk.serialFilter` is set.

Text for anyone who asks:

> sonatype-2026-006746 describes `java.rmi.MarshalledObject`, which is on
> Log4j's `FilteredObjectInputStream` allow-list, opening a nested unfiltered
> stream. That's accurate, but exploiting it requires your application to
> deserialize untrusted bytes. Log4j never does that itself, and Apache Logging
> Services lists it as a known non-finding
> (https://logging.apache.org/security/faq.html#deserialization). There's no
> CVE. If your application deserializes untrusted data, set a JVM-wide
> `jdk.serialFilter`, which blocks this path on every version. The allow-list
> gap itself is hardened in 2.26.2.
