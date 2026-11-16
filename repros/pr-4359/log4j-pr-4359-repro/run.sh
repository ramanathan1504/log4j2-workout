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
STORE="target/truststore.p12"
rm -f "$STORE"
keytool -genkeypair -alias repro -keyalg RSA -keysize 2048 -dname CN=repro -validity 2 \
  -storetype PKCS12 -keystore target/source.p12 -storepass changeit -keypass changeit >/dev/null 2>&1
keytool -exportcert -alias repro -keystore target/source.p12 -storepass changeit -file target/repro.cer >/dev/null 2>&1
keytool -importcert -noprompt -alias repro -file target/repro.cer -storetype PKCS12 -keystore "$STORE" -storepass changeit >/dev/null 2>&1
rm -f target/source.p12
java -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro "$STORE" 2>"$OUT/status.txt" | tee "$OUT/result.txt"
echo "version=$L $(grep -c . "$OUT/status.txt" || true) status lines" | tee -a "$OUT/result.txt"
