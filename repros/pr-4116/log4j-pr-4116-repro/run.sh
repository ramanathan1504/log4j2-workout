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
java -Dout="$OUT" -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2> "$OUT/stderr.txt" || true
cat "$OUT/stdout.txt" > "$OUT/result.txt"
STATUS=$(cat "$OUT/stderr.txt" "$OUT/stdout.txt" | grep -c 'invalid element or attribute' || true)
if [ "$STATUS" = 0 ]; then echo "PASS no 'invalid element or attribute' status message observed=0" >> "$OUT/result.txt"; else echo "FAIL 'invalid element or attribute' status messages observed=$STATUS: $(grep -h 'invalid element' "$OUT/stderr.txt" "$OUT/stdout.txt" | head -1)" >> "$OUT/result.txt"; fi
cat "$OUT/result.txt"
