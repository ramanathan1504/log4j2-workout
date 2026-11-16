# PR #4364 — JMS appender logs the JNDI `securityCredentials` in clear text

https://github.com/apache/logging-log4j2/pull/4364

`JmsAppender.Builder.build()` puts `securityCredentials` into the JNDI `Properties` held by
`JmsManagerConfiguration`. When the manager cannot be created (naming server or broker down at startup),
`JmsManagerFactory` logs `Error creating JmsManager using JmsManagerConfiguration [...]` at `ERROR`, and
`AbstractManager.getManager` repeats the configuration in its `IllegalStateException`; both print
`JmsManagerConfiguration.toString()`, which printed `java.naming.security.credentials=<secret>`. The fix masks that key
in a copy of the table in both `toString()` methods, in `log4j-core` (`JMS-Javax`) and `log4j-jakarta-jms`
(`JMS-Jakarta`), and prints `securityCredentials=*****` in `Builder.toString()`.

## Commands

```bash
cd log4j-pr-4364-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`log4j2.xml` configures one `JMS-Javax` and one `JMS-Jakarta` appender with `securityCredentials="JndiSecretJavax"` /
`"JndiSecretJakarta"` and a `factoryName` whose `getInitialContext` throws `NamingException`. `run.sh` starts the
application with `-Dlog4j2.enableJndiJms=true` at status level `WARN` and `DEBUG`, and counts occurrences of each
secret in the status output.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | `JndiSecretJavax` in status output, WARN / DEBUG | 0 | 4 / 4 | FAIL |
| 2.26.1 | `JndiSecretJakarta` in status output, WARN / DEBUG | 0 | 4 / 4 | FAIL |
| 2.26.1 | control: `Error creating JmsManager` ERROR lines | 2 | 2 | PASS |
| 2.x HEAD ea28f5a78b | `JndiSecretJavax` in status output, WARN / DEBUG | 0 | 0 / 0 (6 lines show `credentials=*****`) | PASS |
| 2.x HEAD ea28f5a78b | `JndiSecretJakarta` in status output, WARN / DEBUG | 0 | 0 / 0 (6 lines show `credentials=*****`) | PASS |
| 2.x HEAD ea28f5a78b | control: `Error creating JmsManager` ERROR lines | 2 | 2 | PASS |

Notes: of the 4 leaks per appender on 2.26.1, 3 go through `JmsManagerConfiguration.toString()` (fixed here). The
fourth is `AbstractManager.getManager`'s own `IllegalStateException` for `JndiManager$JndiManagerFactory ... with
data [{...credentials=JndiSecretJavax}]`, which prints the raw JNDI `Properties`. It is gone on 2.x HEAD only because
#4363 took `JndiManager` out of the manager registry; a port of #4364 to 2.26.x / 2.25.x without #4363 would still
leak that one line.
