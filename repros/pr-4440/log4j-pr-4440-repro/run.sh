#!/bin/bash
set -euo pipefail
V="${1:?usage: run.sh <log4j version> [label]}"
L="${2:-$V}"
cd "$(dirname "$0")"
OUT="../output/$L"
rm -rf "$OUT"; mkdir -p "$OUT"
mvn -q -Dlog4j.version="$V" package dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes:$(cat target/cp.txt)"

wait_second() {
  while [ "$(date +%S | sed 's/^0//')" != "$1" ]; do sleep 0.2; done
}

app() {
  java -Dlogdir="$1" -Dlog4j2.debug=false -cp "$CP" Repro "$2" "$3" | tee -a "$1/../expected.txt"
}

count() {
  local dir="$1" name="$2" expected found
  expected=$(awk -F'lines=' '{s+=$2} END {print s}' "$dir/../expected.txt")
  found=$(cat "$dir"/app.log* | grep -oE 'run=[A-Z0-9]+ seq=[0-9]+' | sort -u | wc -l | tr -d ' ')
  echo "$name version=$L logged=$expected found=$found lost=$((expected - found))" | tee -a "$OUT/result.txt"
  ( cd "$dir" && for f in app.log*; do echo "  $f $(wc -l < "$f") lines, first: $(head -1 "$f" | cut -c1-40)"; done ) | tee -a "$OUT/result.txt"
}

S1="$OUT/s1-restart-same-period/logs"; mkdir -p "$S1"
wait_second 50
app "$S1" S1A 20
sleep 3
app "$S1" S1B 62
count "$S1" "S1 #4439 restart in the same period"

S2="$OUT/s2-missed-fire/logs"; mkdir -p "$S2"
wait_second 50
app "$S2" S2A 20
wait_second 8
app "$S2" S2B 62
count "$S2" "S2 #4449 missed fire, file created by a scheduled roll"
