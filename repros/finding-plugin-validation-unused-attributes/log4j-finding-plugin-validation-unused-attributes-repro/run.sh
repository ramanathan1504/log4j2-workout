#!/bin/bash
set -euo pipefail
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then
  if ls "$ARG"/log4j-plugins-*.jar >/dev/null 2>&1; then RESOLVE=3.0.0-beta3; else RESOLVE=2.26.1; fi
else
  RESOLVE="$ARG"
fi
case "$RESOLVE" in
  3.0.0-alpha*) API="$RESOLVE" ;;
  3.*) API=2.24.3 ;;
  *) API="$RESOLVE" ;;
esac
mvn -q -Dlog4j.version="$RESOLVE" -Dlog4j.api.version="$API" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
OUTDIR="$(cd "$OUT" && pwd)"
java -Doutdir="$OUTDIR" -Dlog4j.configuration.location=log4j2.xml -Dlog4j2.configurationFile=log4j2.xml -cp "$CP" Repro >"$OUT/stdout.txt" 2>"$OUT/status.txt" || true
grep -E "^(PASS|FAIL|unused-attribute-or-element-errors)" "$OUT/stdout.txt" | tee "$OUT/result.txt"
echo "classpath-log4j: $(tr ':' '\n' <<< "$CP" | grep -o 'log4j-[a-z0-9-]*-[0-9][^/]*\.jar' | tr '\n' ' ')" | tee -a "$OUT/result.txt"
