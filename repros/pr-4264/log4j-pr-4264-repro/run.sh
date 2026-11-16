#!/bin/bash
set -euo pipefail
BASELINE=2.26.1
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
mvn -q -Dlog4j.version="$RESOLVE" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][^-]*(-SNAPSHOT)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/status.txt" 2>&1
for configurator in DOMConfigurator PropertyConfigurator; do
  line=$(grep -A1 "Ignoring \`$configurator\` call" "$OUT/status.txt" | grep -oE 'https://[^ ]+' | head -1 || true)
  if [ -z "$line" ]; then verdict=FAIL; observed=no-warning
  elif [[ "$line" == https://logging.apache.org/* ]]; then verdict=PASS; observed="$line"
  else verdict=FAIL; observed="$line"; fi
  echo "$verdict $configurator-warning-links-production-site observed=$observed" | tee -a "$OUT/result.txt"
done
