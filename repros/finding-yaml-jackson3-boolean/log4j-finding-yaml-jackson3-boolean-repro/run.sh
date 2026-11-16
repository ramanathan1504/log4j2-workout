#!/bin/bash
set -euo pipefail
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
if [ -d "$ARG" ]; then
  if ls "$ARG"/log4j-core-3.*.jar >/dev/null 2>&1; then LINE=3; RESOLVE=3.0.0-beta3; else LINE=2; RESOLVE=2.26.1; fi
else
  RESOLVE="$ARG"; if [[ "$ARG" == 3.* ]]; then LINE=3; else LINE=2; fi
fi
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
mvn -q -Dline="$LINE" -Dlog4j.version="$RESOLVE" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes"
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+.*\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
if [ -d "$ARG" ] && [ "$LINE" = 3 ]; then
  for a in log4j-kit log4j-plugins log4j-config-jackson; do
    if ! tr ':' '\n' <<<"$CP" | grep -q "/$a-[0-9]"; then CP="$CP:$(ls "$ARG"/"$a"-[0-9]*.jar | head -1)"; fi
  done
fi
OUTDIR="$(cd "$OUT" && pwd)"
JACKSON3_ONLY="$(tr ':' '\n' <<<"$CP" | grep -vE '/com/fasterxml/jackson/(core/jackson-(core|databind)|dataformat)/|/org/yaml/snakeyaml/' | paste -sd: -)"
JACKSON2_ONLY="$(tr ':' '\n' <<<"$CP" | grep -vE '/tools/jackson/|/org/snakeyaml/snakeyaml-engine/' | paste -sd: -)"
if [ "$LINE" = 3 ]; then
  VARIANTS="classpath"; CONF="-Dlog4j.configuration.location=target/classes/log4j2.yaml"
else
  VARIANTS="jackson2-only jackson3-only jackson2+jackson3"; CONF="-Dlog4j2.configurationFile=target/classes/log4j2.yaml"
fi
: > "$OUT/result.txt"
echo "version=$L java=$(java -version 2>&1 | head -1)" >> "$OUT/result.txt"
for variant in $VARIANTS; do
  case "$variant" in
    jackson2-only) RUNCP="$JACKSON2_ONLY" ;;
    jackson3-only) RUNCP="$JACKSON3_ONLY" ;;
    *) RUNCP="$CP" ;;
  esac
  VOUT="$OUTDIR/$variant"; mkdir -p "$VOUT"
  java -Doutdir="$VOUT" "$CONF" -Dlog4j2.StatusLogger.level=WARN -cp "$RUNCP" Repro "$variant" >"$VOUT/stdout.txt" 2>"$VOUT/status.txt" || true
  { echo "[$variant] jars: $(tr ':' '\n' <<<"$RUNCP" | grep -E 'log4j-(core|config-yaml|config-jackson)|jackson-(core|databind|dataformat-yaml)|snakeyaml' | xargs -n1 basename | tr '\n' ' ')"
    cat "$VOUT/stdout.txt"; echo "[$variant] status-logger:"; head -5 "$VOUT/status.txt" | cut -c1-240 | sed 's/^/  /'; } >> "$OUT/result.txt"
  rm -f "$VOUT/stdout.txt"
done
cat "$OUT/result.txt"
