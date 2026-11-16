#!/bin/bash
set -euo pipefail
BASELINE=2.26.0
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
java -Dlogdir="$OUT/logs" -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2> "$OUT/status.txt" || true
cat "$OUT/stdout.txt" | tee "$OUT/result.txt"
echo "statuslogger maxRandomDelay-rejected=$(grep -c 'maxRandomDelay' "$OUT/status.txt" || true) $(grep -m1 maxRandomDelay "$OUT/status.txt" | cut -c1-180)" | tee -a "$OUT/result.txt"
for f in $(find "$OUT/logs" -name '*.gz'); do gzip -t "$f" || echo "CORRUPT $f" | tee -a "$OUT/result.txt"; done
echo "gzip-t checked $(find "$OUT/logs" -name '*.gz' | wc -l | tr -d ' ') archives" | tee -a "$OUT/result.txt"
find "$OUT/logs" -name '*.gz' -delete
rm -rf target
