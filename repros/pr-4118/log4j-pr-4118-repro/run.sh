#!/bin/bash
set -euo pipefail
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
BASELINE=2.26.1
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
mvn -q -Dlog4j.version="$RESOLVE" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][^-]*(-SNAPSHOT)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2> "$OUT/stderr.txt" || true
cp "$OUT/stdout.txt" "$OUT/result.txt"
NPE=$(cat "$OUT/stderr.txt" "$OUT/stdout.txt" | grep -c 'caught error running command' || true)
if [ "$NPE" = 0 ]; then echo "PASS no 'caught error running command' status error observed=0" >> "$OUT/result.txt"; else echo "FAIL 'caught error running command' status errors observed=$NPE" >> "$OUT/result.txt"; fi
cat "$OUT/result.txt"
