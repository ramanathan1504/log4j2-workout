# PR #3874 — Remove the `jvmrunargs` lookup

https://github.com/apache/logging-log4j2/pull/3874

`Interpolator` registered `JmxRuntimeInputArgumentsLookup` under the `jvmrunargs` prefix, a `MapLookup` built from
`RuntimeMXBean.getInputArguments()` (index → argument). The PR (`e84655e223`, merged to `2.x`) deletes the class and
the `Interpolator` case, so `${jvmrunargs:…}` no longer resolves anywhere: configuration substitution and runtime
`$${…}` in a layout both leave the literal text. The note records that the lookup "never worked until it got
accidentally fixed in 2.25.0", so 2.25.x is the only release line where it resolves.

## Commands

```bash
cd log4j-pr-3874-repro
./run.sh 2.25.1 2.25.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

The JVM is started with `-Dprobe=jvmrunargs-probe` as its first input argument. `Repro` resolves `${jvmrunargs:0}`
through the configuration's `StrSubstitutor` and logs one event through a `File` appender whose pattern is
`arg0=$${jvmrunargs:0} msg=%m`, then reads the file back. A case is `FAIL` when the lookup still returns the JVM
argument.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.25.1 | config `${jvmrunargs:0}` | unresolved | `-Dprobe=jvmrunargs-probe` | FAIL |
| 2.25.1 | layout `$${jvmrunargs:0}` | unresolved | `arg0=-Dprobe=jvmrunargs-probe msg=hello` | FAIL |
| 2.25.1 | control: event written | `msg=hello` | written | PASS |
| 2.x HEAD ea28f5a78b | config `${jvmrunargs:0}` | unresolved | `${jvmrunargs:0}` | PASS |
| 2.x HEAD ea28f5a78b | layout `$${jvmrunargs:0}` | unresolved | `arg0=${jvmrunargs:0} msg=hello` | PASS |
| 2.x HEAD ea28f5a78b | control: event written | `msg=hello` | written | PASS |

Notes: neither run logs a StatusLogger message at WARN when an unknown lookup prefix is used, so a configuration still
using `jvmrunargs` degrades silently to the literal text.
