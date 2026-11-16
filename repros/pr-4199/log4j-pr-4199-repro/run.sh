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
if echo "$CP" | grep -q 'jackson-databind'; then echo "jackson-databind must be absent from the classpath" >&2; exit 1; fi
java -Drepro.out="$OUT" -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2> "$OUT/stderr.txt" || true
name=$(sed -n 's/^CONFIG_NAME=//p' "$OUT/stdout.txt")
lines=$(grep -c 'XML info-line-from-repro' "$OUT/app.log" 2>/dev/null || true)
diag=$(grep -c 'Found configuration file `log4j2.json` for the inactive' "$OUT/stderr.txt" || true)
{
  echo "version=$L classpath: log4j2.json + log4j2.xml, no jackson-databind"
  if [ "$name" = "FromXml" ]; then echo "PASS fell back to log4j2.xml (configuration name=$name)"; else echo "FAIL log4j2.xml not used (configuration name=$name)"; fi
  if [ "${lines:-0}" = 1 ]; then echo "PASS INFO event written by the XML File appender (app.log lines=$lines)"; else echo "FAIL INFO event not in app.log (lines=${lines:-0})"; fi
  if [ "$diag" -gt 0 ]; then echo "PASS inactive-factory diagnostic for log4j2.json still reported (count=$diag)"; else echo "FAIL inactive-factory diagnostic missing (count=$diag)"; fi
  grep 'CONFIG_CLASS=' "$OUT/stdout.txt" | sed 's/^/INFO /'
} | tee "$OUT/result.txt"
