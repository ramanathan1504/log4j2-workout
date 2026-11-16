# PR #4252 — `Log4j2EventListener` ignores `spring.cloud.config.watch.enabled=false`

https://github.com/apache/logging-log4j2/pull/4252

`log4j-spring-cloud-config-client` registers `Log4j2EventListener` through `spring.factories`, so Spring instantiates it outside the bean factory and its `@ConditionalOnProperty(spring.cloud.config.watch.enabled)` is never evaluated. Every `EnvironmentChangeEvent` (an `/actuator/refresh`) therefore called `WatchEventManager.publishEvent()`, which runs `checkFiles()` on every subscribed `WatchManager` and reloads a modified configuration even with watching disabled. The PR reads the property from the event's `ApplicationContext` and returns early when it is `false`. `Repro` starts Log4j with a file configuration (`monitorInterval="3600"`, so polling never fires during the run), rewrites it with a new root level and a newer mtime, then publishes an `EnvironmentChangeEvent` from a `GenericApplicationContext` that has the listener added the way `spring.factories` does.

## Commands

```bash
cd log4j-pr-4252-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

JBR 17.0.14; spring-cloud-context and Spring Framework as resolved from `log4j-spring-cloud-config-client:2.26.1`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `watch.enabled=false`, config rewritten INFO to DEBUG, refresh | root stays INFO | **DEBUG** (reloaded) | FAIL |
| 2.26.1 | control: `watch.enabled=true`, config rewritten to TRACE, refresh | TRACE | TRACE | PASS |
| 2.x HEAD ea28f5a78b | `watch.enabled=false`, config rewritten INFO to DEBUG, refresh | root stays INFO | INFO (3 s wait) | PASS |
| 2.x HEAD ea28f5a78b | control: `watch.enabled=true`, config rewritten to TRACE, refresh | TRACE | TRACE | PASS |
