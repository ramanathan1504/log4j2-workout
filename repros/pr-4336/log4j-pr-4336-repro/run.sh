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
: > "$OUT/result.txt"
java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro 20000 >"$OUT/stdout-20000.txt" 2>"$OUT/stderr-20000.txt"
if grep -q 'sizes=\[13, 20001, 12\]' "$OUT/stdout-20000.txt"; then
  echo "PASS control message=20000 bytes delivered whole: $(cat "$OUT/stdout-20000.txt")" | tee -a "$OUT/result.txt"
else
  echo "FAIL control message=20000 bytes not delivered whole: $(cat "$OUT/stdout-20000.txt")" | tee -a "$OUT/result.txt"
fi
for size in 70000; do
  java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro "$size" >"$OUT/stdout-$size.txt" 2>"$OUT/stderr-$size.txt"
  received=$(cat "$OUT/stdout-$size.txt")
  errors=$(grep -c ' ERROR ' "$OUT/stderr-$size.txt" || true)
  warn=$(grep -o 'UDP datagram of [0-9]* bytes exceeds the maximum of [0-9]* bytes and will be dropped' "$OUT/stderr-$size.txt" | head -1 || true)
  cause=$(grep -oE 'java\.[a-z.]*(IOException|SocketException)[^\r]*' "$OUT/stderr-$size.txt" | head -1 || true)
  {
    echo "case message=${size} bytes: $received"
    if [ -n "$warn" ] && [ "$errors" = 0 ]; then
      echo "PASS oversized payload dropped with a warning: \"$warn\", status ERROR lines=$errors"
    else
      echo "FAIL oversized payload not handled: status ERROR lines=$errors, cause=\"${cause:-none}\", drop warning=\"${warn:-none}\""
    fi
    if grep -q 'small-after' "$OUT/stdout-$size.txt"; then
      echo "PASS control: the event after the oversized one is still delivered"
    else
      echo "FAIL control: the event after the oversized one was not delivered"
    fi
  } | tee -a "$OUT/result.txt"
done
