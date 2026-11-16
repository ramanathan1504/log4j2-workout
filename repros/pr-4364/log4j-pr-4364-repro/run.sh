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
echo "$CP" | tr ':' '\n' | grep log4j- > "$OUT/classpath.txt"

check() {
  local level="$1" secret="$2" file="$OUT/status-$1.txt"
  local leaks masked
  leaks=$({ grep -o "$secret" "$file" || true; } | wc -l | tr -d ' ')
  masked=$(grep -c 'java.naming.security.credentials=\*\*\*\*\*' "$file" || true)
  if [ "$leaks" -eq 0 ]; then v=PASS; else v=FAIL; fi
  echo "$v level=$level secret=$secret occurrences=$leaks, lines with credentials=***** : $masked" | tee -a "$OUT/result.txt"
}

for level in WARN DEBUG; do
  java -Dlog4j2.enableJndiJms=true -Dlog4j2.StatusLogger.level=$level -cp "$CP" Repro >"$OUT/status-$level.txt" 2>&1 || true
  check $level JndiSecretJavax
  check $level JndiSecretJakarta
done
errors=$(grep -c 'Error creating JmsManager using JmsManagerConfiguration' "$OUT/status-WARN.txt" || true)
echo "control: 'Error creating JmsManager' ERROR lines at WARN=$errors (expected 2, one per appender)" | tee -a "$OUT/result.txt"
grep 'Error creating JmsManager' "$OUT/status-WARN.txt" | cut -c1-400 | sed 's/^/  /' | tee -a "$OUT/result.txt" || true
