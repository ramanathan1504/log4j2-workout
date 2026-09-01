#!/usr/bin/env bash
# Run this reproduction against one Log4j version.
#   ./run.sh                     # the version pinned in pom.xml
#   ./run.sh 2.24.1              # any other version
#   ./run.sh 2.26.1 CACHED       # any ThreadNameCachingStrategy
#   ./run.sh 2.26.1 UNCACHED false   # and with thread-locals disabled
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-2.26.1}"
STRATEGY="${2:-UNCACHED}"
THREADLOCALS="${3:-true}"
mvn -q \
  -Dlog4j.version="$VERSION" \
  -DthreadNameStrategy="$STRATEGY" \
  -DenableThreadlocals="$THREADLOCALS" \
  compile exec:exec
