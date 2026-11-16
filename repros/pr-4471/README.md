# PR #4471 — Escape the `newLineEscape` replacement in `Rfc5424Layout` SD values

https://github.com/apache/logging-log4j2/pull/4471

`Rfc5424Layout.appendMap` built each structured-data value as
`escapeNewlines(escapeParamValue(value), escapeNewLine)`: the RFC 5424 PARAM-VALUE escaping (`"`, `\`, `]` → `\"`, `\\`, `\]`)
ran first, then CR/LF were replaced, so any special character in the replacement itself went out unescaped.
With `newLineEscape="\n"` an MDC value `A<LF>B` became `k="A\nB"` (an invalid RFC 5424 escape); with `]` it became
`k="A]B"`, which closes the SD element early. The PR swaps the order to `escapeParamValue(escapeNewlines(value, …))`.

## Setup

Three `File` appenders with `Rfc5424Layout includeMDC="true"` and `newLineEscape` = `\n`, `]`, `#012`.
One event with `ThreadContext` `mdcKey=A<LF>B`, one `StructuredDataMessage` with `sdKey=C<CR><LF>D`.
`Repro.java` extracts the raw `key="..."` text from each file.

## Commands

```bash
cd log4j-pr-4471-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2x-head
./run.sh <scratchpad>/wt/jars-pr-4471 pr-4471-head
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `\n`, MDC | `A\\nB` | `A\nB` | **FAIL** |
| 2.26.1 | `\n`, StructuredDataMessage | `C\\nD` | `C\nD` | **FAIL** |
| 2.26.1 | `]`, MDC | `A\]B` | `A]B` | **FAIL** |
| 2.26.1 | `#012`, MDC (control) | `A#012B` | `A#012B` | PASS |
| 2.x HEAD ea28f5a78b | same four cases | as above | same as 2.26.1 | FAIL ×3, control PASS |
| PR head 678feb6c3f | `\n`, MDC | `A\\nB` | `A\\nB` | PASS |
| PR head 678feb6c3f | `\n`, StructuredDataMessage | `C\\nD` | `C\\nD` | PASS |
| PR head 678feb6c3f | `]`, MDC | `A\]B` | `A\]B` | PASS |
| PR head 678feb6c3f | `#012`, MDC (control) | `A#012B` | `A#012B` | PASS |

Full output in `output/<label>/result.txt`, with the log files.

Notes: as the note says, a receiver that does not decode RFC 5424 escapes now sees `\\n` instead of `\n`.
