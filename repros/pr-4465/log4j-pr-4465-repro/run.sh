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
for MODE in default warn ignore; do
  if [ "$MODE" = default ]; then PROP=""; else PROP="-Dlog4j2.julMutatorAccess=$MODE"; fi
  java -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -Dlog4j2.StatusLogger.level=WARN $PROP -cp "$CP" Repro 2>"$OUT/status-$MODE.txt" | sed "s/^/[$MODE] /" | tee -a "$OUT/result.txt"
  N=$(grep -c 'Ignoring call to `j.u.l.Logger' "$OUT/status-$MODE.txt" || true)
  C=$(grep -c 'from `Repro' "$OUT/status-$MODE.txt" || true)
  if [ "$MODE" = default ]; then
    [ "$N" = 1 ] && V=PASS || V=FAIL
    echo "$V [$MODE] 8 mutator calls on 2 loggers -> warnings=$N (expected 1)" | tee -a "$OUT/result.txt"
    [ "$C" -ge 1 ] && V=PASS || V=FAIL
    echo "$V [$MODE] warning names the calling class Repro: count=$C" | tee -a "$OUT/result.txt"
  elif [ "$MODE" = warn ]; then
    [ "$N" = 8 ] && V=PASS || V=FAIL
    echo "$V [$MODE] 8 mutator calls -> warnings=$N (expected 8)" | tee -a "$OUT/result.txt"
  else
    [ "$N" = 0 ] && V=PASS || V=FAIL
    echo "$V [$MODE] 8 mutator calls -> warnings=$N (expected 0)" | tee -a "$OUT/result.txt"
  fi
done
