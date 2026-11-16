# PR #4359 — NPE when an SSL store location is set without a store type

https://github.com/apache/logging-log4j2/pull/4359

`SslConfigurationFactory.createSslConfiguration` reads `log4j2.trustStoreLocation` / `log4j2.keyStoreLocation`
and passes the optional `*KeyStoreType` to `getPassword(password, keyStoreType)`, which called
`keyStoreType.equals(...)` outside the `try`. With a location and no type, that throws `NullPointerException`
out of `getSslConfiguration()`, so HTTPS configuration loading fails. The fix defaults the type to
`SslConfigurationDefaults.KEYSTORE_TYPE` and compares case-insensitively, so the JVM default `pkcs12` on Java 9+
takes the JKS/PKCS12 branch (the case-sensitivity concern in the review note).

## Commands

```bash
cd log4j-pr-4359-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`run.sh` builds a one-certificate PKCS12 trust store with `keytool`, sets only `log4j2.trustStoreLocation` and
`log4j2.trustStorePassword`, and calls `SslConfigurationFactory.getSslConfiguration()`. JDK 17.0.14.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | trust store location, no type | store loaded with default type | `NullPointerException: ... "keyStoreType" is null` | FAIL |
| 2.x HEAD ea28f5a78b | trust store location, no type | store loaded with default type | type=`pkcs12`, 1 entry loaded | PASS |
