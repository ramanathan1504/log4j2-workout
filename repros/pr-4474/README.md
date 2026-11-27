# PR #4474: reject a `KeyValuePair` with a null or empty key (#3472)

Written by hand: `oss run … pr 4474 --checkout` checks out in the main log4j2 checkout, which was in use.
The release run resolves jars from Maven Central. The head run uses the jars built in a separate clone
(`run.sh head <clone> <label>`), never `~/.m2` snapshots.

```bash
log4j-pr-4474-repro/run.sh 2.26.1
log4j-pr-4474-repro/run.sh head <clone of PR head> pr-head-09d2814e5b
```

| Scenario | 2.26.1 | PR head `09d2814e5b` | head without `@Required` |
|---|---|---|---|
| S1 XML `GelfLayout` with `<KeyValuePair value="orphan"/>` and a good pair | NPE in `JsonUtils.quoteAsString` on every event; event lost | one startup error `A key must be provided for KeyValuePair`; event written with `_good:"kept"` | event written; status log has IAE stack and `No factory method found` |
| S2 `new KeyValuePair(null / "", "v")` | created `null=v` / `=v` | `IllegalArgumentException` | `IllegalArgumentException` |
| S3 `newKeyValuePair(null, …)` then `toXmlConfiguration()` | NPE in `XMLStreamWriterImpl.writeAttribute` | `IllegalArgumentException` at `newKeyValuePair` | same |

Mechanism: the `KeyValuePair` constructor now runs `Assert.requireNonEmpty` on the key. `@Required` on the
builder field makes `PluginBuilder` reject the element before `build()`. The parent then gets no pair
instead of a pair whose `getKey()` is `null`.

Outputs: `output/2.26.1/`, `output/pr-head-09d2814e5b/`, `output/pr-suggestion-no-required/`.
