# ─────────────────────────────────────────────────────────────────────────
# Spring Boot Log4j2 Test — Docker image
#
# Build from workspace ROOT (log4j2-workout/):
#   docker build -t spring-boot-log4j2-test:latest \
#     -f spring-boot-test/infrastructure/Dockerfile \
#     --build-arg JAVA_VERSION=17 \
#     --build-arg LOG4J_VERSION=2.25.3 .
#
# Switch Java:    --build-arg JAVA_VERSION=21
# Switch Log4j:   --build-arg LOG4J_VERSION=3.0.0-SNAPSHOT
#
# Run:
#   docker run -p 8081:8081 spring-boot-log4j2-test:latest
#
# Test:
#   curl http://localhost:8081/api/logs/all
#   curl http://localhost:8081/actuator/health
# ─────────────────────────────────────────────────────────────────────────

ARG JAVA_VERSION=17
ARG LOG4J_VERSION=2.25.3

# ── Stage 1: Build ────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /workspace

ARG LOG4J_VERSION
ARG JAVA_VERSION

# Copy parent POM first (layer caching)
COPY pom.xml ./pom.xml

# Copy module POM — fetch dependencies in isolation
COPY spring-boot-test/pom.xml ./spring-boot-test/pom.xml

# Pre-fetch dependencies (cached if pom.xml unchanged)
RUN mvn -pl spring-boot-test -am dependency:resolve -q \
    -Dlog4j.version=${LOG4J_VERSION} \
    -Djava.version=${JAVA_VERSION}

# Copy sources and build fat jar via spring-boot:repackage
COPY spring-boot-test/src ./spring-boot-test/src
RUN mvn -pl spring-boot-test -am -q -DskipTests package \
    -Dlog4j.version=${LOG4J_VERSION} \
    -Djava.version=${JAVA_VERSION}

# ── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:${JAVA_VERSION}-jre
WORKDIR /app

ARG LOG4J_VERSION
ENV LOG4J_VERSION=${LOG4J_VERSION}

# Default: sync config. Override with: -e LOG4J_CONFIG=log4j2-async.xml
ENV LOG4J_CONFIG=log4j2.xml
ENV SERVER_PORT=8081

COPY --from=build /workspace/spring-boot-test/target/spring-boot-test-1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", \
  "java -Dlog4j.configurationFile=${LOG4J_CONFIG} \
        -jar /app/app.jar \
        --server.port=${SERVER_PORT}"]

