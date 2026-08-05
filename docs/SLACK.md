# Slack content

Paste-ready. Nothing here has been posted anywhere.

---

## Summary message

> Spent some time building a real-application bench for Log4j — every module,
> every config format, across 2.x and 3.x — mainly to check issues and PRs
> against something that actually runs. It turned up four defects, now filed:
>
> • **#4241** — `AbstractDatabaseManager` checks `isRunning()` on shutdown but not on write. A manager whose startup failed keeps accepting events and NPEs per event, hiding the real cause; and it's never cleaned up, so whatever startup built leaks. Affects 2.x and 3.x.
> • **#4242** — `log4j-cassandra` leaks the DataStax `Cluster` on failed startup. Its threads are non-daemon, so the JVM never exits. `LogManager.shutdown()` reports "all resources released: true" having released nothing.
> • **#4243** — `CsvParameterLayout` NPEs on any event without parameters. `getParameters()` is null for `SimpleMessage`, so every plain `logger.info("text")` throws. Affects 2.x and 3.x.
> • **#4244** — `Log4j2EventListener`'s `@ConditionalOnProperty` has no effect. It's registered both as a `@Component` and in `spring.factories`; the latter bypasses conditions entirely, so the documented off switch does nothing.
>
> Common thread: Log4j catches appender exceptions and exits 0, so all four are silent in production. Each issue has a captured run rather than a description.

---

## Individual messages

### #4241

> `AbstractDatabaseManager` guards `isRunning()` backwards. `write()` ignores it,
> so a manager whose `startupInternal()` threw keeps accepting events and NPEs on
> state it never assigned — one per event, burying the single line that named the
> real cause. `shutdown()` *does* check it, so that manager is never cleaned up.
> Affects JDBC, JPA and NoSQL alike, on 2.x and 3.x.
> https://github.com/apache/logging-log4j2/issues/4241

### #4242

> `log4j-cassandra`: when startup fails, `cluster.close()` never runs, and the
> DataStax 3.x driver's threads are non-daemon — so the JVM won't exit. Thread
> dump shows five surviving threads carrying the configured `clusterName`, while
> `LogManager.shutdown()` reports everything released.
> https://github.com/apache/logging-log4j2/issues/4242

### #4243

> `CsvParameterLayout` throws NPE on any event without parameters —
> `getParameters()` is null for `SimpleMessage`, and it goes straight into
> `printRecord` with no null check. Only `IOException` is caught, so it escapes:
> one error per event, no output, exit code 0. Present on 2.x and 3.x.
> https://github.com/apache/logging-log4j2/issues/4243

### #4244

> `Log4j2EventListener` carries `@ConditionalOnProperty("spring.cloud.config.watch.enabled")`
> but always runs — it's registered in `spring.factories` as well as being a
> `@Component`, and `SpringApplication` instantiates those directly, with no bean
> definition for a condition to suppress. Setting the property to false disables
> nothing.
> https://github.com/apache/logging-log4j2/issues/4244

---

## Draft comment for #2314 — NOT posted

`ClassLoaderContextSelector` should create a separate context per classloader —
open since Feb 2024. The finding below is the same mechanism, so it belongs here
rather than as a new issue. Post it if it adds anything.

> Another trigger for this, with a captured before/after.
>
> `log4j-appserver` and `log4j-web` together reproduce it reliably. appserver
> routes the container's own logging into Log4j, so Tomcat logs through Log4j
> *while starting* — before any webapp initialises — registering a context keyed
> to the parent classloader. When log4j-web's SCI then calls `getContext`, the
> parent walk finds that context and returns it, so the `ServletContext` entry
> it was given is never applied.
>
> Two symptoms, both from a running Tomcat 9 on 2.26.1:
>
> ```
> LoggerContext name  : log4j-bench-javax
> ServletContext bound: false
> WebLoggerContextUtils.getServletContext(): null
> ```
>
> `${web:}` cannot resolve, and the *shared* container context has been renamed
> to this webapp's display name — which affects anything else using it.
>
> Control, without appserver:
>
> ```
> ServletContext bound to LoggerContext : true
>   ${web:contextPath}        /bench
>   ${web:servletContextName} log4j-bench
> ```
>
> (The control is a sibling app on Tomcat 10 / jakarta.servlet, so it differs in
> servlet API as well as in appserver's presence — corroboration rather than a
> single-variable comparison.)
>
> Worth noting for whichever fix lands: the entry is discarded with no
> diagnostic. Even before the behaviour changes, a debug line on that return path
> saying an entry was supplied and not applied would turn this from "read the
> selector" into a one-line diagnosis.
