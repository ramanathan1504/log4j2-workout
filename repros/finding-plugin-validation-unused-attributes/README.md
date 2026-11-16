# Finding: log4j-plugins (3.x) — a failed `@Required` check reports the node's valid attributes and children as invalid

Status: not yet filed upstream

Prior work: `repros/pr-4153` (noticed there after `StringMatchFilter.text` became `@Required`).

## Mechanism

On 3.x, plugin attributes are consumed one injection point at a time: each `@PluginAttribute` /
`@PluginBuilderAttribute` resolver removes its attribute from the node as it reads it, and the constraint check runs
right after each field or argument. The first failing `@Required` throws, so every injection point after it is never
resolved and its attribute stays on the node. `ConfigurationProcessor` catches the exception, returns `null`, and then
runs the unused-attribute and unused-child checks unconditionally, which report the leftovers as invalid. The parent
of the failed node then sees a `null` child, skips it without removing it, and reports that child as unmatched too.

On `origin/main` at `2bb673f650`:

- `log4j-plugins/src/main/java/org/apache/logging/log4j/plugins/di/DefaultInstanceFactory.java:376-378`: fields are
  injected in a loop; `:410` `validate(field, ...)` throws mid-loop, so later fields are never resolved.
- `.../di/DefaultInstanceFactory.java:170-172`: factory-method arguments, same pattern; `:224` validates each argument
  as it is resolved.
- `.../plugins/di/ConfigurableInstanceFactory.java:175-176`: `throw new ConstraintValidationException`.
- `.../plugins/di/resolver/AbstractAttributeFactoryResolver.java:66`: `node.removeMatchingAttribute(...)` is the only
  place an attribute is consumed.
- `.../plugins/internal/util/BeanUtils.java:99-109`: fields are ordered subclass first, so a `@Required` field in a
  concrete builder (`FileAppender.Builder.fileName`) aborts before every inherited field (`name`, `append`, the layout).
- `log4j-core/src/main/java/org/apache/logging/log4j/core/config/ConfigurationProcessor.java:111-113`: the exception is
  caught and `null` returned; `:77-78` then calls `verifyAttributesConsumed` / `verifyChildrenConsumed` regardless;
  `:117-137` builds `... contains invalid attributes ...`, `:139-150` builds `... has no field or parameter that
  matches element ...`.
- `.../plugins/di/resolver/PluginElementFactoryResolver.java:84-88`: a `null` child is logged and skipped
  (`continue`) before `iterator.remove()`, so the parent's `verifyChildrenConsumed` reports it as well.

2.x is not affected: `log4j-core/src/main/java/org/apache/logging/log4j/core/config/plugins/util/PluginBuilder.java:177-202`
on `origin/2.x` (`ea28f5a78b`) injects every field first and only then validates at `:203`, so all attributes are
consumed before the failure; the remaining-attribute check at `:210` is never reached on a failed node.

## Commands

```bash
cd log4j-finding-plugin-validation-unused-attributes-repro
./run.sh 3.0.0-alpha1
./run.sh 3.0.0-beta3
./run.sh <scratchpad>/wt/jars-main main-HEAD-2bb673f650
./run.sh 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

`log4j2.xml` has a `File` appender without `fileName`, a `StringMatchFilter` without `text` inside an otherwise valid
`RandomAccessFile`, an `AppenderRef` with `level` but no `ref` (factory-method path), a `Console` with a real typo
`bogusAttr="1"` (control: must be reported on every version), and the valid `RandomAccessFile` itself (control: must
not be reported). `Repro.java` tees `System.out`/`System.err` before Log4j starts, parses every
`contains ... attribute(s)` and `has no field or parameter that matches element` line, and checks each case. 3.x runs
use `log4j-api` 2.24.3 (alpha1: its own 3.0.0-alpha1 API) and `-Dlog4j.configuration.location`.

## Results

Expected for every failing case: the validation error only, no valid attribute or element reported as invalid.

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-alpha1 | `File` without `fileName` | no extra error | `File contains invalid attributes "name", "append", "immediateFlush"` | FAIL |
| 3.0.0-alpha1 | its `PatternLayout` child | no extra error | `appender File has no field or parameter that matches element PatternLayout` | FAIL |
| 3.0.0-alpha1 | parent `Appenders` | no extra error | `Appenders has no field or parameter that matches element File` | FAIL |
| 3.0.0-alpha1 | `AppenderRef` without `ref` | no extra error | `AppenderRef contains an invalid element or attribute "level"`, then `Logger has no field ... AppenderRef` | FAIL |
| 3.0.0-alpha1 | `StringMatchFilter` without `text` | no extra error | none (`text` not `@Required` before the port) | PASS |
| 3.0.0-alpha1 | control: `Console bogusAttr` | reported | reported | PASS |
| 3.0.0-alpha1 | control: valid `RandomAccessFile` | not reported | not reported | PASS |
| 3.0.0-beta3 | `File` / child / `Appenders` / `AppenderRef` / `Logger` | no extra error | same five extra errors as alpha1 | FAIL |
| 3.0.0-beta3 | `StringMatchFilter` without `text` | no extra error | none (`text` not `@Required` yet) | PASS |
| 3.0.0-beta3 | both controls | as above | as above | PASS |
| main HEAD 2bb673f650 | `StringMatchFilter` without `text` | no extra error | `StringMatchFilter contains invalid attributes "onMatch", "onMismatch"` | FAIL |
| main HEAD 2bb673f650 | `File` / child / `Appenders` / `AppenderRef` / `Logger` | no extra error | same five extra errors | FAIL |
| main HEAD 2bb673f650 | both controls | as above | as above | PASS |
| 2.26.1 | all five failing cases | no extra error | none; only the validation errors and `Null object returned for File in Appenders.` | PASS |
| 2.26.1 | both controls | as above | as above | PASS |
| 2.x HEAD ea28f5a78b | all cases | as 2.26.1 | as 2.26.1 | PASS |

Per-version output: `output/<label>/result.txt`, full StatusLogger text in `output/<label>/stdout.txt` (3.x) or
`status.txt` (2.x).

## Since when

Throw-on-first-failing-field arrived with the dependency-injection rewrite, `adad882cd6` (2022-02-03,
"[LOG4J2-2854] - Create standardized dependency injection API"), which replaced `PluginBuilder`'s
inject-all-then-validate; refined by `aebc3ac068` and `2c79390529` (March 2022). Every 3.x release is affected:
3.0.0-alpha1 and 3.0.0-beta3 reproduce. `StringMatchFilter` only joins the affected set on `main` because #4153 made
`text` `@Required`.

## 3.x

3.x only. `origin/main` has the code above and reproduces. 2.x (2.26.1 and `origin/2.x`) is not affected.

## Defect-class sweep (origin/main)

| Site | Verdict |
|---|---|
| `DefaultInstanceFactory.injectField` (`:399-414`), builder fields | affected (`File`, `StringMatchFilter`) |
| `DefaultInstanceFactory.getInjectableInstance` (`:170-172`) / `getArgumentFactory` (`:224`), factory-method arguments | affected (`AppenderRef`) |
| `DefaultInstanceFactory.injectMembers` (`:383-393`), injectable setter methods with `@Required` arguments, e.g. `AbstractAppender.Builder.setName` | untested; same throw-mid-loop shape |
| `getInjectableInstance` class-level `validate(rawType, ...)` (`:167`) | untested; fails before any attribute is read, so every attribute would be reported |
| `PluginElementFactoryResolver.findChildElements` (`:84-88`), parent of a failed child | affected (`Appenders`, `Logger`) |
| `ConfigurationProcessor.instantiate` catch (`:111-113`), any other exception thrown during injection | untested; same leftovers would be reported |
| 2.x `PluginBuilder.injectFields` / `generateParameters` | not affected (ran) |

## Class

`log4j-bug`, 3.x only. Cosmetic: the invalid node is still rejected correctly and the real validation error is
logged first; the extra ERRORs misdirect the user toward attributes that are fine. Rank: P3, ranked by hand.
