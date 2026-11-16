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
java -Doutdir="$OUTDIR" -Dlog4j2.configurationFile=target/classes/log4j2.xml -Dlog4j2.StatusLogger.level=WARN \
  -Dlog4j2.messageFactory=org.apache.logging.log4j.message.StringFormatterMessageFactory \
  -cp "$CP" Repro >"$OUT/stdout.txt" 2>"$OUT/status.txt" || true
{ echo "version=$L"; echo "binding=$(tr ':' '\n' <<<"$CP" | grep slf4j2-impl)"; cat "$OUT/stdout.txt"; echo "status-logger:"; sed 's/^/  /' "$OUT/status.txt"; } > "$OUT/result.txt"
rm -f "$OUT/stdout.txt"
cat "$OUT/result.txt"
