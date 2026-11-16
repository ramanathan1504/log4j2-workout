# PR #4337 — fail TLS connections when an `Ssl` key store or trust store cannot be loaded

https://github.com/apache/logging-log4j2/pull/4337 (fixes #4332)

On 2.26.1 a `TrustStore` or `KeyStore` whose file cannot be loaded makes its `@PluginFactory` throw; the plugin builder logs
`Unable to invoke factory method` and passes `null`, so `SslConfiguration` builds an `SSLContext` with `null` managers, i.e.
the JVM default trust store and no client key. The connection proceeds with trust the user never configured. The PR moves the
`@PluginFactory` to `createOrRecordFailure`, which returns a store configuration carrying the load failure; `SslConfiguration`
then logs `Failed to load the store located at ...` and installs a `RejectingTrustManager`, so every handshake fails.
The same path is used for the `log4j2.trustStore*`/`log4j2.keyStore*` properties in `SslConfigurationFactory`.

The repro generates a self-signed `server.p12` (`keytool`, CN and SAN `127.0.0.1`), starts three in-JVM TLS sinks with it, and
sets the JVM default trust store (`-Djavax.net.ssl.trustStore`) to that same file, so falling back to the JVM default is visible as
a successful delivery. Three `Socket protocol="SSL"` appenders each send 3 events; the oracle counts the lines each sink decrypted.

- `missingTrustStore`: `TrustStore location=.../does-not-exist.p12`
- `missingKeyStore`: `KeyStore location=.../does-not-exist.p12` plus a valid `TrustStore`
- `validTrustStore` (control): valid `TrustStore` only

## Commands

```bash
log4j-pr-4337-repro/run.sh 2.26.1 2.26.1
log4j-pr-4337-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4337 pr-head-20397d866c
```

## Results

| Version | Case | Expected received | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `missingTrustStore` | 0 | 3 (handshake trusted via JVM default store) | FAIL |
| 2.26.1 | `missingKeyStore` | 0 | 3 | FAIL |
| 2.26.1 | `validTrustStore` (control) | 3 | 3 | PASS |
| PR head `20397d866c` | `missingTrustStore` | 0 | 0; client `The \`Ssl\` configuration is invalid, see the status logger for the cause` | PASS |
| PR head `20397d866c` | `missingKeyStore` | 0 | 0 | PASS |
| PR head `20397d866c` | `validTrustStore` (control) | 3 | 3 | PASS |

StatusLogger on 2.26.1: `ERROR Unable to invoke factory method in class ...TrustStoreConfiguration ... StoreConfigurationException`,
then nothing further. On the PR head: `ERROR Failed to load the store located at \`.../does-not-exist.p12\`, all TLS connections
using this configuration will be rejected`, then one `TcpSocketManager ... caught exception` / `Unable to write to stream` per event.

Raw: `output/<label>/result.txt`, `output/<label>/status.txt`.

Notes: the fix fails closed per connection, not at startup; the appender starts and reports an error per event. The question in the
review note (whether `Socket`, `Http` and `SMTP` should refuse to start) is not answered by this run. The `log4j2.trustStore*`
property path was not exercised.
