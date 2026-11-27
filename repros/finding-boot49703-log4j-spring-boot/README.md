# finding-boot49703-log4j-spring-boot

Spring Boot [#49703](https://github.com/spring-projects/spring-boot/issues/49703) (closed as not planned, 2026-03-23): `log4j-spring-boot` breaks startup on Boot 4. Piotr asked for Log4j doc PRs saying the module is for Spring Boot 2 only. This repro checks every claim the docs fix would make. Gap 7 in `~/knowledge/Projects/log4j/2026-11-25-spring-boot-log4j-integration-gaps.md`.

```bash
repros/finding-boot49703-log4j-spring-boot/run.sh
```

The app (`app/`) uses one `log4j2-spring.xml` with `${spring:spring.application.name}` in the pattern and `<SpringProfile name="dev">` choosing the appender, and sets `log4j2.reproProbe=fromSpring` in `application.properties`. For each run it prints:
- the `LoggingSystem` Boot picked,
- the class behind the `spring` lookup and the `SpringProfile` arbiter,
- what `PropertiesUtil` returns for `log4j2.reproProbe`.

Log4j is 2.26.1 everywhere. Each Boot version has its own `pom-<v>.xml`; profile `with` adds `log4j-spring-boot`. Output: `output/run.txt`.

## Results (2026-11-26, JBR 17)

| Boot | without `log4j-spring-boot` | with `log4j-spring-boot` |
|---|---|---|
| 2.7.18 | starts; `SpringProfile` → `CLASS_NOT_FOUND`, `${spring:}` not resolved, no property source | starts; Log4j's `Log4j2SpringBootLoggingSystem`, `SpringLookup`, `SpringProfileArbiter`, property source all work |
| 3.5.16 | starts; Boot's own `SpringEnvironmentLookup`, `SpringProfileArbiter`, property source all work | starts. **Classpath order decides**: module last → Boot's classes used and the module ignored; module first → Log4j's classes used. Both work |
| 4.0.3 | starts; Boot's own classes work | **fails**: `NoSuchMethodError: Log4J2LoggingSystem: method 'void <init>(java.lang.ClassLoader)' not found` |
| 4.0.4 | starts; Boot's own classes work | **fails**: `IllegalAccessError: Log4j2SpringBootLoggingSystem tried to access method Log4J2LoggingSystem.<init>(ClassLoader)` (classpath run and `java -jar`) |
| 4.2.0 | starts; Boot's own classes work | **fails** as 4.0.4 when the module is first on the classpath (Maven's default order here). When it is last: starts, and Boot's classes are used |

Extra cases:
- **`-Dlog4j2.disableCloudConfigLoggingSystem=true`, module first, on Boot 3.5.16 and 4.2.0:** the app starts with Boot's `LoggingSystem`. But the `spring` lookup and `SpringProfile` arbiter still come from `log4j-spring-boot`, and **no application log line is written**: every `<SpringProfile>` branch is dropped.
- **The same switch as a Spring property** (`--log4j2.disableCloudConfigLoggingSystem=true`): it has no effect, and the app still fails on 4.2.0.

## Why

- Boot commit `7d34320` (2025-10-06, "Simplify Log4J2LoggingSystem", by Piotr P. Karwasz) changed the constructor `Log4j2SpringBootLoggingSystem` calls:
  - Boot 4.0.0–4.0.3 have only `Log4J2LoggingSystem(ClassLoader, LoggerContext)`, package-private.
  - From 4.0.4 there is `Log4J2LoggingSystem(ClassLoader)` again, but package-private (`javap` on each jar).
  - 3.5.16 still has it `public`.
- `Log4j2SpringBootLoggingSystem.Factory` and Boot's `Log4J2LoggingSystem.Factory` are both `@Order(0)`, so the one found first on the classpath wins.
- The `spring` lookup and `SpringProfile` arbiter have the same plugin names in both jars, and the same order decides between them.
- Log4j's `SpringLookup` and `SpringProfileArbiter` read the Spring `Environment` that only `Log4j2SpringBootLoggingSystem` stores (`ENVIRONMENT_KEY`), so with the switch they see none.
- The switch is read through `PropertiesUtil` before Boot adds the Spring `Environment` as a property source, so only a JVM system property (or `log4j2.component.properties`) can set it.

## What the docs can say (all backed by the table)

1. `log4j-spring-boot` is for Spring Boot 2 only.
2. Spring Boot 3 and later provide the `spring:` lookup, the `<SpringProfile>` arbiter and Log4j properties from the Spring `Environment` themselves. Remove `log4j-spring-boot` when upgrading.
3. On Spring Boot 4 the module can stop the application from starting (`NoSuchMethodError` on 4.0.0–4.0.3, `IllegalAccessError` from 4.0.4).
4. Do not suggest `log4j2.disableCloudConfigLoggingSystem` as a workaround: the app starts, but its log events are lost.
