# <advisory-id>: <advisory title>

**Verdict: <Vulnerability | Hardening | Non-finding>.** <One sentence explaining why.>

Triaged <YYYY-MM-DD> using the flow in [`security/README.md`](../../README.md).

## 1. The claim

| Field | Value |
|---|---|
| Advisory | <URL> |
| CVE | <CVE-… / none> |
| Score | <CVSS and vector> |
| Artifact / versions | <as stated, or "not stated"> |
| Vulnerable methods | <list> |
| Upstream references | <issues / PRs> |

## 2. Official record

- In the VDR: <yes, with fixed version / no>
- Upstream status: <issue state, PRs, milestone>

## 3. Threat model

- The attacker must control: <input>
- Threat-model row: <content / configuration / structural / sink / same-process / application deserialization>
- In scope: <yes / no>

## 4. FAQ

- <anchor, or "no match">

## 5. Measurement

```bash
probe/run.sh <versions>
PROBE_JVM_ARGS='<mitigation>' probe/run.sh <versions>
```

| Case | <v1> | <v2> | <v3> |
|---|:---:|:---:|:---:|
| control | | | |
| advisory path | | | |
| application view | | | |
| with mitigation | | | |

Raw output: [`output/`](output/)

## 6. Response

Scanner suppression (VEX): `<justification>`. <One line explaining it.>

Text for anyone who asks:

> <Three or four sentences: what the advisory claims, why it is or isn't a
> vulnerability under the threat model, what the application must do, and
> which version contains any hardening.>
