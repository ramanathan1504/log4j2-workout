# PR #4156 — [main] Warn when `@PluginBuilderAttribute` field lacks public setter (#3195 port)

https://github.com/apache/logging-log4j2/pull/4156

`PluginProcessor` (the `log4j-plugin-processor` annotation processor every plugin build runs) now visits each
`@PluginBuilderAttribute` field and looks in the enclosing builder for a public one-argument `set<Field>` or
`with<Field>` whose return type is assignable to the builder. If there is none, and the field does not carry
`@SuppressWarnings("log4j.public.setter")`, it emits a `Diagnostic.Kind.ERROR` on the field. On 3.0.0-beta3 such a
field compiled silently and could only be set reflectively from a configuration file, never from Java.

## Command

```bash
cd log4j-pr-4156-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
```

`run.sh` resolves the classpath with Maven (Maven's own compile is skipped), then compiles
`src/main/java/example/ReproLayout.java` with `javac -processorpath <that classpath>`, so the processor under test is
the `log4j-plugin-processor` jar of the run. The builder has three `@PluginBuilderAttribute` fields: `prefix` with a
fluent `setPrefix`, `fieldWithoutSetter` with no setter, and `suppressedFieldWithoutSetter` with
`@SuppressWarnings("log4j.public.setter")`. The oracle reads javac's diagnostics and checks that the plugin index
(`META-INF/services/...PluginService`) was generated, which proves the processor ran.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | processor ran (control) | plugin index generated | index generated, javac exit 0 | PASS |
| 3.0.0-beta3 | `fieldWithoutSetter` reported | ERROR on the field | no diagnostic, javac exit 0 | FAIL |
| 3.0.0-beta3 | `prefix` with setter (control) | not reported | not reported | PASS |
| 3.0.0-beta3 | suppressed field (control) | not reported | not reported | PASS |
| main HEAD 2bb673f650 | processor ran (control) | plugin index generated | index generated | PASS |
| main HEAD 2bb673f650 | `fieldWithoutSetter` reported | ERROR on the field | ``ReproLayout.java:25: error: The field `fieldWithoutSetter` does not have a public setter.`` javac exit 1 | PASS |
| main HEAD 2bb673f650 | `prefix` with setter (control) | not reported | not reported | PASS |
| main HEAD 2bb673f650 | suppressed field (control) | not reported | not reported | PASS |

Notes: despite the title, the diagnostic is an ERROR, not a warning: a third-party plugin that compiled on 3.0.0-beta3
now fails its build until each such field gets a fluent setter or the suppression.
