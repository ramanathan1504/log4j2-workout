# Finding: YAML configuration with Jackson 3 — `yes`/`on` booleans silently become `false`

Status: not yet filed upstream

## Mechanism

- `YamlConfigurationFactory` prefers Jackson 3 whenever `tools.jackson.dataformat.yaml.YAMLMapper` is present, even when Jackson 2 is also present: `log4j-core/src/main/java/org/apache/logging/log4j/core/config/yaml/YamlConfigurationFactory.java:60` and `:96-97` on `origin/2.x` (`ea28f5a78b`).
- `Jackson3YamlConfiguration` parses with a default `YAMLMapper.builder().build()` (`.../config/yaml/Jackson3YamlConfiguration.java:106`), which is backed by snakeyaml-engine and the YAML 1.2 core schema. `yes`, `on` and their case variants are plain strings there.
- Every scalar is stored as `n.asString()` (`Jackson3YamlConfiguration.java:226`), so `additivity: yes` reaches the plugin builder as `"yes"` and `Boolean.valueOf("yes")` is `false`. No status logger message is written.
- With Jackson 2 (`.../config/yaml/YamlConfiguration.java:20`, SnakeYAML 2.4, YAML 1.1 booleans) the same scalar is a boolean node and reaches the builder as `"true"`.
- JSON is untouched: `#4410` changed no file under `config/json/`, `JsonConfiguration` still uses Jackson 2 (`.../config/json/JsonConfiguration.java:103`), and JSON has no unquoted `yes`.

## Commands

```bash
cd log4j-finding-yaml-jackson3-boolean-repro
./run.sh 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
./run.sh 3.0.0-beta3
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-main 3.x-main-2bb673f650
```

Each 2.x version runs three classpaths: `jackson2-only` (Jackson 3 and snakeyaml-engine removed), `jackson3-only` (Jackson 2 and SnakeYAML removed) and `jackson2+jackson3`. 3.x runs one classpath; the module decides the library. Jackson 2.20.0 with SnakeYAML 2.4, Jackson 3.2.3 with snakeyaml-engine 3.0.1, OpenJDK 17.0.14, macOS.

The config (`src/main/resources/log4j2.yaml`) has one logger per scalar, `s.<scalar>`, with `additivity: <scalar>`, writing to `FILE`, with Root at ERROR also writing to `FILE`. Two copies of the event means `true`, one means `false`. It adds a `File` appender with `append: yes` over a pre-seeded file, a logger with `includeLocation: yes` and pattern `%M|%m`, and a logger with `level: off`. Expected values are the Jackson 2 / 2.26.1 meaning. Controls: `true`, `True`, `TRUE`, `false`, `FALSE`, quoted `'yes'`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | jackson2-only: `additivity: yes/Yes/YES/on/On/ON` | true | true | PASS |
| 2.26.1 | jackson2-only: `y/Y/n/no/off` | false | false | PASS |
| 2.26.1 | jackson2-only: controls | as written | as written | PASS |
| 2.26.1 | jackson2-only: `append: yes` | `[seed, appended]` | `[seed, appended]` | PASS |
| 2.26.1 | jackson2-only: `includeLocation: yes` | `main\|loc` | `main\|loc` | PASS |
| 2.26.1 | jackson2+jackson3 | as jackson2-only | as jackson2-only (YamlConfiguration) | PASS |
| 2.26.1 | jackson3-only | YAML loaded | XmlConfiguration, `Content is not allowed in prolog` (the #4410 gap) | FAIL |
| 2.x HEAD ea28f5a78b | jackson2-only: all cases | as 2.26.1 | as 2.26.1 | PASS |
| 2.x HEAD ea28f5a78b | jackson3-only: `additivity: yes/Yes/YES/on/On/ON` | true | **false** (1 copy) | FAIL |
| 2.x HEAD ea28f5a78b | jackson3-only: `y/Y/n/no/off`, controls | as written | as written | PASS |
| 2.x HEAD ea28f5a78b | jackson3-only: `append: yes` | `[seed, appended]` | **`[appended]`**, file truncated | FAIL |
| 2.x HEAD ea28f5a78b | jackson3-only: `includeLocation: yes` | `main\|loc` | **`\|loc`** | FAIL |
| 2.x HEAD ea28f5a78b | jackson2+jackson3: same six scalars, `append`, `includeLocation` | as 2.26.1 | false, `[appended]`, `\|loc` (Jackson3YamlConfiguration) | FAIL |
| 2.x HEAD ea28f5a78b | jackson2+jackson3: controls | as written | as written | PASS |
| 3.0.0-beta3 | all cases (Jackson 2 module) | as 2.26.1 | as 2.26.1 | PASS |
| 3.x main 2bb673f650 | `additivity: yes/Yes/YES/on/On/ON` | true | **false** | FAIL |
| 3.x main 2bb673f650 | `append: yes` / `includeLocation: yes` | seed kept / `main\|loc` | `[appended]` / `\|loc` | FAIL |
| 3.x main 2bb673f650 | controls | as written | as written | PASS |

Status logger output for the failing Jackson 3 runs is empty: the change is silent.

Scalars that differ between Jackson 2 and Jackson 3: `yes`, `Yes`, `YES`, `on`, `On`, `ON`. They are `true` on Jackson 2 and `false` on Jackson 3. `no`/`off` are `false` on both, by different routes. `y`/`Y`/`n` are strings on both (SnakeYAML 2.4 does not resolve them). `true`/`True`/`TRUE`/`false`/`FALSE` agree.

The same YAML 1.1 rule cuts the other way for `level: off`: Jackson 2 turns it into `"false"`, `Level` conversion fails (`WARN Error while converting string [false] to type [class org.apache.logging.log4j.Level]`), and the logger keeps logging (1 line, FAIL on 2.26.1, 2.x HEAD jackson2-only and 3.0.0-beta3). Jackson 3 reads `off` correctly (0 lines). That one is pre-existing on Jackson 2 and is not this finding.

## Since when

Introduced by `31eb0e720d` "Support Jackson 3 (#4410)", found with `git log -S Jackson3YamlConfiguration origin/2.x`. No release tag contains it (`git tag --contains 31eb0e720d` is empty). This is a regression on 2.x that no release has yet. 2.26.1 is not affected: with Jackson 3 only it cannot load YAML at all, and with both it uses Jackson 2.

## 3.x

`log4j-config-yaml` on `origin/main` uses `tools.jackson.dataformat:jackson-dataformat-yaml` (Jackson 3), `YAMLMapper.builder().build()` at `log4j-config-yaml/src/main/java/org/apache/logging/log4j/config/yaml/YamlConfiguration.java:43`, and `n.asText()` at `log4j-config-jackson/src/main/java/org/apache/logging/log4j/config/jackson/AbstractJacksonConfiguration.java:209`. It came with `4e44a28112` "Upgrade Jackson from 2.x to 3.0.0 (#3701)", which no 3.x tag contains. `3.0.0-beta3` uses Jackson 2 (`com.fasterxml.jackson.dataformat.yaml.YAMLMapper`) and is not affected. 3.x main is affected, as run above.

## Defect-class sweep (`origin/2.x`)

| Site | Verdict |
|---|---|
| `Jackson3YamlConfiguration.processAttributes` (`:226`): `additivity` | affected, run |
| same: `File` `append` | affected, run (existing log file truncated) |
| same: `Logger` `includeLocation` | affected, run |
| same: every other `boolean` plugin attribute (`immediateFlush`, `ignoreExceptions`, `bufferedIo`, `locking`, `createOnDemand`, `Console` `follow`/`direct`, `PatternLayout` `alwaysWriteExceptions`/`disableAnsi`, `AsyncLogger` `includeLocation`) | affected by the same line when written as `yes`/`on`; untested individually |
| same: root attributes (`status`, `shutdownHook`, `monitorInterval`) | not affected: none is parsed with `Boolean.valueOf`; `shutdownHook` compares with `disable` |
| `Jackson3YamlConfiguration.getType` (`:213`) | not affected: `type` is a plugin name |
| `JsonConfiguration` / `JsonConfigurationFactory` | not affected: Jackson 2 only, unchanged by #4410 |
| `YamlConfiguration` (Jackson 2) | not affected; it carries the pre-existing `level: off` problem above |
| 3.x `AbstractJacksonConfiguration` (`log4j-config-yaml`) | affected, run |

## Class

`log4j-bug`. Reproduced on `origin/2.x` and `origin/main`: adding Jackson 3 to a classpath that already has Jackson 2 silently changes the meaning of an existing, working YAML configuration, with file truncation as one consequence, and nothing documents it. The changelog entry for #4410 says only "Add Jackson 3 support to the YAML configuration factory."
