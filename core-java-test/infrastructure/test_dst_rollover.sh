#!/bin/bash
# test_dst_rollover.sh
# Script to test Log4j2 CronTriggeringPolicy DST rollover issue in core-java-test
# Usage: sudo ./test_dst_rollover.sh [minutes]
# Default: 30 minutes

set -e

MINUTES=${1:-30}

# 1. Set timezone to Australia/Sydney for this shell
export TZ=Australia/Sydney

echo "[INFO] Timezone set to: $TZ"

echo "[INFO] Current system date: $(date)"
echo "[INFO] Setting system date to April 4, 2026, 23:59 (one minute before DST ends in Sydney)"
sudo date 040423592026

echo "[INFO] System date after change: $(date)"

# 2. Clean logs directory
# Change to workspace root (assumes script is in core-java-test/infrastructure/)
cd "$(dirname "$0")/../.."
LOGDIR="core-java-test/logs"
rm -rf "$LOGDIR" && mkdir -p "$LOGDIR"
echo "[INFO] Cleaned logs directory: $LOGDIR"

# 3. Run the cron-test mode for the specified duration
#    -Duser.timezone ensures JVM uses correct timezone
#    -Dexec.args="cron-test $MINUTES" runs the periodic logger

echo "[INFO] Starting cron-test for $MINUTES minutes (will log every 10 seconds)"
mvn exec:java -pl core-java-test -Dexec.args="cron-test $MINUTES" -Duser.timezone=Australia/Sydney

# 4. Show log files after test
ls -lh "$LOGDIR"
echo "[INFO] Test complete. Check the log files for rollover and DST issues."

echo "[INFO] Restore your system clock after testing!"
echo "  sudo sntp -sS time.apple.com"
