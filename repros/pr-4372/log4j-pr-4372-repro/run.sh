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
RUNS="${RUNS:-30}"
for i in $(seq 1 "$RUNS"); do
  LOGDIR="$OUT/run-$i"; mkdir -p "$LOGDIR"
  java -Dlogdir="$LOGDIR" -cp "$CP" Repro >> "$OUT/runs.txt"
  rm -rf "$LOGDIR"
done
ROLLED=$(grep -c 'rolled=true' "$OUT/runs.txt" || true)
EXPOSED=$(grep -c 'roundedBelowStart=true' "$OUT/runs.txt" || true)
{
  echo "runs=$RUNS exposed=$EXPOSED rolled=$ROLLED"
  if [ "$EXPOSED" -ge 1 ]; then echo "PASS control: the condition occurred observed=$EXPOSED/$RUNS runs with the rounded file time before the JVM start"; else echo "FAIL control: the condition occurred observed=0/$RUNS runs, inconclusive"; fi
  if [ "$ROLLED" -eq 0 ]; then echo "PASS second LoggerContext does not roll the file this JVM created observed=rolled $ROLLED/$RUNS"; else echo "FAIL second LoggerContext does not roll the file this JVM created observed=rolled $ROLLED/$RUNS"; fi
} > "$OUT/result.txt"
cat "$OUT/result.txt"
