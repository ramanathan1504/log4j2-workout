#!/bin/zsh
set -e
cd "$(dirname "$0")"
HERE=$PWD
J17=/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home
export JAVA_HOME=$J17
mvn -q -Plog4j2 compile dependency:build-classpath -Dmdep.outputFile=target/cp-log4j2.txt
mvn -q -Plogback dependency:build-classpath -Dmdep.outputFile=target/cp-logback.txt
OUT=$HERE/../output/boot-4.2.0-strategies.txt; : > $OUT
D1=$(date -v-6d +%Y-%m-%d); D2=$(date -v-5d +%Y-%m-%d); D3=$(date -v-4d +%Y-%m-%d); DY=$(date -v-1d +%Y-%m-%d)
stamp() { date -j -f %Y-%m-%d "$1" +%Y%m%d1200; }
fresh() { W=$HERE/target/work-$1; rm -rf $W; mkdir -p $W/logs; }
seed_days() {
  local base=$1
  for d in $D1 $D2 $D3; do for i in 1 2 3; do head -c 400 /dev/urandom > $base.$d.$i.gz; touch -t $(stamp $d) $base.$d.$i.gz; done; done
}
seed_other() { head -c 3000 /dev/urandom > $1; touch -t $(stamp $D1) $1; }
run() {
  local name=$1 sys=$2 list=$3; shift 3
  echo "\n== $name ($sys, Boot 4.2.0)" | tee -a $OUT
  echo "   args: $*" | sed "s|$W|<work>|g; s|$HERE|<app>|g" | tee -a $OUT
  echo "   before:" | tee -a $OUT
  (cd $W && for d in ${(s:,:)list}; do (cd $d && find . -mindepth 1 \( -type f -o -type d -empty \) | sed -E 's|^\./||; s/\.[0-9]+((\.log)?\.gz)$/.<i>\1/' | sort | uniq -c | sed "s|^|     $d/ |"); done) | tee -a $OUT
  local jvm=() app=()
  for a in "$@"; do if [[ $a == -D* ]]; then jvm+=($a); else app+=($a); fi; done
  (cd $W && $J17/bin/java -cp $HERE/target/classes:$(cat $HERE/target/cp-$sys.txt) -Drepro.list=$list $jvm repro.App --spring.main.banner-mode=off $app 2>&1 \
     | grep -v -E ' INFO .*xxxxxxxx|^\s*$| INFO .*repro.App|INFO .*r.App ' | grep -v -E '^\s+at |^\s+\.\.\. [0-9]+ more|^Caused by' | sed -E "s|$W|<work>|g; s|$HERE|<app>|g; s/^[0-9]{4}-[0-9T:.+-]+ /<ts> /" | awk '/Exception|ERROR/{k=$0;sub(/^<ts> /,"",k);if(c[k]++)next}{print}END{for(k in c)if(c[k]>1)print "   (seen " c[k] " times: " substr(k,1,120) ")"}' | tee -a $OUT)
}
L4=(-Drepro.millis=4000)
LF=(--logging.log4j2.rollingpolicy.max-file-size=1KB --logging.log4j2.rollingpolicy.max-history=2)
BF=(--logging.logback.rollingpolicy.max-file-size=1KB --logging.logback.rollingpolicy.max-history=2)
L5S=(-Drepro.millis=5000)
SEC='logs/app.log.%d{yyyy-MM-dd-HH-mm-ss}.%i.gz'
NEST='logs/archive/%d{yyyy-MM-dd-HH-mm}/app.%d{yyyy-MM-dd-HH-mm-ss}.%i.log.gz'
seed_nested() {
  for d in $D1 $D2 $D3; do mkdir -p $W/logs/archive/$d-12-00; for i in 1 2 3; do f=$W/logs/archive/$d-12-00/app.$d-12-00-00.$i.log.gz; head -c 400 /dev/urandom > $f; touch -t $(stamp $d) $f; done; done
}

fresh T1
run "T1 strategy=time, per-second pattern, max-history=2, Boot XML" log4j2 logs $L5S --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=time --logging.log4j2.rollingpolicy.file-name-pattern=$SEC --logging.log4j2.rollingpolicy.max-history=2

fresh T2
run "T2 strategy=size-and-time, per-second pattern, 1KB, max-history=2, Boot XML" log4j2 logs $L5S --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=size-and-time --logging.log4j2.rollingpolicy.file-name-pattern=$SEC $LF

fresh T3
run "T3 strategy=cron '* * * * * ?', per-second pattern, max-history=2, Boot XML" log4j2 logs $L5S --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=cron '--logging.log4j2.rollingpolicy.cron=* * * * * ?' --logging.log4j2.rollingpolicy.file-name-pattern=$SEC --logging.log4j2.rollingpolicy.max-history=2

fresh TB
run "TB Logback, per-second pattern, max-history=2 (time only: max-file-size 10MB)" logback logs $L5S --logging.file.name=logs/app.log --logging.logback.rollingpolicy.file-name-pattern=$SEC --logging.logback.rollingpolicy.max-history=2

fresh T4
run "T4 strategy=time + fixed cap 5000" log4j2 .,logs $L5S -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=5000 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=time --logging.log4j2.rollingpolicy.file-name-pattern=$SEC --logging.log4j2.rollingpolicy.max-history=2

fresh T5
run "T5 strategy=cron + fixed cap 5000" log4j2 logs $L5S -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=5000 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=cron '--logging.log4j2.rollingpolicy.cron=* * * * * ?' --logging.log4j2.rollingpolicy.file-name-pattern=$SEC --logging.log4j2.rollingpolicy.max-history=2

fresh T6; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $(date -v-1H +%Y%m%d%H%M) $W/logs/app.log
run "T6 strategy=time + OnStartup + fixed cap 2048, previous app.log from 1 h ago, no logging after start" log4j2 logs -DLOG4J2_ROLLINGPOLICY_CLEAN_HISTORY_ON_START=true -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=time --logging.log4j2.rollingpolicy.max-history=2
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT

fresh T7; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $(date -v-1H +%Y%m%d%H%M) $W/logs/app.log
run "T7 strategy=cron + OnStartup + fixed cap 2048, previous app.log from 1 h ago, no logging after start" log4j2 logs -DLOG4J2_ROLLINGPOLICY_CLEAN_HISTORY_ON_START=true -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.strategy=cron --logging.log4j2.rollingpolicy.max-history=2
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT

fresh D1; seed_nested
run "D1 date in folder: $NEST, max-history=2, Boot XML" log4j2 logs $L4 --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.file-name-pattern=$NEST $LF

fresh D2; seed_nested; seed_other $W/logs/other-app.log.1.gz
run "D2 date in folder, stashed XML, logging.file.path=logs, cap 2048" log4j2 logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 --logging.config=file:$HERE/configs/stashed-283b523.xml --logging.file.path=logs --logging.log4j2.rollingpolicy.file-name-pattern=$NEST $LF

fresh D3; seed_nested; seed_other $W/logs/archive/other-app.log.1.gz
run "D3 date in folder, fixed cap: ARCHIVE_DIR=logs/archive, glob=app.*.log.gz at depth 1 (maxDepth=1), cap 2048" log4j2 logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs/archive '-DARCHIVE_GLOB=app.*.log.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.file-name-pattern=$NEST $LF

fresh D4; seed_nested; seed_other $W/logs/archive/other-app.log.1.gz
run "D4 date in folder, fixed cap: ARCHIVE_DIR=logs/archive, glob=*/app.*.log.gz, maxDepth=2, cap 2048" log4j2 logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs/archive '-DARCHIVE_GLOB=*/app.*.log.gz' --logging.config=file:$HERE/configs/fixed-cap-nested.xml --logging.file.name=logs/app.log --logging.log4j2.rollingpolicy.file-name-pattern=$NEST $LF

