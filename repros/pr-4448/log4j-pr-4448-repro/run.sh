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
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
OUTDIR="$(cd "$OUT" && pwd)"
java -Doutdir="$OUTDIR" -Dlog4j.configuration.location=target/classes/log4j2.xml -Dlog4j.statusLoggerLevel=WARN \
  -cp "$CP" Repro >"$OUT/stdout.txt" 2>"$OUT/status.txt" || true
{ echo "version=$L"; echo "log4j-jars=$(tr ':' '\n' <<<"$CP" | grep -oE 'log4j-[a-z-]+-[0-9][^/]*\.jar' | tr '\n' ' ')"
  grep -E '^(PASS|FAIL) ' "$OUT/stdout.txt" || true
  echo "status-logger (count, message):"
  { grep -vhE '^(PASS|FAIL) ' "$OUT/stdout.txt" "$OUT/status.txt" || true; } | sed -E 's/^[0-9T:.-]+Z //' | sort | uniq -c | sed 's/^/  /'; } > "$OUT/result.txt"
cat "$OUT/result.txt"
