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
run_case() {
  java ${JAVA_OPTS:-} -cp "$CP" Repro "$@" 2>>"$OUT/stderr.txt" | grep -E '^(PASS|FAIL)' | tee -a "$OUT/result.txt"
}
run_case legacy-spring-property logging.auth.encoding ISO-8859-1
run_case control-log4j-property log4j2.configurationAuthorizationEncoding ISO-8859-1
run_case control-default none -
JAVA_OPTS=-Dfile.encoding=ISO-8859-1 run_case default-under-latin1-platform none -
