#!/bin/zsh
set -e
cd "$(dirname "$0")"
HERE=$PWD
J17=/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home
REL=${1:-2.26.1}
M2=~/.m2/repository/org/apache/logging/log4j
CP=$M2/log4j-api/$REL/log4j-api-$REL.jar:$M2/log4j-core/$REL/log4j-core-$REL.jar
mkdir -p ../output target/classes
$J17/bin/javac -nowarn -cp $CP -d target/classes src/main/java/RollingRepro.java
OUT=$HERE/../output/run-$REL.txt; : > $OUT
fresh() { W=$HERE/target/work-$1; rm -rf $W; mkdir -p $W/logs; }
run() {
  local case=$1 ms=$2 dirs=$3; shift 3
  echo "== case $case, log4j $REL" | tee -a $OUT
  (cd $W && $J17/bin/java -cp $HERE/target/classes:$CP -Dlog4j2.configurationFile=$HERE/src/main/resources/$case.xml -DLOG_FILE=$W/logs/app.log "$@" RollingRepro $ms $dirs 2>&1 | sed "s|$W|<work>|g" | tee -a $OUT)
}
fresh a; run a-max-history 5000 $W/logs -DLOG4J2_ROLLINGPOLICY_MAX_HISTORY=2
fresh b; head -c 3000 /dev/urandom > $W/other-1.gz; head -c 3000 /dev/urandom > $W/other-2.gz; touch -t 202601010000 $W/other-1.gz
run b-stashed-cap 5000 $W,$W/logs -DLOG4J2_ROLLINGPOLICY_MAX_HISTORY=2 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=4096
fresh c; head -c 3000 /dev/urandom > $W/other-1.gz; head -c 3000 /dev/urandom > $W/other-2.gz
run c-fixed-cap 5000 $W,$W/logs -DLOG4J2_ROLLINGPOLICY_MAX_HISTORY=2 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=200 -DARCHIVE_DIR=$W/logs '-DARCHIVE_GLOB=app.log.*.gz'
fresh d; echo "line from the previous run" > $W/logs/app.log; touch -t 202601010000 $W/logs/app.log
run d-clean-on-start 300 $W/logs -DLOG4J2_ROLLINGPOLICY_CLEAN_HISTORY_ON_START=true
