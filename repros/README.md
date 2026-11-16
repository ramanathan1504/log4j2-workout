# Repros

One directory per reproduction of an apache/logging-log4j2 issue or pull request. Each directory holds a standalone Maven app, a `run.sh`, the captured `output/`, and a `README.md` with the mechanism and the results table.

## Running one

```bash
repros/pr-4469/log4j-pr-4469-repro/run.sh 2.26.1 2.26.1
repros/pr-4469/log4j-pr-4469-repro/run.sh <dir of log4j jars> 2.x-HEAD
```

`run.sh` takes a released version, resolved from Maven Central, or a directory of jars built from a branch. With a directory, it resolves third-party dependencies at the baseline version and swaps in the `log4j-*` jars from the directory, so nothing is installed into `~/.m2`. Each case prints `PASS` or `FAIL` with the observed value, judged from what was actually written (file contents, received bytes, threads, StatusLogger lines), never from the exit code.

## The 2026-11-16 sweep

Every code-changing pull request the maintainer authored or reviewed that had no repro, 93 in all, was worked against the release before it and the build that contains it:

| Result | Count |
|---|---|
| Reproduces: baseline release FAIL, fixed build PASS | 83 (72 merged, 11 open) |
| No difference between the two | 1 |
| Nothing to reproduce: test, docs or build changes only | 9 |

The fixed builds were 2.x HEAD `ea28f5a78b`, main HEAD `2bb673f650`, and each open pull request at its head as of 2026-11-16.

## Problems the runs found

| PR | Problem | Seen on |
|---|---|---|
| [#4410](pr-4410/README.md) | With both Jackson 2 and Jackson 3 present, `additivity: yes` in YAML is read as `false` | 2.x HEAD |
| [#4400](pr-4400/README.md) | `epochMilli = -1` formats as empty, because the thread-local cache uses -1 as its sentinel | 2.26.1 and 2.x HEAD |
| [#4329](pr-4329/README.md) | A reconfigure without a custom `ConfigurationFactory` still loses the programmatic logger | 2.x HEAD |
| [#4153](pr-4153/README.md) | A spurious "invalid attributes onMatch, onMismatch" ERROR is logged | main HEAD |
| [#4364](pr-4364/README.md) | One credential leak is closed only by #4363, so backporting #4364 alone still leaks | backport risk |
| [#4071](pr-4071/README.md) | `maxCompressionDelaySeconds` is rejected on both versions; #4165 `maxRandomDelay` superseded it | 2.26.0 and 2.x HEAD |
| [#4448](pr-4448/README.md) | Reference run of `pr-4234`: zstd writes 0 bytes (NoSuchMethodError with commons-compress 1.27.1) | 2.x HEAD |

## Open pull requests

| PR | Title | State | Baseline | Fixed build | Result | What the run showed |
|---|---|---|---|---|---|---|
| [#4175](pr-4175/README.md) | Add native JsonTemplateLayout resolvers for W3C tracing fields | open | 2.26.1 | PR head | fixed | 2.26.1 and 2.x HEAD: `unknown resolver: traceId`; PR head b10b76ab00 writes traceId/spanId/traceFlags 4bf92f35…/00f067aa0ba902b7/01 into the JSON. |
| [#4267](pr-4267/README.md) | Custom header for smtp appender | open | 2.26.1 | PR head | fixed | 2.26.1 ignores nested Property headers (X-Static=[]). PR head: all headers present, CRLF made safe, X:Invalid rejected. Same result on javax and jakarta. |
| [#4337](pr-4337/README.md) | Fail TLS connections when an `Ssl` key store or trust store cannot be loaded. | open | 2.26.1 | PR head | fixed | TrustStore/KeyStore missing: 2.26.1 delivers 3/3 TLS lines via JVM default trust. PR head delivers 0/0. Valid-store control gets 3/3 on both. |
| [#4393](pr-4393/README.md) | Allow escaping `$`, `:`, `-` and `}` inside `${...}` in StrSubstitutor (#2679) | open | 2.26.1 | PR head | fixed | `$}` layout: 2.26.1 `18:36:38${AD11T+00$} msg`, PR head `13:06:51 msg`; `a$:-b`→`a:-b`. Both review regressions (`$${`, nested values) are fixed at 064c183a2e. |
| [#4411](pr-4411/README.md) | HandlingNoSQL reserved fields | open | 2.26.1 | PR head | fixed | MongoDB with `_id=fixed-id` twice: 2.26.1 stores 1 doc (`E11000 duplicate key`). PR head stores 2 with ObjectId `_id`s and logs one WARN. |
| [#4428](pr-4428/README.md) | Fix `StatusLogger` crash when property access is denied | open | 2.26.1 | PR head | fixed | Policy denies PropertyPermission "*": 2.26.1 StatusLogger fails with `ExceptionInInitializerError`. PR head starts, reads log4j2.StatusLogger.level=INFO per key, logger works. |
| [#4448](pr-4448/README.md) | [main] Honor `compressionLevel` in the rollover strategies (#4234 port) | open | 3.0.0-beta3 | PR head | fixed | gz/zip level 0 ratio 0.029→1.001 (Default, DirectWrite, both tempCompressed branches); 3.0.0-beta3 7× invalid-attribute ERROR; 42 warns once. Ref pr-4234: 2.x HEAD zstd 0-byte (NoSuchMethodError, commons-compress 1.27.1) |
| [#4470](pr-4470/README.md) | Keep the `AsyncLogger` location when the ring buffer is full | open | 2.26.1 | PR head | fixed | Queue-full SYNCHRONOUS withLocation: 2.26.1 and 2.x HEAD Repro.main:68, PR Caller.explicitMethod:4242; classic/builder correct on both; both translators |
| [#4471](pr-4471/README.md) | Fix newline replacement escaping in Rfc5424Layout | open | 2.26.1 | PR head | fixed | newLineEscape "\n": A\nB→A\\nB, SD C\nD→C\\nD; "]": A]B→A\]B; #012 control unchanged; 2.x HEAD same as 2.26.1 |
| [#4472](pr-4472/README.md) | [main] Fix newline replacement escaping in Rfc5424Layout (#4471 port) | open | 3.0.0-beta3 | PR head | fixed | 3.x escapeNL: A\nB→A\\nB, C\nD→C\\nD, A]B→A\]B; #012 control unchanged; main HEAD same as beta3 |
| [#4473](pr-4473/README.md) | Add `createOnDemand` to the random access and memory mapped file appenders | open | 2.26.1 | PR head | fixed | 2.26.1: RAF/MMF/RollingRAF create app.log at startup, 6× invalid createOnDemand ERROR; PR: no file until first event, File control unchanged |

## Merged pull requests

| PR | Title | State | Baseline | Fixed build | Result | What the run showed |
|---|---|---|---|---|---|---|
| [#3874](pr-3874/README.md) | Remove `jvmrunargs` lookup | merged | 2.25.1 | 2.x HEAD | fixed | ${jvmrunargs:0} resolves to -Dprobe=jvmrunargs-probe on 2.25.1; stays literal on 2.x HEAD ea28f5a78b, in both config substitution and layout |
| [#3892](pr-3892/README.md) | Remove `jvmrunargs` lookup (#3874) | merged | 3.0.0-beta3 | main HEAD | fixed | 3.0.0-beta3 resolves ${jvmrunargs:0} to -Dprobe=jvmrunargs-probe; main HEAD 2bb673f650 leaves it literal; control event written on both |
| [#4049](pr-4049/README.md) | Catch `LinkageError` in `ThrowableExtendedStackTraceRenderer#loadClass` (#4028) | merged | 2.26.1 | 2.x HEAD | fixed | Loader throwing NoClassDefFoundError: 2.26.1 drops the event (AppenderLoggingException, 133-byte log); 2.x HEAD writes it with missing frame (321 bytes) |
| [#4070](pr-4070/README.md) | Deprecate externalContext and migrate to Map-backed getObject/putObject | merged | 2.26.1 | 2.x HEAD | fixed | Composite log4jConfiguration: ${web:servletContextName} stays literal on 2.26.1, resolves CompositeTest on 2.x HEAD; single-file control passes both |
| [#4071](pr-4071/README.md) | Add support for max compression delay in compression actions | merged | 2.26.0 | 2.x HEAD | no difference | maxCompressionDelaySeconds rejected as invalid attribute on 2.26.0 and 2.x HEAD; superseded by #4165 maxRandomDelay (HEAD latencies 1.2–1.7s) |
| [#4072](pr-4072/README.md) | Fix `createOnDemand` for Rolling File Appender | merged | 2.26.0 | 2.x HEAD | fixed | createOnDemand=true: log dir exists at startup on 2.26.0, absent on 2.x HEAD/2.26.1; direct-write variant still creates dir on HEAD |
| [#4081](pr-4081/README.md) | Improve `CronExpression` tests to cover daylight saving and scheduling logic | merged | 2.26.0 | 2.x HEAD | fixed | getPrevFireTime(midnight+1ms) returned the previous day on 2.26.0 (Sydney/Santiago fallback, Sydney spring-forward, also plain UTC +201ms): 4/11 fail; 2.x 11/11 pass. |
| [#4116](pr-4116/README.md) | Fix handling of `onMatch` and `onMismatch` attributes in the properties configuration format | merged | 2.26.1 | 2.x HEAD | fixed | onMisMatch in properties: 2.26.1 logs ERROR 'MarkerFilter contains an invalid element or attribute "onMismatch"' (filter still DENY/ACCEPT, output right); 2.x logs none. |
| [#4118](pr-4118/README.md) | Fix NPE in ConfigurationScheduler by ensuring scheduledFuture is chec… | merged | 2.26.1 | 2.x HEAD | fixed | First cron fire forced early: 2.26.1 hits 4 NPE 'caught error' messages, the command never runs and fires 3 times after cancel(); 2.x runs once, 0 after cancel. |
| [#4119](pr-4119/README.md) | Remove the `patternFlags` attribute from `RegexFilter` | merged | 2.26.0 | 2.x HEAD | fixed | Config patternFlags is rejected the same way on both. Deprecated createFilter(DOTALL): 2.26.0 returns ACCEPT, 2.x returns DENY because the flags are now silently ignored. |
| [#4124](pr-4124/README.md) | Improve logging for `LinkageError` scenarios involving the LMAX Disruptor library | merged | 2.26.0 | 2.x HEAD | fixed | With Disruptor 4.0.0, 'LMAX Disruptor version detected: 4' DEBUG line is missing on 2.26.0 and present on 2.x. Disruptor 3.4.4 logs nothing on either version. |
| [#4125](pr-4125/README.md) | Fix KafkaAppender reporting error after successful retry | merged | 2.26.0 | 2.x HEAD | fixed | MockProducer fails the first send and the retry delivers: 2.26.0 still logs 'Unable to write to Kafka in appender' ERROR, 2.x logs 0 errors. |
| [#4133](pr-4133/README.md) | Fix stack trace rendering for exceptions with identity malfunction | merged | 2.26.0 | 2.x HEAD | fixed | Colliding-equals chain outer→middle→inner: 2.26.0 JsonLayout and %xEx render outer,middle only; 2.x HEAD renders all three; plain-chain control passes both. |
| [#4142](pr-4142/README.md) | Fix properties configuration support for per-logger properties | merged | 2.26.1 | 2.x HEAD | fixed | Properties-format logger.db.property/rootLogger.property: 2.26.1 %X empty (subsystem=, region=), no StatusLogger line; 2.x HEAD subsystem=Database, region=eu-west. |
| [#4152](pr-4152/README.md) | [main] Fix RegexFilter NPE when `useRawMsg` is null (#3265 port) | merged | 3.0.0-beta3 | main HEAD | fixed | RegexFilter.createFilter(regex,null,null,DENY,NEUTRAL): beta3 NPE unboxing useRawMsg; main HEAD builds, DENY. XML without useRawMsg works on both (control). |
| [#4153](pr-4153/README.md) | [main] Add `@Required` and validation to `StringMatchFilter` `text` attribute (#3158 port) | merged | 3.0.0-beta3 | main HEAD | fixed | StringMatchFilter without text: beta3 denies every event ([]), build() returns filter; main HEAD ERROR, both lines written, build() null. Extra spurious "invalid attributes onMatch, onMismatch" ERROR. |
| [#4154](pr-4154/README.md) | [main] Reopen log file when rollover is unsuccessful (#3226 port) | merged | 3.0.0-beta3 | main HEAD | fixed | Read-only archive dir, 1KB size trigger, 200 events: beta3 keeps 23/200 (177 copy errors, stream left closed); main HEAD 200/200, 8 failed rollovers, file reopened. |
| [#4156](pr-4156/README.md) | [main] Warn when `@PluginBuilderAttribute` field lacks public setter (#3195 port) | merged | 3.0.0-beta3 | main HEAD | fixed | Custom plugin builder field without setter: beta3 processor compiles silently (exit 0); main HEAD javac ERROR on that field only; setter and suppressed fields not flagged. |
| [#4157](pr-4157/README.md) | [main] Port `InternalLoggerRegistry` from `2.x` (#3418 and #3681 ports) | merged | 3.0.0-beta3 | main HEAD | fixed | 5000 unreferenced loggers after System.gc(): beta3 getLoggers() still holds 5000/5000; main HEAD 0/5000; referenced logger kept on both. |
| [#4165](pr-4165/README.md) | Adds `maxRandomDelay` configure to Rolling Appenders | merged | 2.26.0 | 2.x HEAD | fixed | maxRandomDelay=2 gz delays: 2.26.0 [7,4,9,1,1,2]ms plus "invalid attribute" ERROR; HEAD [902..1928]ms; control <10ms both; stop now waits 335ms |
| [#4171](pr-4171/README.md) | Add native tracing fields to LogEvent to eliminate async MDC overhead | merged | 2.26.1 | 2.x HEAD | fixed | %traceId/%spanId/%traceFlags: 2.26.1 "trace=mainraceId span=%spanId"; HEAD prints provider IDs for sync and AsyncLogger, empty when no span |
| [#4172](pr-4172/README.md) | fix: null-check parseAppender result in log4j-1.2-api XmlConfiguration | merged | 2.26.1 | 2.x HEAD | fixed | log4j1 XML with an uncreatable appender: 2.26.1 NPE, appenders=[], no output; HEAD appenders=[good], event written; new WARN visible only under log4j2.debug |
| [#4180](pr-4180/README.md) | Include free-form message when encoding StructuredDataMessage to XML | merged | 2.26.1 | 2.x HEAD | fixed | StructuredDataMessage XML: 2.26.1 omits free-form text (API and %m{XML}); HEAD emits escaped <message>, distinct from map key "message" |
| [#4184](pr-4184/README.md) | Fix italic and underline ANSI escape codes | merged | 2.26.1 | 2.x HEAD | fixed | %style underline: 2.26.1 ESC[3m, italic unresolved (ESC[m plus WARN); HEAD ESC[4m and ESC[3m; bold control same in both |
| [#4185](pr-4185/README.md) | fix: null-check LoggerDynamicMBean addAppender and AppenderDynamicMBean setLayout | merged | 2.26.1 | 2.x HEAD | fixed | JMX bad class: 2.26.1 addAppender RuntimeMBeanException(NPE), setLayout silently nulls layout so appender drops events; HEAD MBeanException, layout kept, writes |
| [#4186](pr-4186/README.md) | Add literal Context Data (MDC) filtering to JSON Template Layout | merged | 2.26.1 | 2.x HEAD | fixed | EcsLayout with MDC message/log.level: 2.26.1 duplicate keys (2/2), key.allowed config rejected; HEAD 1/1, allowed=[user] gives ctx {"user":"alice"} |
| [#4199](pr-4199/README.md) | Honor ConfigurationFactory active state | merged | 2.26.1 | 2.x HEAD | fixed | log4j2.json+log4j2.xml, no jackson-databind: 2.26.1 DefaultConfiguration, app.log 0 lines; 2.x HEAD XmlConfiguration FromXml, 1 line; diagnostic kept |
| [#4218](pr-4218/README.md) | fix: close streams in LoggerContextAdmin.setConfigLocationUri | merged | 2.26.1 | 2.x HEAD | fixed | Non-consuming factory, 50 setConfigLocationUri calls: 2.26.1 50/50 streams open, FDs +49; 2.x HEAD 0/50 open, FDs +0 |
| [#4224](pr-4224/README.md) | Add missing RootLogger.Builder withFilter alias | merged | 2.26.1 | 2.x HEAD | fixed | RootLogger.Builder.withFilter(Filter): 2.26.1 NoSuchMethodException; 2.x HEAD sets the filter; LoggerConfig.Builder control passes on both |
| [#4230](pr-4230/README.md) | [main] Revamp the Extending, Plugins, Architecture, and Programmatic configuration pages | merged | 3.0.0-beta3 | main HEAD | fixed | OrderComparator ignored @Ordered on beta3 (sort Legacy5,None,Ordered10; custom @Ordered(10) XML factory unused); main HEAD sorts Ordered10 first, factory used |
| [#4249](pr-4249/README.md) | Fix circular references in exceptions | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: %xEx drops packaging, getRootCause=r, JPA StackOverflowError, NoSQL OOM; 2.x HEAD: [classes/:?], r_c_c, one Caused by, depth 2 |
| [#4250](pr-4250/README.md) | Fix NPE in PluginElementVisitor when handling unresolved child elements | merged | 2.26.1 | 2.x HEAD | fixed | Unresolved layout type: 2.26.1 NPE (childType null), StdOut appender absent, 0 lines; 2.x HEAD "Ignoring unresolved element", default PatternLayout, 1 line |
| [#4252](pr-4252/README.md) | Honor spring.cloud.config.watch.enabled on Log4j2EventListener | merged | 2.26.1 | 2.x HEAD | fixed | watch.enabled=false refresh: 2.26.1 reloads config (root INFO→DEBUG); 2.x HEAD stays INFO; control watch.enabled=true reloads to TRACE on both. |
| [#4256](pr-4256/README.md) | Switch the default minimum allowed plugin processor message severity from NOTE to ERROR | merged | 2.26.1 | 2.x HEAD | fixed | Plugin compile, no option: 2.26.1 emits 2 processor notes, 2.x HEAD 0; NOTE opt-in control 2 on both; typo warnings 1 vs 2. |
| [#4264](pr-4264/README.md) | Document XML parser features and clarify Log4j 1 bridge properties | merged | 2.26.1 | 2.x HEAD | fixed | Docs PR; only runtime change: DOMConfigurator/PropertyConfigurator warning link logging.staged.apache.org on 2.26.1 vs logging.apache.org on 2.x HEAD. |
| [#4269](pr-4269/README.md) | Reject dynamic proxies in `FilteredObjectInputStream` | merged | 2.26.1 | 2.x HEAD | fixed | Comparator proxy via FilteredObjectInputStream: accepted on 2.26.1 (Java 8 and 17), rejected on 2.x HEAD; HashMap/File controls unchanged. |
| [#4271](pr-4271/README.md) | Serialize the `LogEvent` message without `MarshalledObject` | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: non-allowlisted GadgetMessage instantiated via MarshalledObject (readObject calls=1). 2.x HEAD: InvalidObjectException "Class is not allowed", calls=0. |
| [#4272](pr-4272/README.md) | Simplify the serialization of `SortedArrayStringMap` | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: forged 146-byte legacy stream allocates keys.length=16777216, non-serializable value→null. 2.x HEAD: InvalidClassException (new UID), value→"non-serializable". |
| [#4274](pr-4274/README.md) | Simplify the serialization of `ObjectArrayMessage` and `ParameterizedMessage` | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: ParameterizedMessage null args NPE; ObjectArrayMessage non-serializable element NotSerializableException. 2.x HEAD: both round-trip ([A, non-serializable, C]). |
| [#4304](pr-4304/README.md) | Forward mail attributes in deprecated `SmtpAppender.createAppender` | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: deprecated createAppender throws NullPointerException: name. 2.x HEAD: appender "Mail" delivers to embedded SMTP, RCPT to@example.org, Subject 4304. |
| [#4316](pr-4316/README.md) | escape scope attribute names and values in DumpTag | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: DumpTag emits raw <name>/<script>alert('xss')</script>. 2.x HEAD: escaped &lt;name&gt;/&lt;script&gt;…; plain and null-toString controls unchanged. |
| [#4319](pr-4319/README.md) | Clear the database appender buffer when committing fails | merged | 2.26.1 | 2.x HEAD | fixed | Persistent commit failure: rows per flush 5→20 (220 sent) vs capped 5 plus discard WARN; ack-lost commit duplicates 15/10 vs 10. Fixed drops rolled-back batch (5/10). |
| [#4321](pr-4321/README.md) | Improve missing logging provider diagnostic | merged | 2.26.1 | 2.x HEAD | fixed | API-only classpath: 2.26.1 prints the bare "could not find a logging provider"; HEAD adds the Simple Logger line and the installation.html#impl link. |
| [#4329](pr-4329/README.md) | Preserve custom Configuration elements under CompositeConfiguration | merged | 2.26.1 | 2.x HEAD | fixed | Composite loses programmatic logger on 2.26.1; HEAD keeps it. Still lost on HEAD reconfigure without a custom ConfigurationFactory. |
| [#4336](pr-4336/README.md) | Grow DatagramOutputStream buffer and drop oversized UDP payloads | merged | 2.26.1 | 2.x HEAD | fixed | 70000-byte UDP event: 2.26.1 logs SocketException "Message too long" ERROR; HEAD drops it with a 65507-limit WARN. 20000-byte control delivered on both. |
| [#4340](pr-4340/README.md) | Compute AsyncLogger location when the caller passes null | merged | 2.26.1 | 2.x HEAD | fixed | AsyncLogger, includeLocation: atInfo().log() location "?" on 2.26.1, Repro.main:12 on HEAD. info() and withLocation() controls correct on both. |
| [#4353](pr-4353/README.md) | Preserve Log4j 1 lookups when converting a properties configuration | merged | 2.26.1 | 2.x HEAD | fixed | Converter: 2.26.1 bakes the converter host path and threshold DEBUG into XML, and the log lands on that host. HEAD emits ${sys:...}, log lands on deploy host. |
| [#4358](pr-4358/README.md) | Honor legacy basic authentication encoding property | merged | 2.26.1 | 2.x HEAD | fixed | logging.auth.encoding=ISO-8859-1 ignored on 2.26.1 (UTF-8 header), honored on HEAD. With no property and latin1 platform: ISO-8859-1 vs UTF-8. |
| [#4359](pr-4359/README.md) | fix: handle SSL stores without an explicit type | merged | 2.26.1 | 2.x HEAD | fixed | trustStoreLocation without type: 2.26.1 throws NullPointerException ("keyStoreType" is null); 2.x HEAD loads store, type=pkcs12, 1 entry. |
| [#4360](pr-4360/README.md) | Fix HTTP appender handling of invalid evaluated headers | merged | 2.26.1 | 2.x HEAD | fixed | CRLF in ${ctx:tenant} header: 2.26.1 received 2/3 events (ERROR Unable to send HTTP); HEAD 3/3, X-Tenant skipped with WARN. |
| [#4363](pr-4363/README.md) | Create a fresh InitialContext for each JndiManager | merged | 2.26.1 | 2.x HEAD | fixed | Two JNDI environments: 2.26.1 one shared manager, broker-b lookup resolved from broker-a, 1 factory call; HEAD distinct managers, 2 calls. |
| [#4364](pr-4364/README.md) | Mask JMS credentials in diagnostic messages | merged | 2.26.1 | 2.x HEAD | fixed | JNDI secret in status log: 2.26.1 4 occurrences per appender (javax+jakarta); HEAD 0, credentials=*****. One leak removed only via #4363 (port risk). |
| [#4368](pr-4368/README.md) | Do not output partially serialized events in `SerializedLayout` | merged | 2.26.1 | 2.x HEAD | fixed | Unserializable marker event: 2.26.1 reader stops WriteAbortedException, read [event-1]; HEAD reads [event-1, event-3], clean EOF. |
| [#4370](pr-4370/README.md) | Escape RFC 5424 delimiters in default MapMessage rendering | merged | 2.26.1 | 2.x HEAD | fixed | Value a"b\c]d: 2.26.1 user="a"b\c]d" unescaped (3 cases FAIL); HEAD user="a\"b\\c\]d", also StructuredDataMessage and %m. |
| [#4371](pr-4371/README.md) | Render HTML formatted pattern messages | merged | 2.26.1 | 2.x HEAD | fixed | %m{html}: 2.26.1 prints "[INFO] " (message empty, 6 chars); HEAD prints XML-escaped message (67 chars), &apos; not &#39;. |
| [#4372](pr-4372/README.md) | Align startup and file timestamp precision | merged | 2.26.1 | 2.x HEAD | fixed | Second LoggerContext rolled app.log the JVM had just created in 7/30 runs on 2.26.1 (7 runs met the condition) and 0/30 on 2.x HEAD (10 met it). |
| [#4373](pr-4373/README.md) | Fix JPA throwable message conversion | merged | 2.26.1 | 2.x HEAD | fixed | 2.26.1: multi-line messages throw StringIndexOutOfBoundsException, and a fake "Caused by" line initializes the named class. 2.x HEAD round-trips LF/CRLF; legacy rows are read without throwing. |
| [#4374](pr-4374/README.md) | Honor Cassandra TLS hostname verification | merged | 2.26.1 | 2.x HEAD | fixed | With sslVerifyHostName=true, 2.26.1 accepted a wrong-host cert; 2.x HEAD refuses it (certificate_unknown). Caveat: driver reverse-resolves an IP host, so an IP-only SAN is refused. |
| [#4387](pr-4387/README.md) | Port #4316 to `2.25.x` | merged | 2.26.1 | 2.x HEAD | fixed | 2.25.x port of #4316: 2.25.5/2.26.1 write <script> raw in <log:dump>; 2.x build escapes it (DumpTag identical on both branches; 2.25.x not built). |
| [#4392](pr-4392/README.md) | Fix idle thread issue in RollingFileManager to allow JVM exit | merged | 2.26.1 | 2.x HEAD | fixed | The bug was never released (2.26.1 exits). Pre-fix 2.x build (PR 4175 jars) leaves non-daemon RollingFileManager-1 thread, JVM still alive after 15s; 2.x HEAD exits in 5s. |
| [#4400](pr-4400/README.md) | Fix the formatting of instants before the epoch (#4399) | merged | 2.26.1 | 2.x HEAD | fixed | -1500ms printed "23:59:0/.+00" on 2.26.1, "23:59:58.500" on HEAD. Residual: epochMilli -1 formats empty on both (thread-local cache sentinel -1). |
| [#4401](pr-4401/README.md) | Preserve JPA throwable messages with no-arg constructors | merged | 2.26.1 | 2.x HEAD | fixed | Throwable with only a no-arg constructor came back from the JPA round-trip with message=null on 2.26.1; 2.x HEAD keeps "Message from default constructor.". Controls pass on both. |
| [#4404](pr-4404/README.md) | Fix LocationAwareLogger bypassing configured MessageFactory | merged | 2.26.1 | 2.x HEAD | fixed | SLF4J2 LocationAwareLogger.log("location-aware %s") with StringFormatterMessageFactory: 2.26.1 "location-aware %s", 2.x HEAD "location-aware formatted"; info() control identical. |
| [#4410](pr-4410/README.md) | Support Jackson 3 | merged | 2.26.1 | 2.x HEAD | fixed | Jackson3-only: 2.26.1 parses log4j2.yaml as XML, 0 events; HEAD loads it. Both Jacksons on HEAD: "additivity: yes" becomes false (2→1 copies), note concern confirmed. |
| [#4414](pr-4414/README.md) | Respect logger-name hierarchy boundary in LoggerNameLevelRewritePolicy | merged | 2.26.1 | 2.x HEAD | fixed | Policy logger=com.foo INFO→WARN: com.foobar.Service and com.food rewritten WARN on 2.26.1, stay INFO on HEAD; com.foo controls WARN on both. |
| [#4419](pr-4419/README.md) | Respect the logger-name hierarchy boundary in Configurator.setAllLevels and putIfAbsent results in AbstractConfiguration | merged | 2.26.1 | 2.x HEAD | fixed | setAllLevels(com.foo,TRACE): com.foobar WARN→TRACE, com.foobar.child ERROR→TRACE on 2.26.1, unchanged on HEAD; addLoggerAppender mismatched key: lost vs delivered. |
| [#4420](pr-4420/README.md) | Fix merging of structured data elements with the same SD-ID in `Rfc5424Layout` | merged | 2.26.1 | 2.x HEAD | fixed | Rfc5424Layout: SDM id=mdc+ThreadContext NPE, two Transfer@18060 SDMs UnsupportedOperationException, 1/3 lines on 2.26.1; 3/3 lines, merged fields on HEAD. |
| [#4433](pr-4433/README.md) | Fix/missing constructor | merged | 3.0.0-beta3 | main HEAD | fixed | LoggerConfig(String,Level,boolean): NoSuchMethodException, DEBUG not written on 3.0.0-beta3; present and DEBUG written on main HEAD 2bb673f650. |
| [#4456](pr-4456/README.md) | Count RFC 5424 TLS frame bytes using the configured charset | merged | 2.26.1 | 2.x HEAD | fixed | ISO-8859-1 TLS frame: 2.26.1 MSG-LEN=87 vs 84 payload bytes; 2.x HEAD 84/84; UTF-8 control 87/87 both |
| [#4458](pr-4458/README.md) | Return null from JavaLookup for an unknown key | merged | 2.26.1 | 2.x HEAD | fixed | ${java:build} with <Properties> build: 2.26.1 unresolved plus 3 "Resolver failed" ERRORs; HEAD fromProperties, 0 errors; :-default works on both |
| [#4461](pr-4461/README.md) | Fix quadratic complexity in HTML pattern escaping | merged | 2.26.1 | 2.x HEAD | fixed | %enc{%m}{HTML} 16→64 KiB of '"': 2.26.1 35→886 ms (24.9x); HEAD 0→2 ms (3.9x); output identical to reference both |
| [#4465](pr-4465/README.md) | Limit JUL mutator warnings and identify callers | merged | 2.26.1 | 2.x HEAD | fixed | 8 JUL mutator calls: 2.26.1 8 warnings, no caller, ignore still 8; HEAD 1 warning naming Repro.main, warn=8, ignore=0 |
| [#4469](pr-4469/README.md) | Write the layout header in `RandomAccessFile` and `MemoryMappedFile` appenders | merged | 2.26.1 | 2.x HEAD | fixed | header="HEADER": 2.26.1 RAF and MMF files lack HEADER (DATA, FOOTER only); HEAD all three have HEADER; File control passes both |

## Nothing to reproduce

| PR | Title | State | Baseline | Fixed build | Result | What the run showed |
|---|---|---|---|---|---|---|
| #3916 | Synchronization with main backlog | merged | 3.0.0-beta3 | main HEAD | n/a | Javadoc-only change: ConfigurationStrSubstitutor class comment wording |
| #4111 | Revamped `ListAppender` for thread-safety | merged | 2.26.0 | 2.x HEAD | n/a | Test-only: ListAppender lives in log4j-core-test, so no runtime change for an application. |
| #4240 | Port `MessageFactory`-namespaced logger registry from `2.x` | merged | 3.0.0-beta3 | main HEAD | n/a | Dead-code removal: registry keyed by (name, MessageFactory) since #4157, so the deleted checkMessageFactory warning was unreachable |
| #4260 | Merge `log4j-*-java9` modules into `log4j-api` and `log4j-core` | merged | 2.26.1 | 2.x HEAD | n/a | Build refactor merging java9 modules; versions/9 entries unchanged (only DefaultObjectInputFilter gone, removed by #4365), no app-visible behavior. |
| #4265 | Document location restrictions on Log4j 1 external entities | merged | 2.26.1 | 2.x HEAD | n/a | Javadoc and migration-guide documentation only; no runtime change. |
| #4270 | Apply the deserialization filter in test helpers on Java 9+ | merged | 2.26.1 | 2.x HEAD | n/a | Test helpers, test classes and test POM only; no production code changed. |
| #4307 |   Use `TIMESTAMP` instead of the removed `DATETIME` alias in the H2 tests                                                                                                        | merged | 3.0.0-beta3 | main HEAD | n/a | Test-only: H2 test DDL and JMH benchmark change DATETIME to TIMESTAMP; no runtime code touched. |
| #4315 | Fix `ThreadContextTestAccess` out of the `org.apache.logging.log4j.core` package | merged | 3.0.0-beta3 | main HEAD | n/a | Test-only: moves ThreadContextTestAccess test helper into org.apache.logging.log4j.core.test package; no shipped behavior change. |
| #4424 | Add fuzz targets for syslog, GELF and structured-data layouts | merged | 2.26.1 | 2.x HEAD | n/a | Adds fuzz targets only (four *Fuzzer classes); no runtime behaviour change for applications. |

## Earlier repros

Written by hand before the sweep: `issue-4143`, `issue-4243`, `issue-4262`, `issue-4275`, `issue-4279`, `issue-4356`, `issue-4475`, `issue-4480`, `pr-4226`, `pr-4229`, `pr-4234`, `pr-4235`, `pr-4246`, `pr-4440`, `pr-4474`.
