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
CONVERT_HOST="$PWD/$OUT/converter-host"
DEPLOY_HOST="$PWD/$OUT/deploy-host"
mkdir -p "$CONVERT_HOST" "$DEPLOY_HOST"
java -Drepro.dir="$CONVERT_HOST" -Drepro.threshold=DEBUG -cp "$CP" org.apache.log4j.config.Log4j1ConfigurationConverter \
  -i input/log4j.properties -o "$OUT/converted.xml" 2>"$OUT/converter-stderr.txt"
java -Drepro.dir="$DEPLOY_HOST" -Drepro.threshold=INFO -Dlog4j2.configurationFile="$OUT/converted.xml" \
  -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro 2>"$OUT/app-stderr.txt"
{
  file_attr=$(grep -o 'fileName="[^"]*"' "$OUT/converted.xml" | sed "s#$PWD/##")
  if grep -q 'fileName="${sys:repro.dir}/app.log"' "$OUT/converted.xml"; then
    echo "PASS converted fileName kept as lookup: $file_attr"
  else
    echo "FAIL converted fileName resolved at conversion time: $file_attr"
  fi
  prop=$(grep -o '<Property name="app.dir"[^<]*</Property>' "$OUT/converted.xml" | sed "s#$PWD/##")
  if grep -q '${sys:repro.dir}/data' "$OUT/converted.xml"; then
    echo "PASS converted property kept as lookup: $prop"
  else
    echo "FAIL converted property not translated to a sys lookup: $prop"
  fi
  thr=$(grep -o '<ThresholdFilter[^>]*>' "$OUT/converted.xml")
  if grep -q 'level="${sys:repro.threshold}"' "$OUT/converted.xml"; then
    echo "PASS converted threshold kept as lookup: $thr"
  else
    echo "FAIL converted threshold resolved at conversion time: $thr"
  fi
  if [ -s "$DEPLOY_HOST/app.log" ]; then
    echo "PASS deployed app wrote to its own repro.dir: deploy-host/app.log=$(cat "$DEPLOY_HOST/app.log")"
  else
    echo "FAIL deployed app did not write to its own repro.dir: deploy-host files=[$(ls "$DEPLOY_HOST")] converter-host files=[$(ls "$CONVERT_HOST")]"
  fi
} | tee "$OUT/result.txt"
