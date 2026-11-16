# PR #4230 — [main] `OrderComparator` honours `@Ordered`

https://github.com/apache/logging-log4j2/pull/4230

Mostly a manual revamp. The one runtime change: `org.apache.logging.log4j.core.config.OrderComparator`, which `DefaultConfigurationFactory` uses to sort `ConfigurationFactory` plugins, read only the legacy `@Order` annotation. A class with only `org.apache.logging.log4j.plugins.Ordered` counted as unannotated, and an annotated class always beats an unannotated one, so the `@Ordered` value was ignored. The PR reads `@Order` first and falls back to `@Ordered`, keeping the descending order, and moves the built-in factories to `@Ordered`. The second case is the app-visible one: a custom `.xml` factory plugin annotated `@Ordered(10)` should outrank the built-in XML factory (5).

## Commands

```bash
cd log4j-pr-4230-repro
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-main main-HEAD
```

`log4j-api` is pinned to 2.24.3 (`log4j.api.version`). The plugin is registered by `log4j-plugin-processor` 3.0.0-beta3 at compile time, which also needs `biz.aQute.bnd.annotation`. The config is passed as `-Dlog4j.configuration.location=log4j2.xml`. The brief's jar-swap regex only strips `-SNAPSHOT`, so a `-beta3` jar name never matched and the swap silently did nothing. `run.sh` here also strips `-alpha`/`-beta` suffixes; the resolved classpath was checked to contain `log4j-core`, `log4j-plugins` and `log4j-kit` 3.0.0-SNAPSHOT.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 3.0.0-beta3 | sort `[none, @Order(5), @Ordered(10)]` | `NewOrderedTen,LegacyOrderFive,Unannotated` | `LegacyOrderFive,Unannotated,NewOrderedTen` | FAIL |
| 3.0.0-beta3 | `@Ordered(10)` custom factory picks `log4j2.xml` | used | not used (built-in XML factory won) | FAIL |
| main HEAD 2bb673f650 | sort | `NewOrderedTen,LegacyOrderFive,Unannotated` | `NewOrderedTen,LegacyOrderFive,Unannotated` | PASS |
| main HEAD 2bb673f650 | `@Ordered(10)` custom factory picks `log4j2.xml` | used | used | PASS |
