#!/usr/bin/env bash
# Run this reproduction against one Log4j version.
#   ./run.sh              # the version pinned in pom.xml
#   ./run.sh 2.26.1       # any other version
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-}"
if [[ -n "$VERSION" ]]; then
  mvn -q -Dlog4j.version="$VERSION" compile exec:java
else
  mvn -q compile exec:java
fi
