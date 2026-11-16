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
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
LOGDIR="$OUT/logs"; mkdir -p "$LOGDIR"
START=$(date +%s)
java -Dlogdir="$LOGDIR" -cp "$CP" Repro > "$OUT/app-stdout.txt" 2>&1 &
PID=$!
EXITED=no
for _ in $(seq 1 30); do
  if ! kill -0 "$PID" 2>/dev/null; then EXITED=yes; break; fi
  sleep 0.5
done
if [ "$EXITED" = no ]; then kill "$PID"; wait "$PID" 2>/dev/null || true; fi
ELAPSED=$(( $(date +%s) - START ))
ARCHIVES=$(grep -o 'archives=[0-9]*' "$OUT/app-stdout.txt" | cut -d= -f2)
THREADS=$(grep 'non-daemon threads' "$OUT/app-stdout.txt" | sed 's/.*=//')
{
  cat "$OUT/app-stdout.txt"
  if [ "${ARCHIVES:-0}" -ge 1 ]; then echo "PASS control: rollover ran observed=archives=$ARCHIVES"; else echo "FAIL control: rollover ran observed=archives=${ARCHIVES:-0}"; fi
  if [ "$THREADS" = "[]" ]; then echo "PASS no idle non-daemon Log4j thread observed=$THREADS"; else echo "FAIL no idle non-daemon Log4j thread observed=$THREADS"; fi
  if [ "$EXITED" = yes ]; then echo "PASS JVM exits after main returns observed=exited within ${ELAPSED}s"; else echo "FAIL JVM exits after main returns observed=still running after ${ELAPSED}s, killed"; fi
} > "$OUT/result.txt"
rm -rf "$LOGDIR"
cat "$OUT/result.txt"
