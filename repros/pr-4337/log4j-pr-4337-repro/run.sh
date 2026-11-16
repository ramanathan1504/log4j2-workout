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
TLS="$(pwd)/target/tls"
rm -rf "$TLS"; mkdir -p "$TLS"
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 2 -dname CN=127.0.0.1 -ext SAN=ip:127.0.0.1 \
  -storetype PKCS12 -keystore "$TLS/server.p12" -storepass changeit -keypass changeit >/dev/null 2>&1
: > "$OUT/result.txt"
java -Dtlsdir="$TLS" -Djavax.net.ssl.trustStore="$TLS/server.p12" -Djavax.net.ssl.trustStorePassword=changeit \
  -Djavax.net.ssl.trustStoreType=PKCS12 -Dlog4j2.StatusLogger.level=WARN -cp "$CP" Repro \
  >"$OUT/stdout.txt" 2>"$OUT/status.txt" || echo "exit=$?" >> "$OUT/result.txt"
cat "$OUT/stdout.txt" >> "$OUT/result.txt"
grep -E 'ERROR|WARN' "$OUT/status.txt" | grep -vE '^\s+at ' | cut -c1-240 | sort -u | head -8 | sed 's/^/status: /' >> "$OUT/result.txt" || true
cat "$OUT/result.txt"
