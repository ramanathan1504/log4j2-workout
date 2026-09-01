#!/usr/bin/env bash
# Run this reproduction against one Log4j version.
#   ./run.sh            # the version pinned in pom.xml
#   ./run.sh 2.24.1     # any other version
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-2.26.1}"
API_VERSION="$VERSION"
case "$VERSION" in
  3.*) API_VERSION=2.24.3 ;;
esac
mvn -q -Dlog4j.version="$VERSION" -Dlog4j.api.version="$API_VERSION" compile exec:exec
