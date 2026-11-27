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
CORE=""
for e in $(tr ':' ' ' < target/cp.txt); do
  a=$(basename "$e" | sed -E 's/-[0-9][^-]*(-SNAPSHOT)?\.jar$//')
  if [ -d "$ARG" ] && [[ "$a" == log4j-* ]] && r=$(ls "$ARG"/"$a"-[0-9]*.jar 2>/dev/null | head -1) && [ -n "$r" ]; then e="$r"; fi
  CP="$CP:$e"
  [ "$a" = log4j-core ] && CORE="$e"
done
: > "$OUT/result.txt"
mkdir -p "$OUT/builder"
java -Dout="$OUT/builder" -Dlog4j2.configurationFile=src/main/resources/builder.xml -cp "$CP" repro.Repro > "$OUT/builder/stdout.txt" 2>&1 || true
R=$(grep '^RESULT' "$OUT/builder/stdout.txt" || echo "RESULT no-result")
ST=$(grep -E '(ERROR|WARN)' "$OUT/builder/stdout.txt" | grep -oE '(InaccessibleObjectException|Could not create plugin of type [^ ]+|Unable to invoke factory method[^:]*)' | sort -u | paste -sd'|' - || true)
if [[ "$R" == *"keep=true drop=false"* ]]; then V=PASS; else V=FAIL; fi
echo "$V builder (Builder extends AtomicInteger: filter built, drop event denied) observed: ${R#RESULT } status=[$ST]" >> "$OUT/result.txt"
F=$(unzip -l "$CORE" | grep -o 'META-INF/native-image/log4j-generated/.*reflect-config.json' | head -1)
unzip -p "$CORE" "$F" > "$OUT/reflect-config.json"
python3 -I - "$OUT/reflect-config.json" >> "$OUT/result.txt" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
fields = {e["name"]: [f["name"] for f in e.get("fields", [])] for e in d}
total = sum(len(v) for k, v in fields.items() if "Builder" in k)
print("INFO metadata builder fields registered: %d" % total)
for k in ("org.apache.logging.log4j.core.appender.SocketAppender$Builder", "org.apache.logging.log4j.core.net.ssl.SslConfiguration", "org.apache.logging.log4j.core.appender.SyslogAppender$Builder"):
    print("INFO metadata %s %s" % (k, sorted(fields.get(k, []))))
PY
cat "$OUT/result.txt"
