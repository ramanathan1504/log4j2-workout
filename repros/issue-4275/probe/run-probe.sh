#!/usr/bin/env bash
# Widens issue #4275: shows that %tp is frozen along with %t, and that the
# mixed-async path is not affected.
#
#   ./run-probe.sh 2.26.1
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-2.26.1}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.apache.logging.repro</groupId>
  <artifactId>probe</artifactId>
  <version>1.0-SNAPSHOT</version>
  <dependencies>
    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>$VERSION</version></dependency>
    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>$VERSION</version></dependency>
    <dependency><groupId>com.lmax</groupId><artifactId>disruptor</artifactId><version>4.0.0</version></dependency>
  </dependencies>
</project>
POM

mvn -q -f "$WORK/pom.xml" dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt"
CP="$(cat "$WORK/cp.txt")"
javac -nowarn -d "$WORK/classes" -cp "$CP" Probe.java

echo "=== full async (AsyncLoggerContextSelector), $VERSION, UNCACHED ==="
java -cp "$WORK/classes:full-async:$CP" \
  -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
  -Dlog4j2.asyncLoggerThreadNameStrategy=UNCACHED Probe

echo "=== mixed async (AsyncRoot), $VERSION, UNCACHED ==="
java -cp "$WORK/classes:mixed-async:$CP" \
  -Dlog4j2.asyncLoggerThreadNameStrategy=UNCACHED Probe
