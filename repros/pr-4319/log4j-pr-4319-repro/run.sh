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
run_case() {
  local mode="$1" events="$2"
  java -Drepro.failure="$mode" -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro "$events" >"$OUT/stdout-$mode.txt" 2>"$OUT/stderr-$mode.txt" || true
  OBSERVED=$(cat "$OUT/stdout-$mode.txt")
  DISCARD=$(grep -o 'discarded [0-9]* buffered events after a database flush failure' "$OUT/stderr-$mode.txt" | head -1 || true)
  DISCARD="${DISCARD:-no discard warning}"
}
: > "$OUT/result.txt"
{
  run_case rollback-always 20
  largest=$(echo "$OBSERVED" | sed -E 's/.*rowsSentPerFlush=\[([^]]*)\].*/\1/' | tr ',' '\n' | sort -n | tail -1 | tr -d ' ')
  if [ "$largest" -le 5 ]; then v=PASS; else v=FAIL; fi
  echo "$v A persistent commit failure, buffer must not grow past bufferSize=5, largest flush=$largest: $OBSERVED; $DISCARD"
  run_case after-commit-once 10
  if [[ "$OBSERVED" == *"duplicated=0"* ]]; then v=PASS; else v=FAIL; fi
  echo "$v B commit reached the database then threw once, no duplicate rows: $OBSERVED; $DISCARD"
  run_case rollback-once 10
  echo "INFO C commit rolled back once, the cost of discarding: $OBSERVED; $DISCARD"
} | tee "$OUT/result.txt"
