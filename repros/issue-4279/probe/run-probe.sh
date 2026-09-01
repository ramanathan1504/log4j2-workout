#!/usr/bin/env bash
# The same cases as the repro, PatternLayout only, so the 3.x column can be
# measured without the layout-template-json module (which needs JDK 17 exactly).
#
#   ./run-probe.sh 2.26.1
#   ./run-probe.sh 3.0.0-SNAPSHOT
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-2.26.1}"
API_VERSION="$VERSION"
case "$VERSION" in
  3.*) API_VERSION=2.24.3 ;;
esac
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.apache.logging.repro</groupId>
  <artifactId>probe</artifactId>
  <version>1.0-SNAPSHOT</version>
  <dependencies>
    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>$API_VERSION</version></dependency>
    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>$VERSION</version></dependency>
  </dependencies>
</project>
POM

mvn -q -f "$WORK/pom.xml" dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt"
CP="$(cat "$WORK/cp.txt")"
javac -nowarn -d "$WORK/classes" -cp "$CP" Probe.java

mkdir -p "$WORK/run/target"
echo "=== $VERSION (core) / $API_VERSION (api) ==="
( cd "$WORK/run" && java -cp "$WORK/classes:$OLDPWD/conf:$CP" \
    -Dlog4j.configuration.location=classpath:log4j2.xml Probe )
