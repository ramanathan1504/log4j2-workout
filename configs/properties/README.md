# Properties configurations

Mirrors of `configs/xml/`, one file per config, same name. Feature matrix §11 —
`PropertiesConfigurationFactory`.

This is the **Log4j 2** properties format, not the Log4j 1.x one. They look
similar and are not compatible; see `configs/log4j1/log4j.properties` for 1.x.

## Two things this format cannot express

Everywhere else in `configs/` the same config name exists in all four formats.
These are the exceptions, both established from
`PropertiesConfigurationBuilder` rather than by guessing.

### Arbiters — no file here at all

`configs/properties/arbiters.properties` does not exist, and cannot.

The builder recognises exactly seven top-level prefixes — `property`, `script`,
`customLevel`, `filter`, `appender`, `logger`, `rootLogger` — and has no
arbiter handling of any kind. Filing an arbiter under `appender.` is the only
plausible spelling and it fails hard, because `createAppender` requires a name
that arbiters do not have:

```
ConfigurationException: No name attribute provided for Appender select
    at PropertiesConfigurationBuilder.createAppender
```

That exception propagates out of configuration into `LogManager.getLogger`, so
the application dies with `ExceptionInInitializerError` in whichever class holds
the first static logger. Unlike most configuration mistakes, this one is not
survivable and not silent.

Arbiters are available in XML, JSON and YAML — see `configs/xml/arbiters.xml`.

### Composite filters — one filter per component

`PropertiesConfigurationBuilder.createFilter` always calls
`builder.newFilter(type, onMatch, onMismatch)`, so a `Filters` composite is
always built carrying `onMatch`/`onMismatch` attributes, which `CompositeFilter`
does not declare:

```
ERROR Filters contains invalid attributes "onMatch", "onMismatch"
```

The configuration still loads, but the bench treats status-logger errors as
failures, so a permanent spurious error would mask real ones. Appender, logger
and appender-ref scopes therefore take exactly one filter each here;
`filter-all.properties` spreads the sixteen filters across one appender apiece
and explains the layout in its header. Only the context-wide scope accepts
several, because the builder combines those itself without going through the
`CompositeFilter` plugin.

## And one that is not this format's fault

The whole directory is 2.x-only. Log4j 3 removed
`PropertiesConfigurationFactory` outright; `log4j-config-properties` ships
`JavaPropsConfigurationFactory` instead, a Jackson java-properties reader whose
keys mirror the JSON/YAML tree rather than this flat dotted syntax. Running any
file here on 3.x falls back to the default configuration without a word, which
is why `./bench matrix` skips those cells with a stated reason.
