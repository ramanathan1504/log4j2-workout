#!/bin/bash
set -euo pipefail
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then
  if ls "$ARG"/log4j-core-3.*.jar >/dev/null 2>&1; then RESOLVE=3.0.0-beta3; else RESOLVE=2.26.1; fi
else
  RESOLVE="$ARG"
fi
if [[ "$RESOLVE" == 3.* ]]; then
  MVN_EXTRA=(-Dlog4j.api.version=2.24.3 -Dsrc.line=none3x)
  JPROPS=(-Dlog4j.configuration.location=log4j2.xml -Dlog4j.statusLogger.level=WARN)
else
  MVN_EXTRA=()
  JPROPS=(-Dlog4j2.configurationFile=log4j2.xml -Dlog4j2.clock=ScriptedClock -Dlog4j2.StatusLogger.level=WARN)
fi
rm -rf target
mvn -q -Dlog4j.version="$RESOLVE" "${MVN_EXTRA[@]+"${MVN_EXTRA[@]}"}" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
echo "$CP" | tr ':' '\n' | grep log4j > "$OUT/classpath.txt"
java -Duser.language=en -Duser.country=US -Duser.timezone=UTC "${JPROPS[@]}" ${EXTRA_JVM_ARGS:-} -Dlogdir="$OUT" -cp "$CP" Repro "$OUT" 2>"$OUT/status.txt" | tee "$OUT/result.txt"
