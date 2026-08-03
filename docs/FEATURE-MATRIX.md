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
| Properties | `PropertiesConfigurationFactory` | log4j-core |
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

Currently exercised: `Console`, `RollingFile`, `JsonTemplateLayout`, `PatternLayout`, MDC,
markers, `${docker:}`, JDBC, MongoDB, async, SLF4J bridge, OTel converters.

**Not yet exercised (the work list):** Syslog · Socket · SMTP · JMS · Kafka · JeroMQ · Http ·
Cassandra · CouchDB · JPA · MemoryMappedFile · RandomAccessFile · Failover · Routing · Rewrite ·
AppenderSet · ScriptAppenderSelector · Servlet(jakarta) · GelfLayout · HtmlLayout · XmlLayout ·
YamlLayout · CsvLayouts · Rfc5424Layout · **EcsLayout** · 13 of 16 filters · all 7 arbiters ·
15 of 18 lookups · JSON/YAML/Properties config formats · Log4j 1.x bridge (all 23 plugins) ·
BlockingQueueFactories · Delete/IfAll/IfAny actions · PosixViewAttribute · custom levels ·
Ssl/KeyStore · composite configuration · programmatic ConfigurationBuilder · `log4j-iostreams` ·
`log4j-jul` · `log4j-jcl` · `log4j-jpl` · `log4j-taglib` · custom plugin authoring