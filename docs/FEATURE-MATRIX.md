# Log4j Feature Matrix — Complete Coverage Catalog

Extracted from the local source clone `~/apache/logging-log4j2` (branch `2.x`, at `04c93c1d33`).
**293 plugins across 59 modules.** This is the checklist the bench must cover.

Legend: `[ ]` not yet covered by the bench · `[x]` covered · `(test)` = test-only fixture, low priority

---

## 1. Appenders (35 total, ~27 production)

### File-based
| Plugin | Module | Notes |
|---|---|---|
| `File` | log4j-core | baseline |
| `RollingFile` | log4j-core | + policies/strategies, see §6 |
| `RandomAccessFile` | log4j-core | ByteBuffer-backed |
| `RollingRandomAccessFile` | log4j-core | |
| `MemoryMappedFile` | log4j-core | mmap, region length |

### Console / stream
| `Console` | log4j-core | SYSTEM_OUT / SYSTEM_ERR, direct + ANSI |
| `OutputStream` | log4j-core | |
| `Writer` | log4j-core | |

### Network
| `Socket` | log4j-core | TCP/UDP/SSL, reconnect delay |
| `Syslog` | log4j-core | BSD + RFC5424 |
| `Http` | log4j-core | |
| `SMTP` | log4j-core | javax; see `log4j-jakarta-smtp` for jakarta |
| `JMS-Javax` | log4j-core | |
| `JMS-Jakarta` | log4j-jakarta-jms | |
| `JeroMQ` | log4j-core | ZeroMQ |
| `Kafka` | log4j-core | |

### Database
| `JDBC` | log4j-core | + `ColumnMapping`, `Column`, connection sources §7 |
| `NoSql` | log4j-core | generic wrapper |
| `MongoDb` | log4j-mongodb | |
| `MongoDb4` | log4j-mongodb4 | |
| `CouchDB` | log4j-couchdb | |
| `Cassandra` | log4j-cassandra | |
| `JPA` | log4j-jpa | |

### Composite / routing
| `Async` | log4j-core | ArrayBlockingQueue by default, see §8 |
| `Failover` | log4j-core | + `Failovers` |
| `Rewrite` | log4j-core | + rewrite policies §7 |
| `Routing` | log4j-core | + `Routes`, `Route`, `IdlePurgePolicy` |
| `ScriptAppenderSelector` | log4j-core | needs `log4j-script` |
| `AppenderSet` | log4j-core | |

### Container / web
| `Servlet` | log4j-web (javax) / log4j-jakarta-web | |

### No-op & test fixtures
`Null`, `CountingNoOp` · `(test)` `List`, `AlwaysFail`, `FailOnce`, `Block`, `EncodingAppender`, `JsonEncodingAppender`

---

## 2. Layouts (15 core + external)

| Layout | Module |
|---|---|
| `PatternLayout` | log4j-core — the big one, see §4 converters |
| `JsonTemplateLayout` | log4j-layout-template-json — see §5 |
| `JsonLayout` | log4j-core (legacy, Jackson) |
| `XmlLayout` | log4j-core |
| `YamlLayout` | log4j-core |
| `GelfLayout` | log4j-core — Graylog |
| `HtmlLayout` | log4j-core |
| `CsvLogEventLayout` | log4j-csv |
| `CsvParameterLayout` | log4j-csv |
| `SyslogLayout` | log4j-core — BSD/RFC3164 |
| `Rfc5424Layout` | log4j-core — + `LoggerFields`, structured data |
| `MessageLayout` | log4j-core |
| `SerializedLayout` | log4j-core — deprecated |
| `Log4j1XmlLayout` | log4j-1.2-api |
| `Log4j1SyslogLayout` | log4j-1.2-api |
| **`EcsLayout`** | **external** — `co.elastic.logging:log4j2-ecs-layout` (cached in ~/.m2) |

---

## 3. Filters (16)

`BurstFilter` · `DenyAllFilter` · `DynamicThresholdFilter` · `LevelMatchFilter` · `LevelRangeFilter` ·
`MapFilter` · `MarkerFilter` · `NoMarkerFilter` · `MutableThreadContextMapFilter` · `RegexFilter` ·
`ScriptFilter` · `StringMatchFilter` · `StructuredDataFilter` · `ThreadContextMapFilter` ·
`ThresholdFilter` · `TimeFilter`

Each is placeable at **4 scopes**: context-wide, logger, appender, appender-ref. The bench must
exercise all four, plus `Filters` composition and `onMatch`/`onMismatch` combinations.

---

## 4. Pattern Converters (41 log-event + 26 type converters)

### Event data
`%c` Logger · `%C` ClassName · `%d` Date · `%enc` encode · `%ex`/`%throwable` Throwable ·
`%xEx` ExtendedThrowable · `%rEx` RootThrowable · `%F` FileLocation · `%K` Map · `%l` FullLocation ·
`%L` LineLocation · `%m` Message · `%M` MethodLocation · `%marker` · `%markerSimpleName` ·
`%n` LineSeparator · `%N` NanoTime · `%p` Level · `%pid` ProcessId · `%r` RelativeTime ·
`%sn` SequenceNumber · `%t` Thread · `%T` ThreadId · `%tp` ThreadPriority · `%u` Uuid ·
`%X` MDC · `%x` NDC · `%fqcn` LoggerFqcn · `%endOfBatch`

### OpenTelemetry (2.24+)
`%spanId` · `%traceId` · `%traceFlags`  ← **relevant to your existing OTel controller**

### Log4j 1.x compat
`Log4j1LevelPatternConverter` · `Log4j1MdcPatternConverter` · `Log4j1NdcPatternConverter`

### Formatting/transforming
`%highlight` · `%style` · `%maxLen` · `%notEmpty` · `%equals` · `%equalsIgnoreCase` ·
`%replace` · `%repeat` · `yellow` and other colour aliases

### Pattern selectors
`LevelPatternSelector` · `MarkerPatternSelector` · `ScriptPatternSelector` (+ `PatternMatch`)

### Type converters (26)
`BigDecimal` `BigInteger` `Boolean` `Byte` `ByteArray` `Character` `CharacterArray` `Charset`
`Class` `CronExpression` `Double` `Duration` `File` `Float` `InetAddress` `Integer` `Level`
`Long` `Path` `Pattern` `SecurityProvider` `Short` `String` `URI` `URL` `UUID` `RecyclerFactory`

---

## 5. JsonTemplateLayout Resolvers (20)

`caseConverter` · `counter` · `endOfBatch` · `exception` · `exceptionRootCause` · `level` ·
`logger` · `mainMap` · `map` · `marker` · `message` · `messageParameter` · `pattern` · `source` ·
`thread` · `threadContextData` (MDC) · `threadContextStack` (NDC) · `timestamp`

Interceptors: `EventAdditionalFieldInterceptor` · `EventRootObjectKeyInterceptor`
Plus built-in event templates: `EcsLayout.json`, `GelfLayout.json`, `LogstashJsonEventLayoutV1.json`,
`GcpLayout.json`, `JsonLayout.json`

---

## 6. Rollover — policies & strategies

**Triggering policies:** `SizeBasedTriggeringPolicy` · `TimeBasedTriggeringPolicy` ·
`CronTriggeringPolicy` · `OnStartupTriggeringPolicy` · `NoOpTriggeringPolicy` · `Policies` (composite)

**Rollover strategies:** `DefaultRolloverStrategy` · `DirectWriteRolloverStrategy`

**Delete actions:** `Delete` · `IfFileName` · `IfLastModified` · `IfAccumulatedFileCount` ·
`IfAccumulatedFileSize` · `IfAll` · `IfAny` · `IfNot` · `ScriptCondition` · `SortByModificationTime`

**Post-rollover:** `PosixViewAttribute` · compression via `log4j-compress`
(gz, zip, bz2, deflate, pack200, xz, zstd, lz4, …) · `maxCompressionDelaySeconds` ← **issue #4012**

---

## 7. Connection sources & policies

**JDBC connection sources:** `ConnectionFactory` · `DataSource` · `DriverManager` ·
`PoolingDriver` (+ `PoolableConnectionFactory`)

**Rewrite policies:** `MapRewritePolicy` · `PropertiesRewritePolicy` · `LoggerNameLevelRewritePolicy`

**Advertisers:** `Default` · `MulticastDns`

---

## 8. Async infrastructure

- `AsyncLogger` / `AsyncRoot` — LMAX Disruptor, fully async
- `AsyncAppender` — queue-backed, appender-level
- **BlockingQueueFactories:** `ArrayBlockingQueue` · `DisruptorBlockingQueue` (Conversant) ·
  `JCToolsBlockingQueue` · `LinkedTransferQueue`
- `AsyncWaitStrategyFactory` — Block / Timeout / Sleep / Yield
- Mixed sync+async configurations
- Garbage-free mode (`log4j2.enableThreadlocals`, `log4j2.garbagefreeThreadContextMap`)

---

## 9. Lookups (18)

`${bundle:}` · `${ctx:}` · `${date:}` · `${docker:}` · `${env:}` · `${event:}` · `${java:}` ·
`${jndi:}` · `${log4j:}` · `${lower:}` · `${main:}` · `${map:}` · `${marker:}` · `${sd:}` ·
`${spring:}` · `${sys:}` · `${upper:}` · `${web:}`

---

## 10. Arbiters — conditional configuration (7)

`ClassArbiter` · `DefaultArbiter` · `EnvironmentArbiter` · `ScriptArbiter` · `SystemPropertyArbiter` ·
`SpringProfile` · `Select`

---

## 11. Configuration formats

| Format | Factory | Module |
|---|---|---|
| XML | `XmlConfigurationFactory` | log4j-core |
| JSON | `JsonConfigurationFactory` | log4j-core (needs Jackson) |
| YAML | `YamlConfigurationFactory` | log4j-core (needs Jackson YAML) |
| Properties (2.x) | `PropertiesConfigurationFactory` | log4j-core — **removed in 3.x** |
| Properties (3.x) | `JavaPropsConfigurationFactory` | log4j-config-properties — Jackson java-properties, different key layout |
| Log4j 1.x properties | `Log4j1PropertiesConfigurationFactory` | log4j-1.2-api |
| Log4j 1.x XML | `Log4j1XmlConfigurationFactory` | log4j-1.2-api |

Also: composite configuration (comma-separated files), `ConfigurationBuilder` programmatic API,
`MonitorResources`/`MonitorResource` auto-reload, `http` `Watcher` for remote config,
`ScriptRef`/`ScriptFile`/`Scripts`, `CustomLevels`/`CustomLevel`, `Properties`/`Property`,
`KeyValuePair`, `Ssl`/`KeyStore`/`TrustStore`, `SocketOptions`/`SocketPerformancePreferences`.

---

## 12. API bridges & adapters

| Module | Direction |
|---|---|
| `log4j-slf4j2-impl` | SLF4J 2.x → Log4j |
| `log4j-slf4j-impl` | SLF4J 1.7 → Log4j |
| `log4j-to-slf4j` | Log4j API → SLF4J |
| `log4j-jcl` | Commons Logging → Log4j |
| `log4j-jul` | java.util.logging → Log4j |
| `log4j-to-jul` | Log4j API → JUL |
| `log4j-jpl` | JDK9 System.Logger → Log4j |
| `log4j-1.2-api` | Log4j 1.x API → Log4j 2/3 |
| `log4j-iostreams` | Stream/Writer/Reader → Log4j |
| `log4j-taglib` | JSP tags |

**Log4j 1.x bridge surface (23 plugins):** `org.apache.log4j.ConsoleAppender`, `FileAppender`,
`RollingFileAppender`, `DailyRollingFileAppender`, `AsyncAppender`, `net.SocketAppender`,
`net.SyslogAppender`, `varia.NullAppender`, `rewrite.RewriteAppender`,
`rolling.RollingFileAppender`, `rolling.TimeBasedRollingPolicy`, `rolling.SizeBasedTriggeringPolicy`,
`rolling.CompositeTriggeringPolicy`, layouts (`PatternLayout`, `SimpleLayout`, `TTCCLayout`,
`HTMLLayout`, `xml.XMLLayout`), filters (`varia.DenyAllFilter`, `LevelMatchFilter`,
`LevelRangeFilter`, `StringMatchFilter`)

---

## 13. Framework & container integration

`log4j-spring-boot` (+ `SpringProfile` arbiter, `${spring:}` lookup) ·
`log4j-spring-cloud-config-client` · `log4j-web` (javax) · `log4j-jakarta-web` ·
`log4j-appserver` (Tomcat/Jetty/WildFly) · `log4j-docker` (`${docker:}`) ·
`log4j-kit` · `log4j-plugins` / `log4j-plugin-processor` (custom plugin authoring)

---

## 14. Message types

`SimpleMessage` · `ParameterizedMessage` · `FormattedMessage` · `StringFormattedMessage` ·
`MessageFormatMessage` · `ObjectMessage` · `MapMessage` · `StringMapMessage` · `ObjectMapMessage` ·
`StructuredDataMessage` · `ThreadDumpMessage` · `MultiformatMessage` · `ReusableMessage` (GC-free) ·
`FlowMessage` (entry/exit) · Lambda/`Supplier` suppliers

---

## 15. Version axes

| Line | Source | Status in ~/.m2 |
|---|---|---|
| **1.x** | `log4j:log4j:1.2.17`, `ch.qos.reload4j` | cached |
| **1.x bridge** | `log4j-1.2-api` | cached |
| **2.x releases** | 2.24.1, 2.25.4, 2.25.5, 2.26.0, 2.26.1, 2.27.0 | cached |
| **2.x snapshot** | 2.27.0-SNAPSHOT (branch `2.x`) | cached |
| **3.x** | 3.0.0-SNAPSHOT (branch `main`) | ⚠ **core only — `log4j-api:3.0.0-SNAPSHOT` missing** |

⚠ To get real 3.x coverage: `cd ~/apache/logging-log4j2 && git worktree add ../log4j-main main && mvn -f ../log4j-main install -DskipTests`

---

## 16. Coverage gaps in the current workspace

**Exercised:** `Console` · `File` · `RollingFile` · `RollingRandomAccessFile` · `Null` ·
`JDBC` (DriverManager, PoolingDriver, Column, ColumnMapping) · `PatternLayout` (nearly every
converter) · `JsonTemplateLayout` (4 built-in templates, inline template, file: template URI) ·
`JsonLayout` · `XmlLayout` · `YamlLayout` · `HtmlLayout` · all 16 filters at all 4 scopes ·
every triggering policy and both rollover strategies · Delete/IfFileName/IfAny/IfAccumulated\* ·
gz/zip/zstd compression · MDC/NDC · markers · `${docker:}` · async · SLF4J bridge ·
OTel converters · **all four Log4j 2 config formats** · **both Log4j 1.x config formats** ·
the 1.x bridge app · servlet container integration (per-webapp `LoggerContext`, `${web:}`) ·
Spring Boot under both Maven and Gradle.

**Module reach: 33 of 42 shippable 2.x modules** are on some app's classpath.
`./bench coverage` recomputes this from the source clone and the resolved classpaths, so it
does not go stale as either moves.

**No classpath yet (8):** `log4j-appserver` · `log4j-jakarta-jms` · `log4j-jakarta-smtp` ·
`log4j-jdbc-jndi` · `log4j-jpa` · `log4j-spring-cloud-config-client` · `log4j-taglib` ·
`log4j-web` (javax). Each needs either a javax servlet container, a broker, a mail server, a
JPA provider or a config server, which is why they are grouped rather than bolted onto an
existing app.

`log4j-plugin-processor` is covered by `apps/custom-plugins`, which is the one of these that
needed no infrastructure: a custom appender, filter, lookup and pattern converter, discovered
through the `Log4j2Plugins.dat` the processor generates. It checks the build-time and run-time
halves separately, since either can fail while the other passes.

**On a classpath but not yet driven by a config or scenario:** Syslog · Http ·
Kafka · JeroMQ · SMTP · MongoDB · Cassandra · CouchDB — every one of them a
network destination needing its container from `infra/docker-compose.yml`, so
they land with the infra work — plus Ssl/KeyStore, which needs a generated
keystore, and custom plugin authoring, which needs a compile-time test rather
than a config.

Newly driven, each in every format that can express it, verified identical across formats:

| Config | Covers |
|---|---|
| `appender-composite` | `Failover` + `Failovers` · `Routing` + `Routes`/`Route`/`IdlePurgePolicy` · `Rewrite` with all three policies (`MapRewritePolicy`, `PropertiesRewritePolicy`, `LoggerNameLevelRewritePolicy`) · `AppenderSet` · `ScriptAppenderSelector` · `Async` · `Socket` (as a deliberately dead primary) |
| `arbiters` | `SystemPropertyArbiter` · `EnvironmentArbiter` · `ClassArbiter` · `ScriptArbiter` · `Select` · `DefaultArbiter`. **XML/JSON/YAML only** — the properties format has no arbiter support at all. `SpringProfile` needs a Spring Environment and belongs with the Spring Boot app |
| `layout-remaining` | `GelfLayout` · `CsvLogEventLayout` · `CsvParameterLayout` · `Rfc5424Layout` + `LoggerFields` · `SyslogLayout` · `MessageLayout` · Elastic's `EcsLayout` |
| `async-queues` | `AsyncLogger` · `AsyncAppender` · all four `BlockingQueueFactories` (`ArrayBlockingQueue`, `DisruptorBlockingQueue`, `JCToolsBlockingQueue`, `LinkedTransferQueue`) · `AsyncWaitStrategyFactory` · mixed sync/async in one context |
| `custom-levels` | `CustomLevels`/`CustomLevel`, custom levels in `ThresholdFilter`/`LevelMatchFilter`/`LevelRangeFilter`, and a `LevelPatternSelector` keyed on one. Driven by the `custom-levels` scenario, since the API has no `audit()` method |
| `rollover-advanced` | `Delete` with `IfAll`/`IfNot`/`SortByModificationTime` · `ScriptCondition` · `PosixViewAttribute` · `filePermissions` |
| `appender-file-variants` | `MemoryMappedFile` · `RandomAccessFile` · `File` with `immediateFlush`/`bufferedIO`/`createOnDemand`/`filePermissions` — the four ways bytes reach the disk, which differ in durability rather than output |
| `lookups` | All 18 built-in lookups **from a configuration** — the config-time (`$`) and per-event (`$$`) paths, which the `lookups` scenario does not exercise. `${docker:}`, `${web:}`, `${spring:}` and `${jndi:}` are present and deliberately unresolved |

Every layout Log4j ships is now exercised except `SerializedLayout`, which is deprecated and
refuses to build without `log4j2.enableSerialization`.

The distinction matters: the first list is what the bench *cannot* reach however it is
invoked; the second is what it could reach today with a config nobody has written yet.

`programmatic` (ConfigurationBuilder) is covered by the scenario of that name rather than by a
config file, since by definition it has none.

---

## 17. Findings from building the bench

Things the configs above turned up, verified against the source clone rather than the docs.

| Finding | Where |
|---|---|
| **log4j-appserver and log4j-web together break the per-webapp `LoggerContext`.** log4j-appserver routes the container's own logging into Log4j, so Tomcat logs through Log4j while starting — before any webapp initialises — creating a context keyed to the parent classloader. log4j-web's SCI then hits `ClassLoaderContextSelector.locateContext`'s parent-walk, which returns that context without applying the `ServletContext` entry: `${web:}` never resolves and the shared context is renamed to the webapp's display-name. Same root cause as the entry-dropping bug above, but here it is unavoidable rather than a test artifact, since routing container logging is exactly what log4j-appserver is for. `apps/jakarta-web` is the control — same code, no appserver, and the binding works | `apps/javax-web` |
| `ServletAppender` cannot appear in a configuration loaded by the JVM-wide `LoggerContext`: with no `ServletContext` yet it fails to build with "No servlet context is available", and the error is permanent even if nothing references it. It is only constructible from a webapp-scoped configuration | `configs/xml/appender-servlet.xml` |
| `log4j-jakarta-smtp` contains no appender at all — `SmtpAppender` lives in log4j-core and is written against javax.mail, then selects its implementation with `ServiceLoader.load(MailManagerFactory.class)`. So the jar on the classpath silently replaces the whole mail stack, with nothing in the configuration or the log naming which one won | `apps/smtp` |
| **log4j-jpa cannot initialise on any JDK 16+ without `--add-opens java.base/java.lang=ALL-UNNAMED`.** `ThrowableAttributeConverter`'s static initialiser calls `Throwable.class.getDeclaredField("cause").setAccessible(true)`, and its `catch` handles only `NoSuchFieldException`, so JDK 16's `InaccessibleObjectException` escapes as `ExceptionInInitializerError`. The JPA provider then reports a deployment failure from a `Class.forName` inside its own code, naming neither Log4j, nor the converter, nor the flag | `apps/jpa` |
| log4j-jpa's `*Json*AttributeConverter` classes build a Jackson `ObjectMapper` in a static initialiser, but log4j-jpa does not declare Jackson. Without it the failure is the same shapeless `ExceptionInInitializerError` from inside the provider, with nothing naming Jackson | `apps/jpa` |
| With `exclude-unlisted-classes=true`, Log4j's own attribute converters must each be listed in `persistence.xml` — the provider names only the first one it needed, so the list is best written whole rather than discovered one exception at a time | `apps/jpa` |
| EclipseLink 2.7 (the last javax.persistence release) cannot drive H2 2.x: its DDL emits `ID BIGINT IDENTITY NOT NULL`, which H2 2.x rejects, and its IDENTITY key fetch is `CALL IDENTITY()`, a function H2 2.x removed. EclipseLink treats the failed DDL as a warning, so deployment reports success and every insert then fails with "table not found" — the real syntax error appears only in H2's trace file. Using a sequence generator avoids both | `apps/jpa` |
| log4j-plugin-processor refuses to compile a `@PluginBuilderAttribute` field with no public setter, naming the field and the `@SuppressWarnings("log4j.public.setter")` escape. One of the few plugin-authoring mistakes caught at build time rather than as a runtime surprise — worth knowing before writing one | `apps/custom-plugins` |
| The `packages` configuration attribute is now deprecated on 2.x (`WARN The use of package scanning to locate Log4j plugins is deprecated`) and gone on 3.x, where discovery is descriptor-only. Setting it also masks a build where the annotation processor never ran, since scanning finds the plugins anyway | `configs/xml/custom-plugins.xml` |
| **`Delete`'s sibling conditions are order-sensitive, and the properties format cannot express order.** `IfAccumulatedFileCount` is stateful — it counts every file it is asked about — so placing it before `IfFileName` makes it count the active `app.log` too and the identical policy keeps 3 files instead of 4. Measured: XML `IfFileName`-first keeps 4, `IfAll`-first keeps 3, and the properties build produced `{IfAll[...], IfFileName}` regardless of file order, because `java.util.Properties` is a `Hashtable`. Nesting the conditions inside `IfFileName` removes the dependence and is the only portable formulation | `configs/*/rollover-advanced.*` |
| `SortByModificationTime`'s attribute is `recentFirst`, not `ascending`. The wrong name is rejected as an invalid attribute while the sorter still builds with its default, so the retention order silently stays whatever the default is | `configs/*/rollover-advanced.*` |
| `AsyncWaitStrategyFactory`'s attribute is `class`, not the builder field name `factoryClassName`. Using the field name fails `@Required` with "cannot be configured without a factory class name", which reads as a missing attribute rather than a misspelled one | `configs/*/async-queues.*` |
| In the properties format a custom level is `customLevel.<NAME> = <int>` — the key IS the name. The nested spelling every other element uses (`customLevel.audit.name = AUDIT`) makes the builder parse "AUDIT" as an integer, and the `NumberFormatException` escapes configuration, killing the application at its first log call | `configs/properties/custom-levels.properties` |
| **`CsvParameterLayout` throws NPE on any event with no parameters.** `Message.getParameters()` is null for `SimpleMessage` and for the plain `logger.info("text")` form, and `toSerializable` passes it straight to `CSVFormat.printRecord` → `NullPointerException: Cannot read the array length because "values" is null`. One per event, with no recovery, so the layout is unusable against ordinary traffic | `configs/*/layout-remaining.*` |
| `MessageLayout.toByteArray()` is `return null;` — it only implements `toSerializable`, returning the `Message` object. So it works with appenders that consume messages (JMS) and fails on every event with File, Console, Socket or any other stream appender, producing an empty file and one error per event | `configs/*/layout-remaining.*` |
| `Rfc5424Layout`'s `mdcId`/`sdId` must not be written as `name@enterprise`, even though that is exactly what appears on the wire — Log4j appends the enterprise number itself, and an `@` fails the layout with "Structured id name cannot contain an '@'". The appender then falls back to its default layout, so the output looks like a plain-text log rather than a broken one | `configs/*/layout-remaining.*` |
| Under `log4j-to-jul`, `ThreadContext` is a no-op on the Log4j side as well as the JUL side: `JULProvider` registers `NoOpThreadContextMap.INSTANCE`, so `ThreadContext.put()` discards the value and reading it back returns nothing. Code that stores a trace id and later reads its own MDC gets null, not merely unrendered output | `apps/bridges-to-jul` |
| `SimpleMessage` and `MapMessage` implement both `Message` and `CharSequence`, and `Logger` overloads for each, so `logger.info(new SimpleMessage(...))` does not compile — it is ambiguous and needs a cast | `apps/java8-baseline` |
| Log4j 2's `JsonConfiguration` enables `ALLOW_COMMENTS`; Log4j 3's does not, and does not report a parse error either — `root` is left null and the first logger call dies with an NPE inside `JsonConfiguration.setup`, surfacing as `ExceptionInInitializerError`. A commented JSON config works on every 2.x line and hard-fails on 3.x | `configs/json/` (see its README) |
| **The Log4j 2 properties config format does not exist in 3.x.** `PropertiesConfigurationFactory` is absent from the 3.x source entirely; `log4j-config-properties` ships `JavaPropsConfigurationFactory` instead, a Jackson java-properties reader that maps onto the same tree as JSON/YAML and so uses completely different keys. The module is on the bench's 3.x classpath and still cannot read these files — Log4j falls back to `DefaultConfiguration` without a word | `configs/properties/` |
| Log4j 3 reads `log4j.configuration.location`, not `log4j.configurationFile`. Passing the 2.x name against 3.x does not fail — Log4j falls back to `DefaultConfiguration` and logs to the console, so a 3.x run can look healthy while testing nothing but the default config | `bench` `cmd_run` |
| `NullAppender`'s factory takes only a name — nesting any filter under it fails plugin binding with "no parameter that matches element", visible only in the status logger | `configs/*/filter-all.*` |
| The properties format cannot express **arbiters at all**. `PropertiesConfigurationBuilder` recognises only `property`, `script`, `customLevel`, `filter`, `appender`, `logger`, `rootLogger`, and filing an arbiter under `appender.` throws `ConfigurationException: No name attribute provided for Appender` — which escapes configuration into `LogManager.getLogger`, killing the application with `ExceptionInInitializerError`. Not survivable and not silent, unlike most config mistakes | `configs/properties/` (see its README) |
| The properties format cannot express a `Filters` composite: `PropertiesConfigurationBuilder.createFilter` always passes `onMatch`/`onMismatch`, which `CompositeFilter` does not declare, so every such config logs an invalid-attribute error. Appender/logger/appender-ref scopes are therefore limited to one filter each | `configs/properties/filter-all.properties` |
| `maxCompressionDelaySeconds` is not an attribute of `DefaultRolloverStrategy` — it appears nowhere in the 2.x or 3.x source | `configs/*/rollover-full.*` |
| `RollingFileManager` builds its async executor with `Log4jThreadFactory.createThreadFactory()`, i.e. **non-daemon** threads, started lazily on the first compressing rollover. The JVM then cannot begin shutdown, and the shutdown hook that would stop Log4j only runs once shutdown has begun — so a short-lived app with a compressing rolling appender hangs on exit unless it calls `LogManager.shutdown()` itself | `apps/core-java` `Bench.main` |
| `ClassLoaderContextSelector.locateContext` returns a parent classloader's context without applying the `Map.Entry` it was given. When log4j-core sits on a shared/parent classloader and a context already exists there, `log4j-jakarta-web`'s SCI silently fails to bind the `ServletContext`: every `${web:}` lookup goes unresolved and the shared context is renamed to the webapp's path | `apps/jakarta-web` |
| Maven resolves a shared transitive dependency once and keeps the winning path's exclusions; Gradle unions all paths. So excluding `spring-boot-starter-logging` from `spring-boot-starter-web` alone suffices in Maven but leaves Logback on the Gradle classpath via the actuator | `apps/spring-boot-gradle` |
| Gradle platforms contribute constraints resolved by highest-version-wins, so Spring Boot's `log4j2.version` pin silently overrides a request for an older Log4j unless forced | `apps/spring-boot-gradle` |