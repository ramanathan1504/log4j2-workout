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
PROVIDER=target/provider
rm -rf "$PROVIDER"; mkdir -p "$PROVIDER"
if javac -d "$PROVIDER" -cp "$CP" src/provider/FixedTraceContextProvider.java 2>"$OUT/provider-javac.txt"; then
  cp -R src/provider/META-INF "$PROVIDER/"
  echo "provider: TraceContextProvider SPI present, FixedTraceContextProvider registered" > "$OUT/result.txt"
else
  echo "provider: TraceContextProvider SPI absent from log4j-api, provider not compiled" > "$OUT/result.txt"
fi
java -Doutdir="$OUT" -Dlog4j2.StatusLogger.level=WARN -cp "$PROVIDER:$CP" Repro >"$OUT/stdout.txt" 2>"$OUT/status.txt" || echo "exit=$?" >> "$OUT/result.txt"
cat "$OUT/stdout.txt" >> "$OUT/result.txt"
grep -iE 'resolver|error' "$OUT/status.txt" | head -5 | sed 's/^/status: /' >> "$OUT/result.txt" || true
cat "$OUT/result.txt"
