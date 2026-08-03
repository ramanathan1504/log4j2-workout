# AGENTS.md — Log4j2 Workout


## Purpose
Multi-module Maven workspace for hands-on Log4j 2 integration testing across versions, configs, and runtimes. No production code — every class is a test scenario.

**This repository is dedicated to validating local Log4j BOM (Bill of Materials) fixes in a real application context.**

All Log4j dependencies are resolved from your local Maven repository (`~/.m2/repository`), using a locally built BOM (e.g., `2.26.0-SNAPSHOT`). No remote downloads are performed during testing.

Special focus: **Issue #4012** — compression delay feature testing to reduce disk IO load at midnight.
## Local BOM Testing Instructions

To verify your local Log4j BOM and fixes (e.g., ListAppender thread-safety) in this sample project:

1. Ensure your local BOM and all Log4j artifacts are built and installed in `~/.m2/repository` (e.g., from `/Users/ramanathan/canonical/logging-log4j2`).
2. Do NOT use `-U` or any flags that force remote downloads.
3. Run Maven in offline mode to guarantee only local artifacts are used:

```bash
mvn clean verify -pl spring-boot-test -Dlog4j.version=2.26.0-SNAPSHOT -o
```

This will run all tests (including ListAppender and concurrency tests) using only your local Log4j BOM and dependencies.

If you want to inspect the effective POM and confirm the correct versions are resolved:

```bash
mvn help:effective-pom -pl spring-boot-test -Dlog4j.version=2.26.0-SNAPSHOT -o
```

If any test fails, review the logs and test output for details.

## Architecture

Two independent modules, both rooted in the **parent `pom.xml`** which controls all dependency versions:

| Module | Entry Point | What it tests |
|--------|-------------|---------------|
| `core-java-test` | `Main.java` → `LogTestScenarios.java` + `cron-test` mode | All 7 Log4j 2 features + RollingFile compression via plain Java |
| `spring-boot-test` | `SpringBootTestApp.java` + REST controller | Same features via Spring-managed service + HTTP triggers |

**Critical ordering rule in `pom.xml`:** Log4j entries appear *before* the Spring Boot BOM import in `<dependencyManagement>` — this ensures the pinned Log4j version wins over whatever Spring Boot pulls in.

## Version Matrix

All versions flow from parent `pom.xml` properties. Override at the CLI — never edit individual module POMs for version changes.

```bash
# Default: Log4j 2.25.3, Java 17
mvn clean package

# Profiles
-Pjava21                    # Java 21
-Plog4j-2x-maintenance      # Log4j 2.24.3
-Plog4j-legacy              # Log4j 2.12.4 + Java 8 + Disruptor 3.x
-Plog4j-snapshot -U         # Local 2.26.0-SNAPSHOT (must be installed in ~/.m2 first)
-Dlog4j.version=X.Y.Z       # Any version inline
```

## Key Build & Run Commands

All commands run from the **workspace root** (`log4j2-workout/`).

### Core Java Test (Default Scenarios)
```bash
# Build + run all 7 scenarios (sync config, default log4j2.xml)
mvn clean package -pl core-java-test -am
mvn exec:java -pl core-java-test

# Run with async config (LMAX Disruptor ring buffer)
mvn exec:java -pl core-java-test -Dlog4j.configurationFile=log4j2-async.xml
```

### Spring Boot Test (HTTP Endpoints)
```bash
# Build + run
mvn clean package -pl spring-boot-test -am
mvn spring-boot:run -pl spring-boot-test

# Smoke-test all scenarios at once
curl http://localhost:8081/api/logs/all

# Change log level at runtime — no restart needed
curl -X POST http://localhost:8081/actuator/loggers/com.springtest \
  -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
```

### Version Switching
```bash
# Local 3.0.0-SNAPSHOT with compression delay fix
mvn clean package -Plog4j-snapshot -U

# Any inline version
mvn clean package -Dlog4j.version=2.26.0
```

## Logging Configurations

### Standard Configs (both modules)

- **`log4j2.xml`** (default) — sync: `Console` (PatternLayout) + `JsonConsole` (JsonTemplateLayout) + two `RollingFile` appenders with **1KB trigger** + **3-sec compression delay**
- **`log4j2-async.xml`** — async: underlying sync appenders wrapped in `AsyncAppender`; app logger uses `AsyncLogger` (LMAX Disruptor, truly non-blocking)

### RollingFile Structure (Issue #4012)

Each RollingFile has:
```xml
<Policies>
  <TimeBasedTriggeringPolicy interval="1"/>      <!-- Rollover every minute -->
  <SizeBasedTriggeringPolicy size="1KB"/>        <!-- Also rollover at 1KB (for testing) -->
</Policies>
<DefaultRolloverStrategy max="20" maxCompressionDelaySeconds="3"/>
<!-- ↑ max: keep 20 files | maxCompressionDelaySeconds: DELAY compression 0-3 seconds -->
```

**Key attributes:**
- `maxCompressionDelaySeconds="3"` — adds 0-3 seconds random delay before compression starts. This reduces midnight IO spike when many machines roll files simultaneously.
- `size="1KB"` — intentionally small in test configs to force frequent rollovers and test compression.

**JsonTemplateLayout** is used throughout (not `log4j-jackson`). MDC keys (`traceId`, `userId`, `requestId`) surface automatically in the JSON `mdc` field. The `${docker:containerId:-unknown}` lookup requires `log4j-docker` on the classpath and returns `unknown` outside a Docker container — this is expected.

## Testing The Compression Delay Feature (Issue #4012)

The compression delay feature solves this problem: at 00:00 every day, thousands of machines try to compress log files simultaneously, causing disk IO spikes. The fix adds a configurable random delay (0-N seconds) before compression starts.

### Quick Test: Generate Rollover Events

```bash
# Generate 50 log lines in 10 seconds (triggers multiple 1KB rollovers)
cd log4j2-workout
mvn clean package -pl core-java-test -am

# Create a small test to generate logs
cat > /tmp/test_compression.sh << 'EOF'
#!/bin/bash
cd /Users/ramanathan/canonical/log4j2-workout
rm -rf core-java-test/logs
mkdir -p core-java-test/logs

# Run a quick scenario to generate logs
mvn exec:java -pl core-java-test 2>/dev/null

echo "[INFO] Checking rolled log files..."
ls -lh core-java-test/logs/

# Test with custom Log4j version (if 3.0.0-SNAPSHOT is available in ~/.m2)
# mvn clean package exec:java -pl core-java-test -Plog4j-snapshot -U
EOF
chmod +x /tmp/test_compression.sh
```

### What to Look For

After running scenarios, examine the logs directory:

```bash
ls -lh core-java-test/logs/

# Expected output (sample):
# app-2026-04-30-10-29-1.log.gz     ← should be .gz (compressed)
# app-2026-04-30-10-29-2.log        ← current roll file (not yet compressed)
# app.log                           ← active file
```

**The delay is working if:**
1. `.log.gz` files exist (compression happened)
2. At least one `.log` file (without .gz) exists (not yet compressed due to delay)
3. **Timestamp difference**: The .log file's mtime is ~0-3 seconds newer than the .log.gz files

### Verify with 3.0.0-SNAPSHOT

If you have Log4j 3.0.0-SNAPSHOT built locally with your fix:

```bash
# First, build your local Log4j with the fix
cd ~/path/to/logging-log4j2-branch
mvn clean install -DskipTests

# Back to workspace, test with the fix
cd log4j2-workout
mvn clean package -Plog4j-snapshot -U -pl core-java-test -am
mvn exec:java -pl core-java-test

# Check compression behavior
ls -lh core-java-test/logs/
stat core-java-test/logs/app*.log* | grep -E "File|Modify"
```

### Long-Running Compression Test

For a 30-minute test that shows delayed compression more clearly:

```bash
# Run core-java-test's cron-test mode for 5 minutes
# This logs every 10 seconds, giving compression time to happen
mvn exec:java -pl core-java-test -Dexec.args="cron-test 5"

# In another terminal, watch the logs directory
watch 'ls -lh log4j2-workout/core-java-test/logs/ | tail -10'

# You should see:
# - .log files appear (fresh rollover, not yet compressed)
# - After ~0-3 seconds, .log files become .log.gz
```

## Conventions to Follow

**MDC must always be cleared in `finally`** (thread pool reuse):
```java
ThreadContext.put("traceId", id);
try { ... } finally { ThreadContext.clearAll(); }
```

**Marker inheritance** — `AUDIT` is a child of `SECURITY`; a filter on `SECURITY` also catches `AUDIT` logs:
```java
private static final Marker AUDIT = MarkerManager.getMarker("AUDIT").addParents(SECURITY);
```

**Logger declaration pattern** — every class uses both direct Log4j and SLF4J to validate the bridge:
```java
private static final Logger logger = LogManager.getLogger(LogService.class);       // Log4j
private static final org.slf4j.Logger slf4j = LoggerFactory.getLogger(LogService.class);  // SLF4J bridge
```

**core-java-test Docker packaging:** runtime JARs are copied to `target/lib/` by `maven-dependency-plugin`; the Dockerfile builds a thin JAR + lib folder (not a fat JAR). `spring-boot-test` uses a fat JAR via `spring-boot-maven-plugin`.

## Files & Patterns

| Path | Purpose |
|------|---------|
| `core-java-test/src/main/java/com/playground/Main.java` | Entry point; handles `cron-test` mode for rollover testing |
| `core-java-test/src/main/resources/log4j2.xml` | Default config with RollingFile + 1KB + 3-sec delay |
| `core-java-test/infrastructure/test_dst_rollover.sh` | DST rollover edge-case test (changes system time) |
| `spring-boot-test/src/main/resources/application.yml` | Spring Boot logging + actuator config |
| `spring-boot-test/src/main/resources/log4j2-createOnDemand-false.xml` | Config variant with `createOnDemand="false"` for testing |

## Troubleshooting

- **"No Log4j 2 Plugins found"** → `mvn clean package -U`
- **SNAPSHOT not found** → Run `mvn clean install -DskipTests` inside your Log4j source clone first, then rebuild with `-Plog4j-snapshot -U`
- **`containerId: unknown`** → expected outside Docker; the `log4j-docker` module reads from the Docker daemon socket inside containers
- **No .gz files in logs directory** → compression may not have started; check `maxCompressionDelaySeconds` in log4j2.xml and ensure RollingFile appenders are active
- **Gradle/Maven snapshot issues** → use `mvn dependency:purge-local-repository` to force re-download, then `-U` flag

## Related Issues
- **#4012** — Proactively defer compression to reduce midnight disk IO spike
- **Issue resolution commits:**
  - https://github.com/ramanathan1504/logging-log4j2/commit/26933122f5711bf06be05cdb1fe6fc5ae61122e1
  - https://github.com/ramanathan1504/logging-log4j2/commit/8f964f176afbdfa2ca9c240fe0f27e160739128f

