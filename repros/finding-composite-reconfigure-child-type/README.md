# Finding: CompositeConfiguration — reconfigure turns custom child configurations into plain XmlConfiguration

Status: not yet filed upstream

Prior work: `repros/pr-4329` found this after #4329. Open PR #4482 changes the same method for a different case; it was not read or reviewed here.

## Mechanism

- `CompositeConfiguration.reconfigure()` (`log4j-core/src/main/java/org/apache/logging/log4j/core/config/composite/CompositeConfiguration.java:153` on `origin/2.x` `ea28f5a78b`) takes `ConfigurationFactory.getInstance()` at line 156 and rebuilds every child at line 166 with `factory.getConfiguration(getLoggerContext(), config.getName(), sourceURI)`. It never calls the child's own `reconfigure()`.
- A child the application constructed itself (`new CustomXmlConfiguration(ctx, source)`), or one produced by a factory that is not the active `ConfigurationFactory`, comes back as whatever the default factory builds for `.xml`: a plain `XmlConfiguration`.
- Everything the custom class adds is then gone: nodes added in `setup()`, and on 2.x HEAD the loggers added in `postConfigure(Configuration)`, which `doConfigure()` calls on each child at line 148 since `bc90b767d1` (#4329).
- The path is reached from `LoggerContext.onChange(Reconfigurable)` (`LoggerContext.java:854`, calls `reconfigurable.reconfigure()` at 858), which is what the `monitorInterval` file watcher calls. 3.x uses `LoggerContext.accept(Reconfigurable)`.
- `LoggerContext.reconfigure()` (`LoggerContext.java:815`) does not reach this method: it re-resolves `configLocation` through the factory (line 786). A composite set programmatically has `COMPOSITE_SOURCE` (`ConfigurationSource.java:62`, no URI), so it is replaced by `DefaultConfiguration` instead. Recorded as a case, but it is a different path.

## The repro

`log4j-finding-composite-reconfigure-child-type-repro/`:

- `NoReconfigureXmlConfiguration extends XmlConfiguration`: `setup()` adds a `Logger` node `from.setup` at TRACE to the parsed tree; `doConfigure()` and `postConfigure(Configuration)` add a programmatic `LoggerConfig` `programmatic` at TRACE.
- `CustomXmlConfiguration extends NoReconfigureXmlConfiguration` overrides `reconfigure()` to return its own class, which is what a well-behaved subclass does.
- `base.xml` (Console appender, `from.base`, Root) and `overlay.xml` (`from.xml` at DEBUG), both `monitorInterval="1"`, copied to a work directory so their URIs are `file:`.
- Oracle per checkpoint: `[type+setup]` passes when every child of the active configuration (read from the private `configurations` field) is the custom class and `from.setup` is TRACE-enabled; `[doConfigure]` passes when the `programmatic` logger is in the active configuration.

Modes:

| Mode | What |
|---|---|
| `single` | control: one `CustomXmlConfiguration`, reconfigured through `onChange` |
| `single-no-override` | sibling: an `XmlConfiguration` subclass without a `reconfigure()` override |
| `composite` | `new CompositeConfiguration([custom base, custom overlay])`, reconfigured through `onChange` (2.x) / `accept` (3.x) |
| `composite-factory` | control: same, with `ConfigurationFactory.setConfigurationFactory(new CustomConfigurationFactory())` (2.x only) |
| `composite-monitor` | same as `composite`, then `overlay.xml` is rewritten to `level="warn"` and the run waits for the watcher (up to 15 s) |
| `composite-ctx` | same as `composite`, then `LoggerContext.reconfigure()` |

`src/` is the 2.x source; `src3/` is the 3.x source (Node from `log4j-plugins`, context passed to the constructors, no `postConfigure`). `run.sh` picks `src3/` for a 3.x version or a jars directory holding `log4j-core-3.*`, with `log4j-api` 2.24.3.

## Commands

```bash
cd log4j-finding-composite-reconfigure-child-type-repro
./run.sh 2.7 2.7
./run.sh 2.17.2 2.17.2
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
./run.sh 3.0.0-beta3 3.0.0-beta3
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-main 3.x-main
```

`jars-2x` is built from `origin/2.x` `ea28f5a78b`; `jars-main` from `origin/main` `2bb673f650`. The jars used are listed in `output/<label>/classpath.txt`; full lines are in `output/<label>/result.txt`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.7 | control: single custom config | children custom, from.setup.trace=true | children=[CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.7 | control: composite, custom factory registered | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.7 | composite, programmatic children, initial | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.7 | composite, after reconfigure (onChange/accept) | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false | FAIL |
| 2.7 | composite, doConfigure logger, initial | programmatic present | programmatic present=false | FAIL |
| 2.7 | composite, doConfigure logger, after reconfigure | programmatic present | programmatic present=false | FAIL |
| 2.7 | composite, monitorInterval=1, overlay edited | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false, from.xml.level=WARN | FAIL |
| 2.7 | sibling: XmlConfiguration subclass without reconfigure() | children custom, from.setup.trace=true | children=[XmlConfiguration], from.setup.trace=false | FAIL |
| 2.7 | LoggerContext.reconfigure() on composite | children custom, from.setup.trace=true | children=[DefaultConfiguration], from.setup.trace=false, config=DefaultConfiguration | FAIL |
| 2.17.2 | control: single custom config | children custom, from.setup.trace=true | children=[CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.17.2 | control: composite, custom factory registered | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.17.2 | composite, programmatic children, initial | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.17.2 | composite, after reconfigure (onChange/accept) | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false | FAIL |
| 2.17.2 | composite, doConfigure logger, initial | programmatic present | programmatic present=false | FAIL |
| 2.17.2 | composite, doConfigure logger, after reconfigure | programmatic present | programmatic present=false | FAIL |
| 2.17.2 | composite, monitorInterval=1, overlay edited | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false, from.xml.level=WARN | FAIL |
| 2.17.2 | sibling: XmlConfiguration subclass without reconfigure() | children custom, from.setup.trace=true | children=[XmlConfiguration], from.setup.trace=false | FAIL |
| 2.17.2 | LoggerContext.reconfigure() on composite | children custom, from.setup.trace=true | children=[DefaultConfiguration], from.setup.trace=false, config=DefaultConfiguration | FAIL |
| 2.26.1 | control: single custom config | children custom, from.setup.trace=true | children=[CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.26.1 | control: composite, custom factory registered | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.26.1 | composite, programmatic children, initial | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.26.1 | composite, after reconfigure (onChange/accept) | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false | FAIL |
| 2.26.1 | composite, doConfigure logger, initial | programmatic present | programmatic present=false | FAIL |
| 2.26.1 | composite, doConfigure logger, after reconfigure | programmatic present | programmatic present=false | FAIL |
| 2.26.1 | composite, monitorInterval=1, overlay edited | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false, from.xml.level=WARN | FAIL |
| 2.26.1 | sibling: XmlConfiguration subclass without reconfigure() | children custom, from.setup.trace=true | children=[XmlConfiguration], from.setup.trace=false | FAIL |
| 2.26.1 | LoggerContext.reconfigure() on composite | children custom, from.setup.trace=true | children=[DefaultConfiguration], from.setup.trace=false, config=DefaultConfiguration | FAIL |
| 2.x HEAD ea28f5a78b | control: single custom config | children custom, from.setup.trace=true | children=[CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.x HEAD ea28f5a78b | control: composite, custom factory registered | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.x HEAD ea28f5a78b | composite, programmatic children, initial | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 2.x HEAD ea28f5a78b | composite, after reconfigure (onChange/accept) | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false | FAIL |
| 2.x HEAD ea28f5a78b | composite, doConfigure logger, initial | programmatic present | programmatic present=true | PASS |
| 2.x HEAD ea28f5a78b | composite, doConfigure logger, after reconfigure | programmatic present | programmatic present=false | FAIL |
| 2.x HEAD ea28f5a78b | composite, monitorInterval=1, overlay edited | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false, from.xml.level=WARN | FAIL |
| 2.x HEAD ea28f5a78b | sibling: XmlConfiguration subclass without reconfigure() | children custom, from.setup.trace=true | children=[XmlConfiguration], from.setup.trace=false | FAIL |
| 2.x HEAD ea28f5a78b | LoggerContext.reconfigure() on composite | children custom, from.setup.trace=true | children=[DefaultConfiguration], from.setup.trace=false, config=DefaultConfiguration | FAIL |
| 3.0.0-beta3 | control: single custom config | children custom, from.setup.trace=true | children=[CustomXmlConfiguration], from.setup.trace=true | PASS |
| 3.0.0-beta3 | composite, programmatic children, initial | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 3.0.0-beta3 | composite, after reconfigure (onChange/accept) | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false | FAIL |
| 3.0.0-beta3 | composite, doConfigure logger, initial | programmatic present | programmatic present=false | FAIL |
| 3.0.0-beta3 | composite, doConfigure logger, after reconfigure | programmatic present | programmatic present=false | FAIL |
| 3.0.0-beta3 | composite, monitorInterval=1, overlay edited | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false, from.xml.level=WARN | FAIL |
| 3.0.0-beta3 | sibling: XmlConfiguration subclass without reconfigure() | children custom, from.setup.trace=true | children=[XmlConfiguration], from.setup.trace=false | FAIL |
| 3.0.0-beta3 | LoggerContext.reconfigure() on composite | children custom, from.setup.trace=true | children=[DefaultConfiguration], from.setup.trace=false, config=DefaultConfiguration | FAIL |
| 3.x main 2bb673f650 | control: single custom config | children custom, from.setup.trace=true | children=[CustomXmlConfiguration], from.setup.trace=true | PASS |
| 3.x main 2bb673f650 | composite, programmatic children, initial | children custom, from.setup.trace=true | children=[CustomXmlConfiguration, CustomXmlConfiguration], from.setup.trace=true | PASS |
| 3.x main 2bb673f650 | composite, after reconfigure (onChange/accept) | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false | FAIL |
| 3.x main 2bb673f650 | composite, doConfigure logger, initial | programmatic present | programmatic present=false | FAIL |
| 3.x main 2bb673f650 | composite, doConfigure logger, after reconfigure | programmatic present | programmatic present=false | FAIL |
| 3.x main 2bb673f650 | composite, monitorInterval=1, overlay edited | children custom, from.setup.trace=true | children=[XmlConfiguration, XmlConfiguration], from.setup.trace=false, from.xml.level=WARN | FAIL |
| 3.x main 2bb673f650 | sibling: XmlConfiguration subclass without reconfigure() | children custom, from.setup.trace=true | children=[XmlConfiguration], from.setup.trace=false | FAIL |
| 3.x main 2bb673f650 | LoggerContext.reconfigure() on composite | children custom, from.setup.trace=true | children=[DefaultConfiguration], from.setup.trace=false, config=DefaultConfiguration | FAIL |

Readings:

- The controls pass on every version: a single custom configuration keeps its class and its elements across reconfigure, and so does a composite when the custom factory is the active one.
- A composite of programmatically built custom children loses the class and the `setup()` logger after every reconfigure on every version run, 2.7 to 3.x main, both by direct `onChange`/`accept` and by the `monitorInterval` watcher (the reload is confirmed: `from.xml` went from DEBUG to WARN).
- On 2.x HEAD the `doConfigure`/`postConfigure` logger now survives the initial composite (#4329), and is lost on the first reconfigure. On every release and on 3.x it is absent even initially, which is the defect #4329 fixed, not this one.
- `composite-ctx`: `LoggerContext.reconfigure()` replaces the whole programmatic composite with `DefaultConfiguration` on every version. That is the `configLocation` path, not `CompositeConfiguration.reconfigure()`.

## Since when

`git log -S'factory.getConfiguration(' origin/2.x -- .../CompositeConfiguration.java` gives one commit: `bb64f45773` "LOG4J2-494 working CompositeConfiguration" (Ralph Goers, 2016-04-23), first released in 2.6. The method has called the factory rather than the child since it was written. 2.6 does not compile the repro (no `XmlConfiguration(LoggerContext, ConfigurationSource)` constructor, `ConfigurationFactory.getConfiguration(ConfigurationSource)` abstract), so 2.7 is the oldest release run; it fails the same way. Every release from 2.6 is affected.

## 3.x

`origin/main` `CompositeConfiguration.java:129` has the same method: it gets a `URIConfigurationFactory` from the instance factory at line 132 and rebuilds each child at line 142 with `factory.getConfiguration(getLoggerContext(), config.getName(), sourceURI)`. Run on 3.0.0-beta3 and main `2bb673f650`: affected, same result.

## Defect-class sweep (`origin/2.x`)

| Site | Pattern | Verdict |
|---|---|---|
| `core/config/composite/CompositeConfiguration.java:153` | rebuilds children through the active factory, never `child.reconfigure()` | affected (this finding) |
| `core/config/xml/XmlConfiguration.java:255` | `reconfigure()` returns `new XmlConfiguration(...)` at 261 | same shape, but a subclass can override `reconfigure()`; run as `single-no-override` (subclass without override comes back as `XmlConfiguration`) and `single` (override works). Not affected for a subclass that overrides; the composite defeats the override |
| `core/config/json/JsonConfiguration.java:129` | `new JsonConfiguration(...)` at 135 | same shape as XML, overridable; untested |
| `core/config/yaml/YamlConfiguration.java:40` | `new YamlConfiguration(...)` at 46 | same shape, overridable; untested |
| `core/config/yaml/Jackson3YamlConfiguration.java:130` | `new Jackson3YamlConfiguration(...)` at 136 | same shape, overridable; untested |
| `core/config/properties/PropertiesConfiguration.java:40` | `new PropertiesConfigurationFactory().getConfiguration(...)` at 46-47 | same shape, overridable; untested |
| `core/config/builder/impl/BuiltConfiguration.java` | no `reconfigure()`; watchers only if a subclass implements `Reconfigurable` (line 201) | not affected |
| `log4j-1.2-api/.../config/PropertiesConfiguration.java:135`, `.../xml/XmlConfiguration.java:208` | rebuild through their own factory | same shape, overridable; untested |
| `log4j-1.2-api/.../config/Log4j1Configuration.java:74` | returns `null` | not affected |
| `core/LoggerContext.java:815` `reconfigure()` | re-resolves `configLocation` through the factory | different path; a programmatic composite is replaced by `DefaultConfiguration` (`composite-ctx`) |

Only the composite turns the subclass's own `reconfigure()` into dead code; the format siblings call nothing the subclass cannot override.

## Class

`log4j-bug`. Reproduced on `origin/2.x` and `origin/main`. It contradicts the code's own contract on 2.x HEAD: #4329 made `CompositeConfiguration` preserve a child's programmatic elements, and the first reconfigure, including the one `monitorInterval` triggers, drops them along with the child's class. The fix lives in `log4j-core`, `CompositeConfiguration.reconfigure()`: reload each child through its own `Reconfigurable.reconfigure()` when it implements it, and fall back to the factory otherwise.
