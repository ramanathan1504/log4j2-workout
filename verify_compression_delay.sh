#!/bin/bash
# verify_compression_delay.sh - Test if maxCompressionDelaySeconds is working
#
# This script:
# 1. Cleans the logs directory
# 2. Generates log events that trigger RollingFile rollover
# 3. Monitors .log and .log.gz files to verify the delay is working
# 4. Displays timestamp differences to confirm compression delay occurred
#
# Usage:
#   ./verify_compression_delay.sh [log4j_version]
#   # Examples:
#   ./verify_compression_delay.sh 3.0.0-SNAPSHOT    (uses local build)
#   ./verify_compression_delay.sh 2.25.3              (uses released version)
#   ./verify_compression_delay.sh                     (uses default from pom.xml)

set -e

WORKSPACE="/Users/ramanathan/canonical/log4j2-workout"
LOGDIR="$WORKSPACE/core-java-test/logs"
LOG4J_VERSION="${1:-}"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Log4j2 Compression Delay Feature Test             ║${NC}"
echo -e "${BLUE}║  Issue #4012: Defer compression to reduce IO spike ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"

cd "$WORKSPACE"

# Clean logs
echo -e "${YELLOW}[1/4] Cleaning logs directory...${NC}"
rm -rf "$LOGDIR"
mkdir -p "$LOGDIR"
echo -e "${GREEN}✓ Cleaned: $LOGDIR${NC}"

# Build with specified version
if [ -n "$LOG4J_VERSION" ]; then
  if [ "$LOG4J_VERSION" = "3.0.0-SNAPSHOT" ]; then
    echo -e "${YELLOW}[2/4] Building with Log4j 3.0.0-SNAPSHOT (local build)...${NC}"
    mvn clean package -pl core-java-test -am -Plog4j-snapshot -U 2>&1 | grep -E "Building|SUCCESS|FAILURE"
  else
    echo -e "${YELLOW}[2/4] Building with Log4j version $LOG4J_VERSION...${NC}"
    mvn clean package -pl core-java-test -am -Dlog4j.version="$LOG4J_VERSION" 2>&1 | grep -E "Building|SUCCESS|FAILURE"
  fi
else
  echo -e "${YELLOW}[2/4] Building with default Log4j version from pom.xml...${NC}"
  mvn clean package -pl core-java-test -am 2>&1 | grep -E "Building|SUCCESS|FAILURE"
fi

# Generate logs (creates rollovers)
echo -e "${YELLOW}[3/4] Generating log events (triggers RollingFile rollovers)...${NC}"
mvn exec:java -pl core-java-test 2>&1 | grep -E "Log event|Starting" | head -5

# Allow time for compression to complete
echo -e "${YELLOW}[3b/4] Waiting 5 seconds for compression task to start/complete...${NC}"
sleep 5

# Analyze results
echo -e "${YELLOW}[4/4] Analyzing results...${NC}"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
echo -e "${BLUE} Log Files in: $LOGDIR${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"

# Get list of log files sorted by modification time
echo ""
echo -e "File listing (sorted by modification time, newest first):"
ls -lhrt "$LOGDIR"/*.log* 2>/dev/null | awk '{print $6, $7, $8, $9}' | tail -20

# Detailed analysis
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
echo -e "${BLUE} Compression Delay Detection${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"

# Check for both .gz and .log files
if ls "$LOGDIR"/*.log.gz 1>/dev/null 2>&1; then
  NEWEST_GZ=$(ls -t "$LOGDIR"/*.log.gz 2>/dev/null | head -1)
  NEWEST_LOG=$(ls -t "$LOGDIR"/*.log 2>/dev/null | head -1)

  if [ -n "$NEWEST_GZ" ]; then
    GZ_MTIME=$(stat -f%m "$NEWEST_GZ" 2>/dev/null || stat -c%Y "$NEWEST_GZ")
    echo -e "${GREEN}✓ Compressed file found: $(basename $NEWEST_GZ)${NC}"
    echo "  Modified: $(date -r $GZ_MTIME '+%Y-%m-%d %H:%M:%S')"
  fi

  if [ -n "$NEWEST_LOG" ] && [[ ! "$NEWEST_LOG" =~ \.gz$ ]]; then
    LOG_MTIME=$(stat -f%m "$NEWEST_LOG" 2>/dev/null || stat -c%Y "$NEWEST_LOG")
    echo -e "${GREEN}✓ Uncompressed file found: $(basename $NEWEST_LOG)${NC}"
    echo "  Modified: $(date -r $LOG_MTIME '+%Y-%m-%d %H:%M:%S')"

    # Calculate time difference
    DIFF=$((LOG_MTIME - GZ_MTIME))
    if [ $DIFF -gt 0 ]; then
      echo ""
      echo -e "${GREEN}✓ COMPRESSION DELAY DETECTED: +${DIFF} seconds${NC}"
      echo "  (Uncompressed file is newer than .gz file)"
      echo "  This confirms maxCompressionDelaySeconds is working!"
    fi
  fi
else
  echo -e "${RED}✗ No .gz files found in $LOGDIR${NC}"
  echo "  Compression may not have triggered yet."
  echo "  This could mean:"
  echo "    1. maxCompressionDelaySeconds delay hasn't elapsed"
  echo "    2. Conflicting config (createOnDemand or other settings)"
  echo "    3. Log level too high (try TRACE level)"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
echo -e "${BLUE} Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
echo ""
echo "Configuration file used:"
echo "  $WORKSPACE/core-java-test/src/main/resources/log4j2.xml"
echo ""
echo "Key attributes tested:"
echo "  <TimeBasedTriggeringPolicy interval=\"1\" />          (rollover every minute)"
echo "  <SizeBasedTriggeringPolicy size=\"1KB\" />            (also rollover at 1KB)"
echo "  <DefaultRolloverStrategy max=\"20\" maxCompressionDelaySeconds=\"3\" />"
echo ""
echo "What to look for:"
echo "  ✓ .log.gz files = compression happened"
echo "  ✓ .log files (uncompressed) = most recent NOT yet compressed"
echo "  ✓ Time difference = confirms delay is working"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"

