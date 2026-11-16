#!/bin/bash
set -euo pipefail
JARS="${1:?usage: pr4322.sh <2.x jars dir> <work dir> [log4j clone]}"
WORK="${2:?usage: pr4322.sh <2.x jars dir> <work dir> [log4j clone]}"
CLONE="${3:-$HOME/apache/logging-log4j2}"
HERE="$(cd "$(dirname "$0")" && pwd)"
P=log4j-core/src/main/java/org/apache/logging/log4j/core/util/internal/instant/InstantPatternThreadLocalCachedFormatter.java
rm -rf "$WORK"; mkdir -p "$WORK/src" "$WORK/classes" "$WORK/jars" "$WORK/probe"
git -C "$CLONE" show refs/pr/4322:"$P" > "$WORK/src/InstantPatternThreadLocalCachedFormatter.java"
cp "$JARS"/*.jar "$WORK/jars/"
CORE=$(ls "$WORK"/jars/log4j-core-2*.jar | head -1)
API=$(ls "$WORK"/jars/log4j-api-2*.jar | head -1)
javac --release 8 -nowarn -cp "$CORE:$API" -d "$WORK/classes" "$WORK/src/InstantPatternThreadLocalCachedFormatter.java"
(cd "$WORK/classes" && jar uf "$CORE" org)
javac --release 8 -nowarn -cp "$CORE:$API" -d "$WORK/probe" $(find "$HERE/src/pr4322" -name '*.java')
echo "== 2.x jars"
java -cp "$WORK/probe:$(ls "$JARS"/log4j-core-2*.jar | head -1):$(ls "$JARS"/log4j-api-2*.jar | head -1)" org.apache.logging.log4j.core.util.internal.instant.PoisonProbe
echo "== 2.x jars with #4322"
java -cp "$WORK/probe:$CORE:$API" org.apache.logging.log4j.core.util.internal.instant.PoisonProbe
"$HERE/run.sh" "$WORK/jars" review-2.x-ea28f5a78b-with-pr4322
