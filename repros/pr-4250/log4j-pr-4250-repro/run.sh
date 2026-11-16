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
java -Dlog4j2.configurationFile=unresolved-layout.properties -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2> "$OUT/stderr.txt" || true
cat "$OUT/stdout.txt" "$OUT/stderr.txt" > "$OUT/all.txt"
npe=$(grep -c 'NullPointerException' "$OUT/all.txt" || true)
ignored=$(grep -c 'Ignoring unresolved element ThisLayoutDoesNotExist' "$OUT/all.txt" || true)
appender=$(grep -o 'STDOUT_APPENDER=.*' "$OUT/stdout.txt" || echo 'STDOUT_APPENDER=missing')
marker=$(grep -c 'marker-line-from-repro' "$OUT/stdout.txt" || true)
{
  echo "version=$L"
  if [ "$npe" = 0 ]; then echo "PASS no NullPointerException during configuration (count=$npe)"; else echo "FAIL NullPointerException reported during configuration (count=$npe)"; fi
  if [ "$ignored" -gt 0 ]; then echo "PASS unresolved element reported as ignored (count=$ignored)"; else echo "FAIL no 'Ignoring unresolved element' status message (count=$ignored)"; fi
  if [ "$appender" != "STDOUT_APPENDER=absent" ] && [ "$appender" != "STDOUT_APPENDER=missing" ]; then echo "PASS StdOut appender built ($appender)"; else echo "FAIL StdOut appender not built ($appender)"; fi
  echo "INFO marker line on stdout count=$marker"
  grep -E '^(CONFIG_CLASS|CONFIG_STATE|DEFAULT_CONFIG)=' "$OUT/stdout.txt" | sed 's/^/INFO /'
} | tee "$OUT/result.txt"
