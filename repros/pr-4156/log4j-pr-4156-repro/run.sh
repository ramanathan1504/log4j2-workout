#!/bin/bash
set -euo pipefail
BASELINE=3.0.0-beta3
ARG="${1:?usage: run.sh <log4j version | jars dir> [label]}"
L="${2:-$(basename "$ARG")}"
cd "$(dirname "$0")"
OUT="../output/$L"; rm -rf "$OUT"; mkdir -p "$OUT"
if [ -d "$ARG" ]; then RESOLVE="$BASELINE"; else RESOLVE="$ARG"; fi
mvn -q -Dlog4j.version="$RESOLVE" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP=""
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][0-9.]*(-[A-Za-z0-9]+)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then CP="$CP:$r"; else CP="$CP:$e"; fi
done
CP="${CP#:}"
CLASSES="$OUT/classes"; mkdir -p "$CLASSES"
set +e
javac -Xlint:none -cp "$CP" -processorpath "$CP" -d "$CLASSES" src/main/java/example/ReproLayout.java > "$OUT/javac.txt" 2>&1
RC=$?
set -e
INDEX=$(find "$CLASSES" -path '*META-INF/services/org.apache.logging.log4j.plugins.model.PluginService' | wc -l | tr -d ' ')
GENERATED=$(find "$CLASSES" -name '*.class' -path '*plugins*' | wc -l | tr -d ' ')
flagged() { grep -q "The field \`$1\` does not have a public setter" "$OUT/javac.txt"; }
{
  if [ "$GENERATED" -gt 0 ] || [ "$INDEX" -gt 0 ] || [ "$RC" -ne 0 ]; then echo "PASS processor-ran-control javac-exit=$RC plugin-index-files=$INDEX generated-classes=$GENERATED"; else echo "FAIL processor-ran-control javac-exit=$RC plugin-index-files=$INDEX generated-classes=$GENERATED"; fi
  if flagged fieldWithoutSetter; then echo "PASS field-without-setter-reported javac-exit=$RC"; else echo "FAIL field-without-setter-reported javac-exit=$RC"; fi
  if flagged prefix; then echo "FAIL field-with-setter-not-reported-control flagged"; else echo "PASS field-with-setter-not-reported-control not-flagged"; fi
  if flagged suppressedFieldWithoutSetter; then echo "FAIL suppressed-field-not-reported-control flagged"; else echo "PASS suppressed-field-not-reported-control not-flagged"; fi
  echo "javac-errors: $(grep -c 'error:' "$OUT/javac.txt" || true)"
  grep 'error:' "$OUT/javac.txt" | sed -E 's#^.*/src/#src/#' || true
  echo "classpath-log4j: $(tr ':' '\n' <<< "$CP" | grep -o 'log4j-[a-z-]*-[0-9][^/]*' | tr '\n' ' ')"
} | tee "$OUT/result.txt"
rm -rf "$CLASSES"
