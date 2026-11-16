#!/bin/bash
set -euo pipefail
BASELINE=3.0.0-beta3
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
mvn -q -Dlog4j.version="$RESOLVE" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][0-9.]*(-[A-Za-z0-9]+)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
OUTDIR="$(cd "$OUT" && pwd)"
java -Doutdir="$OUTDIR" -Dlog4j.configuration.location=log4j2.xml -Xmx128m -Dlog4j.statusLoggerLevel=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2>&1 || true
grep -E "^(PASS|FAIL)" "$OUT/stdout.txt" | tee "$OUT/result.txt"
echo "classpath-log4j: $(tr ':' '\n' <<< "$CP" | grep -o 'log4j-[a-z-]*-[0-9][^/]*' | tr '\n' ' ')" | tee -a "$OUT/result.txt"
