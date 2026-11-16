#!/bin/bash
set -euo pipefail
BASELINE=2.26.1
ARG="${1:?usage: run.sh <log4j version | jars dir> <commons-compress version> [label]}"
CC="${2:?usage: run.sh <log4j version | jars dir> <commons-compress version> [label]}"
L="${3:-$(basename "$ARG")-cc$CC}"
STATUS="${STATUS:-WARN}"
CONFIG="${CONFIG:-log4j2.xml}"
cd "$(dirname "$0")"
OUT="$(pwd)/../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then
  case "$(ls "$ARG"/log4j-core-*.jar)" in *-3.*) RESOLVE=3.0.0-beta3 ;; *) RESOLVE="$BASELINE" ;; esac
else
  RESOLVE="$ARG"
fi
MVNARGS=(-Dlog4j.version="$RESOLVE" -Dcommons-compress.version="$CC")
JVMARGS=(-Dlog4j2.configurationFile="$CONFIG")
if [[ "$RESOLVE" == 3.* ]]; then
  MVNARGS+=(-Dl4j3 -Dlog4j.api.version=2.24.3)
  JVMARGS=(-Dlog4j.configuration.location="$CONFIG")
fi
mvn -q "${MVNARGS[@]}" clean package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
tr ':' '\n' <<<"$CP" | grep -E 'log4j-|commons-compress|zstd' | xargs -n1 basename > "$OUT/classpath.txt"
java "${JVMARGS[@]}" -Dlogdir="$OUT" -Dlog4j2.StatusLogger.level="$STATUS" -Dlog4j.statusLoggerLevel="$STATUS" -cp "$CP" Repro "$OUT" 2>"$OUT/status.txt" | tee "$OUT/result.txt"
