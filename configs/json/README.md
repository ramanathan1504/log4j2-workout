# JSON configurations

Mirrors of `configs/xml/`, one file per config, same name. Feature matrix §11 —
`JsonConfigurationFactory`. Requires jackson-databind.

**These files carry no comments, deliberately, and that is the interesting part.**

Log4j 2's `JsonConfiguration` builds its ObjectMapper with
`JsonParser.Feature.ALLOW_COMMENTS` enabled, so `//` and `/* */` are accepted
even though they are not legal JSON. Log4j 3's does not. Worse, it does not
report a parse error: the parse fails, `root` is left null, and the first logger
call dies with

```
java.lang.NullPointerException: Cannot invoke "java.util.Map.forEach(...)"
    because "this.root" is null
    at org.apache.logging.log4j.core.config.json.JsonConfiguration.setup
```

which surfaces as `ExceptionInInitializerError` in whatever class happened to
hold the first static logger. A commented JSON config therefore works on every
2.x line and hard-fails on 3.x, so the bench keeps these files strictly valid —
that way the same file is usable across the whole version axis, which is the
point of the bench.

The per-file notes that would otherwise be comments:

| File | Notes |
|---|---|
| `baseline-console.json` | The control config. Two things silently break a JSON config: an array at the top level (`JsonConfiguration` logs "Arrays are not supported at the root configuration" and loads nothing), and a missing jackson-databind (the factory is skipped and the default configuration takes over). Both look identical to "my config was ignored", which is why the bench banner prints the configuration Log4j actually loaded. |
| `filter-all.json` | XML distinguishes one filter from several by repeating elements; JSON cannot. A scope with more than one filter uses the `Filters` wrapper keyed by filter type, and several filters of the *same* type need a JSON array. Two identical keys instead is the classic failure: Jackson keeps the last and the earlier filter vanishes. `NullAppender` takes only a name, so `DenyAllFilter` is attached at appender-ref scope, where it is valid. |
| `layout-pattern-full.json` | The XML holds this pattern in CDATA, so its line breaks are literal. JSON strings cannot span lines, so every break is an explicit `\n`. This is the format where a stray unescaped backslash in a converter (the `%replace` regex) changes the pattern instead of failing to parse. |
| `layout-jsontemplate.json` | The custom template lives in `configs/templates/bench-custom.json` rather than inline — embedding it would mean escaping a JSON document inside a JSON string. The XML covers the inline `EventTemplate` element; this covers `eventTemplateUri` with a `file:` scheme. That URI must be absolute: a relative `file:configs/...` fails with "failed reading URI" whatever the working directory, so it is built from `${sys:user.dir}` (attribute values run through the StrSubstitutor). |
| `layout-legacy-json-xml-yaml.json` | A JSON configuration producing JSON output. The two are unrelated beyond both needing Jackson — and a missing jackson-databind breaks both at once, so you never reach the layout you meant to test. |
| `rollover-full.json` | The Delete action nests (`IfFileName` → `IfAny` → three conditions) and each level is a single key, so it reads as a chain of one-key objects rather than the tree the XML shows. `maxCompressionDelaySeconds` is not an attribute of `DefaultRolloverStrategy` — it exists in neither the 2.x nor the 3.x source. |
| `appender-jdbc.json` | `bufferSize: 0` writes each event immediately; above 0 batches, so a test asserting on table contents right after logging finds nothing. The schema is created by `DbBench` before Log4j initialises — the JDBC appender does not create tables. |