#!/bin/bash
set -euo pipefail
V="${1:?usage: run.sh <log4j version> [label] | run.sh head <log4j2 clone> <label>}"
cd "$(dirname "$0")"
if [ "$V" = head ]; then
  C="${2:?clone path}"; L="${3:?label}"
  mvn -q package >/dev/null
  CP="target/classes:$(ls "$C"/log4j-api/target/log4j-api-*-SNAPSHOT.jar | grep -v -e sources -e javadoc -e tests | head -1):$(ls "$C"/log4j-core/target/log4j-core-*-SNAPSHOT.jar | grep -v -e sources -e javadoc -e tests | head -1)"
else
  L="${2:-$V}"
  mvn -q -Dlog4j.version="$V" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
  CP="target/classes:$(cat target/cp.txt)"
fi
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
echo "classpath: $CP" > "$OUT/result.txt"
for s in S1 S2 S3; do
  echo "== $s" | tee -a "$OUT/result.txt"
  java -cp "$CP" Repro "$s" 2>&1 | sed -E 's/"timestamp":[0-9.]+/"timestamp":T/' | tee -a "$OUT/result.txt"
done
