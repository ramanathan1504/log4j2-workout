# Per-app notes

One entry per app target: what it needs to run, what makes it special, and what
bites when it is skipped or run wrong. This is the *operational* companion to
the site's Applications page, which says what each app exists to reach.

Everything here is encoded in `bench` and can be re-derived from it —
`extra_jvm_args_for`, `requires_config_for`, `requires_app_for`, `min_java_for`,
`min_log4j_for`, `INTERACTIVE_APPS`, `APPS_2X_ONLY`. When this file and the
script disagree, **the script is right**.

## What the columns mean

| Term | Meaning |
|---|---|
| **2.x-only** | No 3.x release path. A 3.x cell is a `SKIP` with that reason |
| **Interactive** | `main()` serves until interrupted, so no matrix cell can finish it |
| **Needs config** | Asserts on one appender; any other configuration is a meaningless cell, not a failure |
| **Config-locked** | Its configuration's destinations are supplied in-process by this app, so no other app can load them |
| **Auto flags** | `bench` adds these itself. Listed because they explain what breaks without them |

---

## General-purpose

### `core-java`
The default for almost everything. No framework, carries all seven scenarios.
Nothing special is needed to run it.

### `java8-baseline`
The only module compiled at release 8; everything else targets 17. It exists so
the oldest JDK Log4j 2 supports is a real column rather than an assumption. On a
JDK below 17 every *other* app is a `SKIP` and this one still runs.

### `spring-boot-maven`
**Auto flag:** `-Dbench.selfTest=true`. Without it `main()` is
`SpringApplication.run` on a web application and the cell never returns — a
sweep once sat on that for two hours looking exactly like slow progress.
`SelfTestRunner` drives the bench endpoints over real HTTP and exits, so the
servlet stack is still exercised. `BENCH_SPRING_SELFTEST=0` gets the interactive
server back.

Note that Spring Boot's `Log4J2LoggingSystem` reconfigures Log4j during startup
and ignores Log4j's own property — it honours `logging.config`, which `bench`
sets for this app in addition to the usual one.

### `spring-boot-gradle`
**2.x-only.** The same sources built by Gradle, which resolves versions
differently from Maven. Same self-test flag.

---

## Bridges — one per conflict group

These cannot share a classpath. Two together is not a warning, it is a coin toss
or a routing loop.

### `bridges-in` · 2.x-only
**Auto flag:** `-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager`.
`log4j-jul` only takes effect if the JUL `LogManager` is replaced *before any
`java.util.logging` class initialises*, so it has to be a launch flag — setting
the property from `main()` is already too late. Without it the bridge is inert
and JUL keeps its own handlers, with nothing logged to say so.

### `bridges-out` · 2.x-only
`log4j-core` is deliberately **absent**. With core present it would answer
`LogManager.getLogger` and the bridge would never run — while the output looked
identical.

### `bridges-to-jul` · 2.x-only
Separate from `bridges-out` because both supply the API's provider and the
`ServiceLoader` would pick one arbitrarily. Also: under `log4j-to-jul`,
`ThreadContext` is a **no-op** — `JULProvider` registers `NoOpThreadContextMap`,
so code reading back its own trace id gets null.

### `log4j1-bridge` · 2.x-only
The 1.x API on 2.x core. Its configurations live in `configs/log4j1/` and are
selected differently: the 1.x property is `log4j.configuration` (no `File`) and
it takes a **URL**, plus `-Dlog4j1.compatibility=true`. `bench` handles both.

---

## Servlet containers

### `jakarta-web` · 2.x-only · interactive
### `javax-web` · 2.x-only · interactive

**Auto flags:** `--add-opens=java.base/java.io=ALL-UNNAMED` and
`-Dsun.io.useCanonCaches=false`. Tomcat 10.1.34+/9.0.98+ refuse to start a
`DirResourceSet` unless they can confirm the JDK's canonical file name cache is
off (the CVE-2024-56337 fix), and they confirm it by reflectively writing a
static final field in `java.io`. Without both the app dies at startup. This is
what Tomcat's own `catalina.sh` exports.

**Both skip in sweeps** — they serve until interrupted, so a cell would burn the
full timeout and then FAIL, which is 300 seconds spent to learn nothing. They
*are* verified, by hand, and that is where the appserver/`${web:}` finding came
from. The fix is a self-test like `SelfTestRunner`; nobody has written it.

`javax-web` also carries `log4j-appserver` and `log4j-taglib` — all three are
`javax.servlet` and cannot meet the jakarta ones.

---

## Database and messaging

### `db` — needs config `appender-jdbc`
**Runs on embedded H2** (`jdbc:h2:./logs/db/log4jdb`), so it needs no container,
whatever drivers its POM also carries. Under a console-only configuration it is
a `SKIP`, not a failure: an app that counts rows cannot answer a question that
never reaches a database.

### `jpa` · 2.x-only — needs config `appender-jpa` · config-locked
**Auto flag:** `--add-opens=java.base/java.lang=ALL-UNNAMED`. `log4j-jpa`'s
`ThrowableAttributeConverter` reflects into `Throwable.cause` in a **static
initialiser** and catches only `NoSuchFieldException`, so on JDK 16+ the
`InaccessibleObjectException` escapes and the class fails to initialise. The JPA
provider then reports `ExceptionInInitializerError` from a `Class.forName` deep
in its own deployment code, naming neither Log4j, nor the converter, nor the
flag. **log4j-jpa is unusable on any current JDK without this.**

Also: EclipseLink 2.7 cannot drive H2 2.x — its DDL emits H2 1.x syntax and it
treats the failure as a warning, so deployment reports success and every insert
fails with "table not found".

### `jdbc-jndi` — needs config `appender-jdbc-jndi` · config-locked
**Auto flags:** `-Djava.naming.factory.initial=…BenchInitialContextFactory` and
`-Dlog4j2.enableJndiJdbc=true`. Both required, both silent when missing. The
provider is in-process and map-backed — it speaks no protocol and opens no
socket, which is why re-enabling the JDBC half of JNDI is safe here. The bench
never sets `log4j2.enableJndiLookup`, the one CVE-2021-44228 abused.

The JNDI `DataSource` source resolves its name **while the appender is being
built**, so the binding must exist before the first logger acquired anywhere in
the JVM. A conventional `private static final Logger` initialises at class load,
so binding in `main()` is already too late.

### `jms` · 2.x-only · **needs Log4j 2.25.0+** — needs config `appender-jms` · config-locked
**Auto flag:** `-Dlog4j2.enableJndiJms=true`. The broker is ActiveMQ Artemis,
in-VM on `vm://0`. The JNDI environment comes from `jndi.properties` on the
app's classpath, **not** from `-D`: `InitialContext` copies only `java.naming.*`
out of system properties, so `connectionFactory.*` and `queue.*` passed as `-D`
are silently ignored and Artemis falls back to `tcp://localhost:61616`.

`log4j-jakarta-jms` did not exist before 2.25.0, so older versions are a `SKIP`.

### `smtp` · 2.x-only — needs config `appender-smtp` · config-locked
Embedded GreenMail. Note `log4j-jakarta-smtp` contains **no appender** —
`SmtpAppender` lives in `log4j-core` and picks its implementation by
`ServiceLoader`, so adding that jar silently replaces the whole mail stack with
nothing naming which won.

---

## Needs containers

Only these two do. Everything else embeds its infrastructure.

### `network` — needs config `appender-network` · config-locked
```bash
docker compose -f infra/docker-compose.yml up -d kafka syslog mailhog
```
Its `NetworkBench` opens the socket and HTTP listeners in-process on :4560,
:5514 and :8123, which is why no other app can load `appender-network` — under
`core-java` those are just "Connection refused", once per appender per event.
JeroMQ needs a subscriber attached *before* publishing: a PUB socket with no
peer discards silently.

### `nosql` — needs config `appender-nosql`
```bash
docker compose -f infra/docker-compose.yml up -d mongodb couchdb cassandra-init
```

**`cassandra-init`, not `cassandra`.** This is the one that costs people a
session. The bare service starts the node; the `-init` service waits for it to
be healthy, applies `infra/cql/cassandra-init.cql`, and exits. Start the node
alone and the appender stores nothing, **silently**.

The appender cannot create its own keyspace, and the reason is circular rather
than mis-ordered: the DataStax driver pulls in Netty, whose
`InternalLoggerFactory` calls `LogManager.getLogger` *while the driver is
initialising*. That first acquisition configures Log4j → starts the Cassandra
appender → `cluster.connect("log4j")` — a keyspace the code that would create it
has not reached, because that code is what is initialising the driver.
`startupInternal` runs once, so the appender is dead for the rest of the run.

Not deliberately absent from `requires_app_for`: Mongo, CouchDB and Cassandra
are in containers, so any app can write to them. Only *in-process*
infrastructure creates that coupling.

Three environment limits here, none of them Log4j defects:

| | |
|---|---|
| Cassandra pinned to **4.1, not 5** | `log4j-cassandra` ships DataStax driver 3.11, which negotiates protocol v4 at most. Against 5.0 the connection neither completes nor errors — the run stalls with no output |
| Cassandra needs 60–90s after start | Much longer than anything else. A run started too early fails at appender *construction*, so the appender is absent for the whole run rather than retrying. `docker compose ps` reports `(healthy)` when it is genuinely up |
| CouchDB cannot work with modern Gson | LightCouch 0.2.0 calls `registerTypeAdapter(JsonObject.class, …)`, which Gson 2.11 forbids, so the provider cannot be constructed — and the message names neither Gson's version nor `log4j-couchdb` |

Two filed defects come out of this app:
[#4241](https://github.com/apache/logging-log4j2/issues/4241) (`write()` ignores
`isRunning()`, so a failed-startup manager keeps accepting events and NPEs per
event) and [#4242](https://github.com/apache/logging-log4j2/issues/4242) (the
leaked DataStax `Cluster`, whose non-daemon threads stop the JVM exiting).

---

## Other

### `custom-plugins` · 2.x-only
2.x-only for a different reason from the rest: `@Plugin` **moved package**
between the lines — `org.apache.logging.log4j.core.config.plugins` on 2.x,
`org.apache.logging.log4j.plugins` on 3.x — so plugin sources cannot compile
against both without two source sets. That is a fact about writing Log4j
plugins, not a bench defect, and it is why `log4j-plugin-processor` is the one
3.x module never reached.

### `spring-cloud-config`
Config-server-driven reload, with the server embedded — no container. The
`Log4j2EventListener` finding ([#4244](https://github.com/apache/logging-log4j2/issues/4244))
came from here: its `@ConditionalOnProperty` has no effect, because
`spring.factories` registers it as an `ApplicationListener` and those are
instantiated without bean definitions — conditions are a bean-definition
mechanism.

---

## One config that fails on purpose

`appender-composite` cannot demonstrate `Failover` without a primary that fails,
and a failing appender reports itself. It emits **exactly three** errors, all
naming `BrokenPrimary`, and its header says so. Judge that one by whether
`logs/composite/failover.log` filled up, not by the error count.
