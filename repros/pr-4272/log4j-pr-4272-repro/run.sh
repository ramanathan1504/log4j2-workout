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
  a=$(basename "$e" | sed -E 's/-[0-9][^-]*(-SNAPSHOT)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
echo "classpath log4j jars: $(tr ':' '\n' <<< "$CP" | grep -o 'log4j-[a-z0-9-]*[0-9][^/]*\.jar' | tr '\n' ' ')" > "$OUT/result.txt"
BASE_CP="target/classes:$(cat target/cp.txt)"
java -cp "$BASE_CP" Repro forge "$OUT/forged-$BASELINE.ser" 2>&1 | tee -a "$OUT/result.txt"
java -cp "$CP" Repro check "$OUT/forged-$BASELINE.ser" 2>&1 | tee -a "$OUT/result.txt"
