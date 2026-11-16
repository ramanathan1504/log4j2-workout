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
COMMON=(-Dlog4j2.configurationFile=target/classes/log4j2.xml -Dlog4j2.StatusLogger.level=WARN
  -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
  -Dlog4j2.asyncLoggerRingBufferSize=128 -Dlog4j2.asyncQueueFullPolicy=AlwaysSynchronousPolicy)
{ echo "version=$L"; echo "log4j-jars=$(tr ':' '\n' <<<"$CP" | grep -oE 'log4j-[a-z-]+-[0-9][^/]*\.jar' | tr '\n' ' ')"; } > "$OUT/result.txt"
for TL in true false; do
  java "${COMMON[@]}" -Dlog4j2.enableThreadlocals=$TL -cp "$CP" Repro >"$OUT/stdout-threadlocals-$TL.txt" 2>"$OUT/status-threadlocals-$TL.txt" || true
  { echo "translator: threadlocals=$TL"; sed 's/^/  /' "$OUT/stdout-threadlocals-$TL.txt"; echo "  status-logger (lines, first):"; echo "    $(wc -l < "$OUT/status-threadlocals-$TL.txt" | tr -d ' ') $(head -1 "$OUT/status-threadlocals-$TL.txt")"; } >> "$OUT/result.txt"
done
cat "$OUT/result.txt"
