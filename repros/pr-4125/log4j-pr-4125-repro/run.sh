#!/bin/bash
set -euo pipefail
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
BASELINE=2.26.0
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
mvn -q -Dlog4j.version="$RESOLVE" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][^-]*(-SNAPSHOT)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro > "$OUT/stdout.txt" 2>&1 || true
R=$(grep '^RESULT' "$OUT/stdout.txt" || echo "RESULT none")
if [[ "$R" == *"sendAttempts=2 delivered=[retried-event]"* ]]; then ok=PASS; else ok=FAIL; fi
echo "$ok control: first send fails, retry delivers the event once observed: ${R#RESULT }" > "$OUT/result.txt"
ERRS=$(grep -c 'Unable to write to Kafka in appender' "$OUT/stdout.txt" || true)
LINE=$(grep -m1 -E ' (ERROR|WARN) ' "$OUT/stdout.txt" | sed -E 's/^[^ ]+ [^ ]+ //' || true)
if [ "$ERRS" = 0 ]; then ok=PASS; else ok=FAIL; fi
echo "$ok no error reported after a successful retry observed=$ERRS 'Unable to write to Kafka' status errors${LINE:+, first: $LINE}" >> "$OUT/result.txt"
cat "$OUT/result.txt"
