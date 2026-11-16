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
KEYS="$OUT/keys"; mkdir -p "$KEYS"
make_cert() {
  keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 2 -storetype PKCS12 \
    -keystore "$KEYS/$1.p12" -storepass changeit -keypass changeit -dname "CN=$2" -ext "SAN=$3" >/dev/null 2>&1
  keytool -exportcert -alias server -keystore "$KEYS/$1.p12" -storepass changeit -file "$KEYS/$1.cer" >/dev/null 2>&1
  keytool -importcert -noprompt -alias "$1" -file "$KEYS/$1.cer" -keystore "$KEYS/trust.p12" -storetype PKCS12 -storepass changeit >/dev/null 2>&1
}
make_cert wrong-host wrong-host.invalid dns:wrong-host.invalid
make_cert localhost-name localhost dns:localhost
make_cert ip-only 127.0.0.1 ip:127.0.0.1

run_case() {
  local label="$1" verify="$2" cert="$3" expected="$4"
  local line
  line=$(java -Djavax.net.ssl.trustStore="$KEYS/trust.p12" -Djavax.net.ssl.trustStorePassword=changeit \
    -Djavax.net.ssl.trustStoreType=PKCS12 -Dlog4j2.sslVerifyHostName="$verify" \
    -cp "$CP" Repro "$KEYS/$cert.p12" 2>>"$OUT/stderr.txt" | grep handshakes)
  local done refused
  done=$(sed -E 's/.*handshakesCompleted=([0-9]+).*/\1/' <<<"$line")
  refused=$(sed -E 's/.*handshakesRefused=([0-9]+).*/\1/' <<<"$line")
  local observed
  if [ "$done" -gt 0 ]; then observed=accepted; elif [ "$refused" -gt 0 ]; then observed=refused; else observed=no-connection; fi
  if [ "$observed" = "$expected" ]; then v=PASS; else v=FAIL; fi
  echo "$v $label expected=$expected observed=$observed ($line)" >> "$OUT/result.txt"
}
: > "$OUT/result.txt"
run_case "sslVerifyHostName=true, cert for wrong-host.invalid" true wrong-host refused
run_case "control: sslVerifyHostName=false, cert for wrong-host.invalid" false wrong-host accepted
run_case "control: sslVerifyHostName=true, cert for dns:localhost" true localhost-name accepted
run_case "sslVerifyHostName=true, cert with only ip:127.0.0.1, host configured as 127.0.0.1" true ip-only accepted
rm -rf "$KEYS"
cat "$OUT/result.txt"
