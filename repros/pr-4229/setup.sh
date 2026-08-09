#!/usr/bin/env bash
#
# Plants the symbolic link that PR #4229 is about, and prints the target's
# permissions before and after a bench run.
#
#   ./setup.sh plant     create the outsider file and the link
#   ./setup.sh check     print the outsider's current permissions
#   ./setup.sh clean     remove both
#
# The bench cannot do this itself: it needs a link that already exists in the
# directory PosixViewAttribute walks, before the rollover runs.
#
# POSIX filesystem required. Does nothing useful on a filesystem without POSIX
# permissions, which is what FileUtils.isFilePosixAttributeViewSupported() gates
# the upstream tests on.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# rollover-advanced.xml: <Property name="dir">${sys:bench.log.dir:-logs}/rollover-advanced</Property>
# and <PosixViewAttribute basePath="${dir}/posix" maxDepth="1" ...>
POSIX_DIR="$ROOT/logs/rollover-advanced/posix"

# The action only touches files its PathCondition accepts. rollover-advanced.xml
# uses <IfFileName glob="app-*.log.gz"/>, so the link has to match that glob or
# nothing happens and the run proves nothing.
LINK="$POSIX_DIR/app-99.log.gz"

# Deliberately outside the log tree, so "did the chmod escape the directory?" is
# the question the file answers.
OUTSIDER="${TMPDIR:-/tmp}/log4j-pr-4229-outsider.txt"

perms() { stat -f '%Sp' "$1" 2>/dev/null || stat -c '%A' "$1" 2>/dev/null; }

case "${1:-}" in
  plant)
    mkdir -p "$POSIX_DIR"
    printf 'secret\n' > "$OUTSIDER"
    chmod 600 "$OUTSIDER"
    ln -sfn "$OUTSIDER" "$LINK"
    echo "outsider : $OUTSIDER  $(perms "$OUTSIDER")"
    echo "link     : $LINK -> $OUTSIDER"
    echo
    echo "Expect -rw------- above. Now run the bench, then './setup.sh check'."
    ;;
  check)
    if [[ ! -e "$OUTSIDER" ]]; then
      echo "no outsider file — run './setup.sh plant' first" >&2
      exit 1
    fi
    echo "outsider : $OUTSIDER  $(perms "$OUTSIDER")"
    echo
    echo "  -rw-r-----  the chmod followed the link   -> bug reproduced (pre-#4229)"
    echo "  -rw-------  the target was left alone     -> fixed (post-#4229)"
    ;;
  clean)
    rm -f "$LINK" "$OUTSIDER"
    echo "removed the link and the outsider file"
    ;;
  *)
    sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
