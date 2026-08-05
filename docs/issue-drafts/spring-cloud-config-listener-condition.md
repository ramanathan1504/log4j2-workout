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

There may also be a second-order effect, which I have **not** verified: if an
application both component-scans `org.apache.logging.log4j.spring.cloud.config.client`
*and* sets the property, the class would be registered as a listener twice, and
`publishEvent()` could be invoked more than once per change event.

That combination is uncommon — few applications scan the Log4j package — so in
practice the `spring.factories` entry is usually the only registration, which is
exactly why the condition appears to do nothing. Treat the double-registration
point as a question rather than a report.

## Configuration

**Version:** 2.x @ `04c93c1d33`

**Operating system:** macOS 15 (Darwin 25.5.0)

**JDK:** Temurin 21

**Spring Boot:** 3.4.3, Spring Cloud Config 4.x

## Reproduction

Run a Spring Boot application with `log4j-spring-cloud-config-client`, a Spring
Cloud Config server, and the documented switch **off**:

```bash
-Dspring.cloud.config.watch.enabled=false
```

Change the served configuration and publish an `EnvironmentChangeEvent` — for
example by POSTing to `/actuator/refresh`.

Expected: nothing, because the watch is disabled. Actual, captured on Log4j
**2.26.1**, JDK 21, Spring Boot 3.4.3.

The lines below come from a purpose-built harness, not from Log4j — it embeds a
Spring Cloud Config server, serves a Log4j configuration from it, sets
`monitorInterval` to 300s, and then reports whether a reload arrived before that
interval elapsed. That design is what makes the result meaningful: a reload seen
298 seconds early cannot be the timer.

```
  WatchEventService impl   org.apache.logging.log4j.spring.cloud.config.client.WatchEventManager
  (still cloud-config-baseline - the watch interval is 300s and has not elapsed)
RELOADED - the refresh event drove the reload, 298 seconds before
           the monitorInterval would have
```

The reload happened 298 seconds ahead of the polling interval, so it was the
event that drove it, not the timer. With the property set to `false`.

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
