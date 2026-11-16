# PR #4472 — [main] Escape the `escapeNL` replacement in `Rfc5424Layout` SD values (#4471 port)

https://github.com/apache/logging-log4j2/pull/4472

Same defect as #4471, on `main`: `Rfc5424Layout.appendMap` builds each structured-data value as
`escapeNewlines(escapeSDParams(value), escapeNewLine)`, so `"`, `\` and `]` in the newline replacement go out
unescaped. With `\n` an MDC value `A<LF>B` becomes `k="A\nB"` (an invalid RFC 5424 escape); with `]` it becomes `k="A]B"`,
which closes the SD element early. The PR swaps the order of the value transform to
`escapeSDParams(escapeNewlines(value, …))`; the key line is unchanged.

## Setup

As in `repros/pr-4471`, with the 3.x attribute names: on `main` the `Rfc5424Layout` builder attributes are
`includeNL` and `escapeNL` (2.x `newLine` / `newLineEscape` are not recognised on the layout there).
Three `File` appenders with `escapeNL` = `\n`, `]`, `#012`; one MDC event `mdcKey=A<LF>B`, one
`StructuredDataMessage` `sdKey=C<CR><LF>D`.

## Commands

```bash
cd log4j-pr-4472-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-head
./run.sh <scratchpad>/wt/jars-pr-4472 pr-4472-head
```

`log4j-api` is 2.24.3 on every run; the configuration is passed with `log4j.configuration.location`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | `\n`, MDC | `A\\nB` | `A\nB` | **FAIL** |
| 3.0.0-beta3 | `\n`, StructuredDataMessage | `C\\nD` | `C\nD` | **FAIL** |
| 3.0.0-beta3 | `]`, MDC | `A\]B` | `A]B` | **FAIL** |
| 3.0.0-beta3 | `#012`, MDC (control) | `A#012B` | `A#012B` | PASS |
| main HEAD 2bb673f650 | same four cases | as above | same as 3.0.0-beta3 | FAIL ×3, control PASS |
| PR head 277ea4a838 | `\n`, MDC | `A\\nB` | `A\\nB` | PASS |
| PR head 277ea4a838 | `\n`, StructuredDataMessage | `C\\nD` | `C\\nD` | PASS |
| PR head 277ea4a838 | `]`, MDC | `A\]B` | `A\]B` | PASS |
| PR head 277ea4a838 | `#012`, MDC (control) | `A#012B` | `A#012B` | PASS |

Full output in `output/<label>/result.txt`, with the log files.

Notes: the 2.x original is `repros/pr-4471`, with identical observed values on 2.26.1 and on its PR head.
