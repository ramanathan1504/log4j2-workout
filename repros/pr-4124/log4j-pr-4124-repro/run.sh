#!/bin/bash
set -euo pipefail
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
BASELINE=2.26.0
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
: > "$OUT/result.txt"
for DV in 4.0.0 3.4.4; do
mvn -q -Dlog4j.version="$RESOLVE" -Ddisruptor.version="$DV" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][^-]*(-SNAPSHOT)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
C="$OUT/disruptor-$DV"; mkdir -p "$C"
java -Dout="$C" -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector -Dlog4j2.StatusLogger.level=DEBUG -cp "$CP" Repro > "$C/stdout.txt" 2>&1 || true
R=$(grep '^RESULT' "$C/stdout.txt" || echo "RESULT none")
DETECTED=$(grep -o 'LMAX Disruptor version detected: [0-9]*' "$C/stdout.txt" | head -1 || true)
ERRORS=$(grep -cE ' (ERROR|FATAL) ' "$C/stdout.txt" || true)
MAJOR="${DV%%.*}"
if [[ "$R" == *"AsyncLogger file=async-event"* ]]; then ok=PASS; else ok=FAIL; fi
echo "$ok disruptor-$DV control: async logger wrote the event observed: ${R#RESULT } statusErrors=$ERRORS" >> "$OUT/result.txt"
if [ "$MAJOR" = 4 ]; then
  if [ "$DETECTED" = "LMAX Disruptor version detected: 4" ]; then ok=PASS; else ok=FAIL; fi
  echo "$ok disruptor-$DV DEBUG status reports detected major version observed=[${DETECTED:-absent}]" >> "$OUT/result.txt"
else
  echo "INFO disruptor-$DV DEBUG version line (v3 returns early, none expected either side) observed=[${DETECTED:-absent}]" >> "$OUT/result.txt"
fi
done
cat "$OUT/result.txt"
