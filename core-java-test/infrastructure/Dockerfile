# ─────────────────────────────────────────────────────────────────────────
# Core Java Log4j2 Test — Docker image
#
# Build from workspace ROOT (log4j2-workout/):
#   docker build -t core-java-test:latest \
#     -f core-java-test/infrastructure/Dockerfile \
#     --build-arg JAVA_VERSION=17 \
#     --build-arg LOG4J_VERSION=2.25.3 .
#
# Switch Java:    --build-arg JAVA_VERSION=21
# Switch Log4j:   --build-arg LOG4J_VERSION=3.0.0-SNAPSHOT
#
# Run (default sync config):
#   docker run --rm core-java-test:latest
#
# Run with async config:
#   docker run --rm -e LOG4J_CONFIG=log4j2-async.xml core-java-test:latest
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

# Copy module POM — triggers dependency download in isolation
COPY core-java-test/pom.xml ./core-java-test/pom.xml

# Pre-fetch dependencies (cached if pom.xml unchanged)
RUN mvn -pl core-java-test -am dependency:resolve -q \
    -Dlog4j.version=${LOG4J_VERSION} \
    -Djava.version=${JAVA_VERSION}

# Copy sources and build
COPY core-java-test/src ./core-java-test/src
RUN mvn -pl core-java-test -am -q -DskipTests package \
    -Dlog4j.version=${LOG4J_VERSION} \
    -Djava.version=${JAVA_VERSION}

# ── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:${JAVA_VERSION}-jre
WORKDIR /app

ARG LOG4J_VERSION
ENV LOG4J_VERSION=${LOG4J_VERSION}

# Default config — override with: -e LOG4J_CONFIG=log4j2-async.xml
ENV LOG4J_CONFIG=log4j2.xml

COPY --from=build /workspace/core-java-test/target/core-java-test-1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /workspace/core-java-test/target/lib /app/lib

# Entrypoint: classpath = app jar + all dependency jars in /app/lib
ENTRYPOINT ["sh", "-c", \
  "java -Dlog4j.configurationFile=${LOG4J_CONFIG} \
        -cp /app/app.jar:/app/lib/* \
        com.playground.Main"]
