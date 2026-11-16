# PR #4175 — native JsonTemplateLayout resolvers for W3C tracing fields

https://github.com/apache/logging-log4j2/pull/4175

The PR adds three `EventResolverFactory` plugins to `log4j-layout-template-json` — `traceId`, `spanId`, `traceFlags` — that write
`LogEvent.getTraceId()/getSpanId()/getTraceFlags()` straight to the `JsonWriter`. Those accessors and the
`org.apache.logging.log4j.spi.TraceContextProvider` SPI come from #4171 (`b6ba7d0af6`), which is on `2.x` but in no release,
so on 2.26.1 neither the native resolvers nor the `%traceId` pattern converters exist. Before the PR the only JTL route was the
`pattern` resolver with `%traceId`, which goes through a `StringBuilder`.

The repro registers a `FixedTraceContextProvider` through `META-INF/services` (compiled by `run.sh` only when the SPI is on the
classpath) and logs one event through two `File` appenders: one with the native resolvers (`native-template.json`), one with the
`pattern` resolver as a control (`pattern-template.json`). The oracle reads the files back.

## Commands

```bash
log4j-pr-4175-repro/run.sh 2.26.1 2.26.1
log4j-pr-4175-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-head
log4j-pr-4175-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4175 pr-head-b10b76ab00
```

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | native `traceId`/`spanId`/`traceFlags` resolvers | ids in JSON | `unknown resolver: traceId`; layout not created, appender fell back to plain `traced event` | FAIL |
| 2.26.1 | `pattern` resolver `%traceId` (control) | ids in JSON | `"traceId":"mainraceId"`, `"spanId":"%spanId"` (converters do not exist; SPI absent) | FAIL |
| 2.x HEAD `ea28f5a78b` | native resolvers | ids in JSON | `unknown resolver: traceId` | FAIL |
| 2.x HEAD `ea28f5a78b` | `pattern` resolver `%traceId` (control) | ids in JSON | `4bf92f35…4736`, `00f067aa0ba902b7`, `01` | PASS |
| PR head `b10b76ab00` | native resolvers | ids in JSON | `"traceId":"4bf92f3577b34da6a3ce929d0e0e4736","spanId":"00f067aa0ba902b7","traceFlags":"01"` | PASS |
| PR head `b10b76ab00` | `pattern` resolver `%traceId` (control) | ids in JSON | same three values | PASS |

Raw: `output/<label>/result.txt`, `output/<label>/native.json`, `output/<label>/status.txt`.

Notes: the 2.x HEAD column isolates this PR from #4171: the SPI and `%traceId` already work there, only the native resolvers are missing.
The zero-allocation claim in the PR description was not measured.
