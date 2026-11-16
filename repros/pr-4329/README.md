# PR #4329 — Preserve custom Configuration elements under CompositeConfiguration

https://github.com/apache/logging-log4j2/pull/4329

`CompositeConfiguration` merges its children at the node level and runs only its own `doConfigure()`, so anything a custom `Configuration` adds programmatically in its `doConfigure()` override is lost. The PR adds a public no-op hook `AbstractConfiguration.postConfigure(Configuration target)` (`@since 2.27.0`) that `CompositeConfiguration.doConfigure()` calls on each child. The repro has `ProgrammaticXmlConfiguration extends XmlConfiguration`, which adds a `programmatic` logger at TRACE in `doConfigure()` and again in a `postConfigure(Configuration)` method (compiled against 2.26.1, where it overrides nothing). It builds `new CompositeConfiguration([base.xml, overlay.xml])`, initializes it, then calls `Reconfigurable.reconfigure()` and `LoggerContext.setConfiguration(...)`, as the file watcher does. `+factory` registers a `ConfigurationFactory` that returns the custom class, which is how a composite reload finds it again; `+no-factory` does not.

## Commands

```bash
cd log4j-pr-4329-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `bc90b767d1`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: single custom config, initial and reconfigured | `programmatic` logger present | `loggers=[, programmatic]`, trace enabled | PASS |
| 2.26.1 | composite+factory initial | `programmatic` present | `loggers=[, from.xml]`, trace disabled | FAIL |
| 2.26.1 | composite+factory after reconfigure | `programmatic` present | `loggers=[, from.xml]` | FAIL |
| 2.26.1 | composite+no-factory initial | `programmatic` present | `loggers=[, from.xml]` | FAIL |
| 2.26.1 | composite+no-factory after reconfigure | `programmatic` present | `loggers=[, from.xml]` | FAIL |
| 2.x HEAD ea28f5a78b | control: single custom config, initial and reconfigured | `programmatic` present | `loggers=[, programmatic]` | PASS |
| 2.x HEAD ea28f5a78b | composite+factory initial | `programmatic` present | `loggers=[, programmatic, from.xml]`, trace enabled | PASS |
| 2.x HEAD ea28f5a78b | composite+factory after reconfigure | `programmatic` present | `loggers=[, programmatic, from.xml]` | PASS |
| 2.x HEAD ea28f5a78b | composite+no-factory initial | `programmatic` present | `loggers=[, programmatic, from.xml]` | PASS |
| 2.x HEAD ea28f5a78b | composite+no-factory after reconfigure | `programmatic` present | `loggers=[, from.xml]` | FAIL |

Notes: the fixed build still loses the element on reconfigure when the custom class is not produced by the active `ConfigurationFactory`. `CompositeConfiguration.reconfigure()` reloads each child with `ConfigurationFactory.getInstance().getConfiguration(ctx, name, uri)`, not `child.reconfigure()`, so the children come back as plain `XmlConfiguration`. The review note says the hook runs on every reconfiguration; that holds only when the factory returns the custom class.
