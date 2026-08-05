# DRAFT — not filed

**Title:** `Log4j2EventListener`'s `@ConditionalOnProperty` has no effect — the listener always runs

---

## Description

`Log4j2EventListener` in `log4j-spring-cloud-config-client` carries
`@ConditionalOnProperty(value = "spring.cloud.config.watch.enabled")`, which
suggests the listener is inert unless that property is set. It is not: the
listener runs regardless.

It is registered twice, by two different mechanisms:

`log4j-spring-cloud-config-client/src/main/java/.../Log4j2EventListener.java`:

```java
@Component
@ConditionalOnProperty(value = "spring.cloud.config.watch.enabled")
public class Log4j2EventListener implements ApplicationListener<EnvironmentChangeEvent> {
```

`log4j-spring-cloud-config-client/src/main/resources/META-INF/spring.factories`, line 17:

```
org.springframework.context.ApplicationListener=org.apache.logging.log4j.spring.cloud.config.client.Log4j2EventListener
```

`@ConditionalOnProperty` is a **bean-definition** condition. It applies to the
`@Component` registration, and only there. Listeners named in `spring.factories`
are instantiated directly by `SpringApplication` during startup, before the
application context exists — there is no bean definition for a condition to
suppress, so that registration is unconditional.

The result is that `spring.cloud.config.watch.enabled=false` does not disable the
listener, and `EnvironmentChangeEvent` still reaches
`WatchEventManager.publishEvent()`.

## Why it matters

The property reads as the documented off switch. Setting it to `false` and
observing that reconfiguration still happens gives no clue why, since nothing is
logged and the annotation is right there in the source saying otherwise.

There is also a second-order effect: with both registrations active in a context
where the condition *does* pass, the class is registered as a listener twice, so
`publishEvent()` may be invoked more than once per change event.

## Configuration

**Version:** 2.x @ `04c93c1d33`

**Operating system:** macOS 15 (Darwin 25.5.0)

**JDK:** Temurin 21

**Spring Boot:** 3.4.3, Spring Cloud Config 4.x

## Reproduction

1. A Spring Boot application with `log4j-spring-cloud-config-client` on the
   classpath and a Spring Cloud Config server.
2. Set `spring.cloud.config.watch.enabled=false`.
3. Publish an `EnvironmentChangeEvent` — for example by POSTing to
   `/actuator/refresh` after changing the served configuration.
4. Log4j reconfigures. Expected: nothing happens, because the watch is disabled.

Adding a breakpoint or a log line in `onApplicationEvent` shows it being reached.

## Suggested fix

Pick one registration mechanism.

- If the condition is meant to apply, remove the `spring.factories` entry and
  rely on component scanning, where `@ConditionalOnProperty` works. This changes
  behaviour for applications that do not scan the Log4j package, so it warrants a
  note.
- If unconditional registration is intended, remove `@ConditionalOnProperty` and
  `@Component` from the class, so the source stops promising a switch that does
  not exist.
- If the property should genuinely gate it while keeping `spring.factories`,
  check it inside `onApplicationEvent` against the `Environment` instead.

The third is the smallest behavioural change and keeps the documented property
meaningful.
