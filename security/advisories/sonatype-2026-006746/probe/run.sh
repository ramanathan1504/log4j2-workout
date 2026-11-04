#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ "$#" -eq 0 ]; then
  set -- 2.26.1 2.26.2-SNAPSHOT
fi
if [ -n "${PROBE_JVM_ARGS:-}" ]; then
  export JAVA_TOOL_OPTIONS="$PROBE_JVM_ARGS"
fi
for version in "$@"; do
  echo "== log4j $version ${PROBE_JVM_ARGS:-}"
  mvn -q -Dlog4j.version="$version" compile exec:exec 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'
done
