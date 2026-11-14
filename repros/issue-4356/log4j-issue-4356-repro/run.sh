#!/usr/bin/env bash
# Run this reproduction against one Log4j and one LMAX Disruptor version.
#   ./run.sh                  # 2.25.3 + Disruptor 4.0.0
#   ./run.sh 2.25.3 3.4.4     # any other pair
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-2.25.3}"
DISRUPTOR="${2:-4.0.0}"
mvn -q -Dlog4j.version="$VERSION" -Ddisruptor.version="$DISRUPTOR" compile exec:exec
