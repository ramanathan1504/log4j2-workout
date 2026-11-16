# PR #4363 — every `JndiManager` shared the first `InitialContext`

https://github.com/apache/logging-log4j2/pull/4363

On 2.26.1 `JndiManager.getJndiManager(Properties)` registers the manager in the `AbstractManager` registry under
`createManagerName()`, a constant (`JndiManager@<class hash>`). The second call with a different environment gets
the first manager back, so a second JMS appender (or any caller passing its own `PROVIDER_URL`) resolves names
against the first provider. The fix drops the registry for this class: each factory call builds its own
`JndiManager` with a fresh `InitialContext`, and `stop` closes only that context.

## Commands

```bash
cd log4j-pr-4363-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`Repro.java` registers an `InitialContextFactory` that counts calls and returns a `Context` whose `lookup` answers
`ConnectionFactory from <PROVIDER_URL>`. It calls `JndiManager.getJndiManager(createProperties(...))` for
`tcp://broker-a:61616` and `tcp://broker-b:61616`, with `-Dlog4j2.enableJndiJms=true`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | two environments, two managers | distinct | same instance | FAIL |
| 2.26.1 | control: manager A lookup | from broker-a | from broker-a | PASS |
| 2.26.1 | manager B lookup | from broker-b | from **broker-a** | FAIL |
| 2.26.1 | `InitialContextFactory` calls | 2 | 1 | FAIL |
| 2.x HEAD ea28f5a78b | two environments, two managers | distinct | distinct | PASS |
| 2.x HEAD ea28f5a78b | control: manager A lookup | from broker-a | from broker-a | PASS |
| 2.x HEAD ea28f5a78b | manager B lookup | from broker-b | from broker-b | PASS |
| 2.x HEAD ea28f5a78b | `InitialContextFactory` calls | 2 | 2 | PASS |

Notes: drives the public `JndiManager` API directly rather than two `JMS` appenders, to avoid a broker; the
appender path goes through the same `getJndiManager(Properties)`.
