# PR #4410 — YAML configuration with Jackson 3

https://github.com/apache/logging-log4j2/pull/4410

`YamlConfigurationFactory` was active only when the Jackson 2 classes were present, so an application with only Jackson 3 (`tools.jackson.*`, e.g. Spring Boot 4) had its `log4j2.yaml` handed to the XML parser. The PR adds `Jackson3YamlConfiguration` and makes the factory prefer Jackson 3 when both are present. The repro configures `org.example` at DEBUG with `additivity: yes` and Root at ERROR, both writing to one file, and runs each version on two classpaths: Jackson 3 only (Jackson 2 core/databind/yaml and SnakeYAML removed) and Jackson 2 + Jackson 3. The second exercises the review note's concern: Jackson 3 YAML reads `yes` as a string, which `Boolean.valueOf` turns into `false`.

## Commands

```bash
cd log4j-pr-4410-repro
./run.sh 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Jackson 3.2.3 (`jackson-dataformat-yaml`, snakeyaml-engine 3.0.1), Jackson 2.20.0, JDK 17.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | Jackson 3 only: `log4j2.yaml` loaded | config `Jackson3Repro` | XmlConfiguration, `Content is not allowed in prolog` | FAIL |
| 2.26.1 | Jackson 3 only: DEBUG event written | >= 1 | 0 | FAIL |
| 2.26.1 | Jackson 2 + 3: loaded | `Jackson3Repro` | `Jackson3Repro` (YamlConfiguration) | PASS |
| 2.26.1 | Jackson 2 + 3: `additivity: yes` is true (2 copies) | 2 | 2 | PASS |
| 2.x HEAD ea28f5a78b | Jackson 3 only: loaded | `Jackson3Repro` | `Jackson3Repro` (Jackson3YamlConfiguration) | PASS |
| 2.x HEAD ea28f5a78b | Jackson 3 only: DEBUG event written | >= 1 | 1 | PASS |
| 2.x HEAD ea28f5a78b | Jackson 2 + 3: loaded | `Jackson3Repro` | `Jackson3Repro` (Jackson3YamlConfiguration) | PASS |
| 2.x HEAD ea28f5a78b | Jackson 2 + 3: `additivity: yes` is true (2 copies) | 2 | **1** | FAIL |

Notes: the note's concern shows up. With both Jacksons present, 2.x HEAD switches to Jackson 3 and `additivity: yes` silently becomes `false` (1 copy instead of 2), a behaviour change for existing Jackson 2 users who add Jackson 3 to the classpath.
