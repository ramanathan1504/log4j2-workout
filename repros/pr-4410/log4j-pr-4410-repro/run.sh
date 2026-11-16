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
OUTDIR="$(cd "$OUT" && pwd)"
JACKSON3_ONLY="$(tr ':' '\n' <<<"$CP" | grep -vE '/com/fasterxml/jackson/(core/jackson-(core|databind)|dataformat)/|/org/yaml/snakeyaml/' | paste -sd: -)"
: > "$OUT/result.txt"
echo "version=$L" >> "$OUT/result.txt"
for variant in jackson3-only jackson2+jackson3; do
  if [ "$variant" = jackson3-only ]; then RUNCP="$JACKSON3_ONLY"; else RUNCP="$CP"; fi
  VOUT="$OUTDIR/$variant"; mkdir -p "$VOUT"
  java -Doutdir="$VOUT" -Dlog4j2.configurationFile=target/classes/log4j2.yaml -Dlog4j2.StatusLogger.level=WARN \
    -cp "$RUNCP" Repro "$variant" >"$VOUT/stdout.txt" 2>"$VOUT/status.txt" || true
  { echo "[$variant] jackson jars: $(tr ':' '\n' <<<"$RUNCP" | grep -E 'jackson-(core|databind|dataformat-yaml)|snakeyaml' | xargs -n1 basename | tr '\n' ' ')"
    cat "$VOUT/stdout.txt"; echo "[$variant] status-logger:"; head -5 "$VOUT/status.txt" | cut -c1-240 | sed 's/^/  /'; } >> "$OUT/result.txt"
  rm -f "$VOUT/stdout.txt"
done
cat "$OUT/result.txt"
