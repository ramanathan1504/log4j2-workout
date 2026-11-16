#!/bin/bash
set -euo pipefail
BASELINE=2.26.1
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
MVN=(-Dlog4j.version="$RESOLVE")
MODES="single single-no-override composite composite-factory composite-monitor composite-ctx"
if [[ "$RESOLVE" == 3.* ]] || { [ -d "$ARG" ] && ls "$ARG"/log4j-core-3.* >/dev/null 2>&1; }; then
  [ -d "$ARG" ] && MVN=(-Dlog4j.version=3.0.0-beta3)
  MVN+=(-Dlog4j.api.version=2.24.3 -Dsrc.dir=src3/main/java -Dmaven.compiler.release=17)
  MODES="single single-no-override composite composite-monitor composite-ctx"
fi
rm -rf target
mvn -q "${MVN[@]}" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
echo "$CP" | tr ':' '\n' | grep log4j > "$OUT/classpath.txt"
: > "$OUT/result.txt"
for mode in $MODES; do
  java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro "$mode" "$OUT/work" 2>>"$OUT/stderr.txt" | grep -E '^(PASS|FAIL)' | tee -a "$OUT/result.txt"
done
rm -rf "$OUT/work"
