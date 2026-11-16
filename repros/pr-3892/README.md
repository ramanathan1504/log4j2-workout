# PR #3892 — Remove the `jvmrunargs` lookup from `main` (port of #3874)

https://github.com/apache/logging-log4j2/pull/3892

The `main` port (`3871c4cd59`) deletes `JmxRuntimeInputArgumentsLookup`, the `@Lookup @Plugin("jvmrunargs")` that
mapped `RuntimeMXBean.getInputArguments()` by index, and its `Interpolator` case. After it, `${jvmrunargs:…}` is no
longer a known prefix on 3.x and stays as literal text, exactly as #3874 did on 2.x.

## Commands

```bash
cd log4j-pr-3892-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
```

`log4j-api` is pinned to `2.24.3`; the configuration is passed as `-Dlog4j.configuration.location`, and the control row
proves it was loaded (the event reaches the `File` appender rather than `DefaultConfiguration`). With the jars
directory, `log4j-core`, `log4j-plugins` and `log4j-kit` all come from `jars-main` (printed in `result.txt`).

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | config `${jvmrunargs:0}` | unresolved | `-Dprobe=jvmrunargs-probe` | FAIL |
| 3.0.0-beta3 | layout `$${jvmrunargs:0}` | unresolved | `arg0=-Dprobe=jvmrunargs-probe msg=hello` | FAIL |
| 3.0.0-beta3 | control: event written | `msg=hello` | written | PASS |
| main HEAD 2bb673f650 | config `${jvmrunargs:0}` | unresolved | `${jvmrunargs:0}` | PASS |
| main HEAD 2bb673f650 | layout `$${jvmrunargs:0}` | unresolved | `arg0=${jvmrunargs:0} msg=hello` | PASS |
| main HEAD 2bb673f650 | control: event written | `msg=hello` | written | PASS |
