# PR #4428 — `StatusLogger` crash when property access is denied

https://github.com/apache/logging-log4j2/pull/4428 (issue #4429)

`StatusLogger.Config` reads its settings through `PropertiesUtilsDouble.readAllAvailableProperties()`, which on 2.26.1 calls
`System.getProperties()` and `System.getenv()` without a guard. Under a security manager whose policy grants only named properties,
both throw, `Config` class init fails with `ExceptionInInitializerError`, and `StatusLogger.getLogger()` (and with it the whole API)
is unusable. At head `0230666664` the PR catches the `SecurityException` and falls back to reading each known StatusLogger property
and environment variable by name (`readEach`), so a policy that grants `log4j2.*` still lets `log4j2.StatusLogger.level` through.
That is the per-key fallback the 2026-10-25 review asked for.

The repro has only `log4j-api` on the classpath (SimpleLogger provider) and runs `Repro` twice with `-Dlog4j2.StatusLogger.level=INFO`:
once without a security manager (control), once with `-Djava.security.manager=default` and
`src/main/policy/named-properties-only.policy`, which grants `PropertyPermission "log4j2.*"/"log4j.*"/"java.version" read`, one named
`getenv.` permission and the class-loading permissions the API needs, but neither `PropertyPermission "*"` nor `getenv.*`.
JDK frames keep their own privileges, as in a real deployment.

## Commands

```bash
log4j-pr-4428-repro/run.sh 2.26.1 2.26.1
log4j-pr-4428-repro/run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-pr-4428 pr-head-0230666664
```

JDK 17.0.14 (JBR); the security manager is still available there.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | no security manager: `StatusLogger.getLogger()` (control) | no throw | ok | PASS |
| 2.26.1 | no security manager: fallback listener level (control) | `INFO` | `INFO` | PASS |
| 2.26.1 | policy: `StatusLogger.getLogger()` | no throw | `ExceptionInInitializerError <- AccessControlException: access denied ("java.util.PropertyPermission" "*" "read,write")` | FAIL |
| PR head `0230666664` | no security manager (control) | no throw, `INFO` | ok, `INFO` | PASS |
| PR head `0230666664` | policy: `StatusLogger.getLogger()` | no throw | ok | PASS |
| PR head `0230666664` | policy: `log4j2.StatusLogger.level=INFO` read per key | `INFO` | `INFO` | PASS |
| PR head `0230666664` | policy: `LogManager.getLogger(...).info(...)` | no throw | ok | PASS |

Raw: `output/<label>/result.txt`, `output/<label>/stderr.txt`.

Notes: under the policy the PR head prints 25 `ERROR The environment variable ... is not available to Log4j due to security
restrictions` entries with stack traces from `PropertiesUtil`/`EnvironmentPropertySource`. Those are the pre-existing guarded paths,
not this PR, but a user under such a policy sees that noise on every start. A first attempt with a hand-written `SecurityManager` that
ignored `doPrivileged` also failed `ParameterFormatter` init in JDK `ZoneId.systemDefault()`; that was an artefact of the stub and is
not in the final run.
