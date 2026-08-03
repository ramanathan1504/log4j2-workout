# Log4j 2 Workout

Multi-module Maven workspace for real-case Log4j 2 integration testing.
See `plan.md` for full context and goals.

---

## Project structure

```
log4j2-workout/
├── pom.xml                          ← parent (multi-module, version profiles)
├── plan.md
│
├── core-java-test/                  ← plain Java — tests every Log4j module
│   ├── pom.xml
│   ├── src/main/java/com/playground/
│   │   ├── Main.java                ← entry point, runs all scenarios
│   │   └── LogTestScenarios.java    ← 7 real-case scenarios
│   ├── src/main/resources/
│   │   ├── log4j2.xml               ← sync: Console + JSON + RollingFile
│   │   └── log4j2-async.xml         ← async: AsyncLogger + AsyncAppender
│   └── infrastructure/
│       ├── Dockerfile               ← multi-stage, dynamic Java/Log4j version
│       └── k8s-job.yaml             ← Kubernetes Job (one-shot test runner)
│
└── spring-boot-test/                ← real Spring Boot app + REST endpoints
    ├── pom.xml
    ├── src/main/java/com/springtest/
    │   ├── SpringBootTestApp.java
    │   ├── controller/LogTestController.java  ← REST test endpoints
    │   └── service/LogService.java            ← all logging scenarios
    ├── src/main/resources/
    │   ├── application.yml          ← actuator, logging config
    │   └── log4j2.xml               ← Console + JSON + RollingFile
    └── infrastructure/
        ├── Dockerfile               ← multi-stage fat-jar, dynamic versions
        └── k8s-deployment.yaml      ← Deployment + Service + health probes
```

---

## Requirements

| Tool  | Minimum version |
|-------|----------------|
| Java  | 17 (or 21 via `-Pjava21`) |
| Maven | 3.8+           |
| Docker | 20+ (optional) |
| kubectl | any (optional) |

---

## Version profiles

All versions are controlled from the **parent `pom.xml`**.
Override at build time with `-D` flags or Maven profiles:

| Profile / Flag | What it does |
|---|---|
| *(default)* | Log4j `2.25.3`, Java `17` |
| `-Pjava21` | Java `21` |
| `-Plog4j-snapshot` | Log4j `3.0.0-SNAPSHOT` (local `~/.m2` build) |
| `-Plog4j-2x-maintenance` | Log4j `2.24.3` |
| `-Plog4j-legacy` | Log4j `2.12.4`, Java `8` |
| `-Dlog4j.version=X.Y.Z` | Any explicit version inline |

---

## 1 — Core Java test

### Run locally (sync config)
```bash
cd log4j2-workout
mvn clean package -pl core-java-test -am
mvn exec:java   -pl core-java-test
```

### Run with async config (AsyncLogger + LMAX Disruptor)
```bash
mvn exec:java -pl core-java-test -Dlog4j.configurationFile=log4j2-async.xml
```

### Switch Log4j version
```bash
# Local SNAPSHOT build from source
mvn clean package exec:java -pl core-java-test -am -Plog4j-snapshot -U

# Any released version inline
mvn clean package exec:java -pl core-java-test -am -Dlog4j.version=2.24.3
```

### What it tests (7 scenarios)
| # | Scenario | What you see |
|---|----------|-------------|
| 1 | All log levels | TRACE → FATAL + SLF4J bridge lines |
| 2 | MDC / ThreadContext | `traceId`, `userId`, `env` in JSON `mdc` field |
| 3 | Markers | `SECURITY`, `AUDIT` (inherits SECURITY), `PERFORMANCE` |
| 4 | Parameterized + StructuredData | `{}` style, lazy supplier, RFC 5424 SDM |
| 5 | Exception logging | single + chained cause stack traces |
| 6 | Fluent API | `atInfo/atWarn/atError` with marker + throwable |
| 7 | Multi-thread | 4 workers, independent MDC per thread |

### Docker
```bash
# Build from workspace root — dynamic Java and Log4j version
docker build -t core-java-test:latest \
  -f core-java-test/infrastructure/Dockerfile \
  --build-arg JAVA_VERSION=17 \
  --build-arg LOG4J_VERSION=2.25.3 .

# Run (sync config)
docker run --rm core-java-test:latest

# Run (async config)
docker run --rm -e LOG4J_CONFIG=log4j2-async.xml core-java-test:latest
```

### Kubernetes
```bash
kubectl apply -f core-java-test/infrastructure/k8s-job.yaml
kubectl logs -f job/core-java-log4j-test
```

---

## 2 — Spring Boot test

### Run locally
```bash
cd log4j2-workout
mvn clean package -pl spring-boot-test -am
mvn spring-boot:run -pl spring-boot-test
```

### REST endpoints (all trigger real logging scenarios)
| Endpoint | Scenario |
|---|---|
| `GET /api/logs/all-levels` | TRACE → FATAL + SLF4J bridge |
| `GET /api/logs/mdc?user=alice&traceId=t-001` | MDC with user + traceId |
| `GET /api/logs/exception` | single + chained exception |
| `GET /api/logs/markers` | SECURITY / AUDIT / PERFORMANCE markers |
| `GET /api/logs/async` | async thread with MDC propagation |
| `GET /api/logs/info` | returns Log4j version + Java version as JSON |
| `GET /api/logs/all` | runs every scenario at once |

```bash
# Quick smoke test — runs all scenarios
curl http://localhost:8081/api/logs/all

# MDC test
curl "http://localhost:8081/api/logs/mdc?user=alice&traceId=trace-001"

# Check active Log4j version
curl http://localhost:8081/api/logs/info

# Actuator health (liveness + readiness)
curl http://localhost:8081/actuator/health

# Change log level at runtime (no restart needed)
curl -X POST http://localhost:8081/actuator/loggers/com.springtest \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel":"DEBUG"}'
```

### Switch Log4j version
```bash
# Local SNAPSHOT
mvn clean package spring-boot:run -pl spring-boot-test -am -Plog4j-snapshot -U

# Inline version
mvn clean package spring-boot:run -pl spring-boot-test -am -Dlog4j.version=2.24.3
```

### Docker
```bash
# Build from workspace root
docker build -t spring-boot-log4j2-test:latest \
  -f spring-boot-test/infrastructure/Dockerfile \
  --build-arg JAVA_VERSION=17 \
  --build-arg LOG4J_VERSION=2.25.3 .

# Run
docker run -p 8081:8081 spring-boot-log4j2-test:latest

# Run with async config
docker run -p 8081:8081 -e LOG4J_CONFIG=log4j2-async.xml spring-boot-log4j2-test:latest

# Test
curl http://localhost:8081/api/logs/all
```

### Kubernetes
```bash
kubectl apply -f spring-boot-test/infrastructure/k8s-deployment.yaml

# Port-forward and test
kubectl port-forward svc/spring-boot-log4j2-test 8081:80
curl http://localhost:8081/api/logs/all

# Watch JSON logs
kubectl logs -f deployment/spring-boot-log4j2-test
```

---

## Build everything at once

```bash
cd log4j2-workout
mvn clean package
```

### Build with a different profile
```bash
mvn clean package -Pjava21
mvn clean package -Plog4j-snapshot -U
mvn clean package -Plog4j-2x-maintenance
```

---

## Troubleshooting

**"No Log4j 2 Plugins found"** — run `mvn clean package -U` to force refresh.

**SNAPSHOT not found** — run `mvn clean install -DskipTests` inside your Log4j source repo first, then rebuild with `-Plog4j-snapshot -U`.

**containerId shows `unknown`** — expected outside Docker. Inside a running container the Docker module reads the real container ID from the Docker daemon socket.
