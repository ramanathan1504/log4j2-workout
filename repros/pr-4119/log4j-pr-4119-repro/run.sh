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
: > "$OUT/result.txt"
verdict() {
  local c="$1" ok="$2" text="$3"
  if [ "$ok" = 1 ]; then echo "PASS $c $text" >> "$OUT/result.txt"; else echo "FAIL $c $text" >> "$OUT/result.txt"; fi
}
for c in inline attr element noregex api; do
  mkdir -p "$OUT/$c"
  java -Dout="$OUT/$c" -Dlog4j2.configurationFile="src/main/resources/$c.xml" -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro "$c" > "$OUT/$c/stdout.txt" 2>&1 || true
  R=$(grep '^RESULT' "$OUT/$c/stdout.txt" || echo "RESULT case=$c no-result")
  ST=$(grep -E ' (ERROR|WARN) ' "$OUT/$c/stdout.txt" | sed -E 's/^[^ ]+ [^ ]+ //' | sort -u | paste -sd'|' - || true)
  case "$c" in
    inline)  [[ "$R" == *"multiLineEventKept=true"* ]] && ok=1 || ok=0; verdict "$c" $ok "(control: (?s) inline flag keeps multi-line event) observed: ${R#RESULT } status=[$ST]" ;;
    attr|element) [[ "$ST" == *"invalid element or attribute"* ]] && ok=1 || ok=0; verdict "$c" $ok "(patternFlags from config is rejected) observed: ${R#RESULT } status=[$ST]" ;;
    noregex) [[ -n "$ST" ]] && ok=1 || ok=0; verdict "$c" $ok "(missing regex reported) observed: ${R#RESULT } status=[$ST]" ;;
    api)     [[ "$R" == *"-> DENY"* ]] && ok=1 || ok=0; verdict "$c" $ok "(PR contract: patternFlags ignored, so DENY) observed: ${R#RESULT }" ;;
  esac
done
cat "$OUT/result.txt"
