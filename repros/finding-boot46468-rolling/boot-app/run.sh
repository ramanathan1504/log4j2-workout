#!/bin/zsh
set -e
cd "$(dirname "$0")"
HERE=$PWD
J17=/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home
export JAVA_HOME=$J17
mvn -q -Plog4j2 compile dependency:build-classpath -Dmdep.outputFile=target/cp-log4j2.txt
mvn -q -Plogback dependency:build-classpath -Dmdep.outputFile=target/cp-logback.txt
OUT=$HERE/../output/boot-4.2.0-run.txt; : > $OUT
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

fresh L1; seed_days $W/logs/app.log
run "L1 Boot default log4j2-file.xml, max-history=2, old days present" log4j2 logs $L4 --logging.file.name=logs/app.log $LF

fresh B1; seed_days $W/logs/app.log
run "B1 Boot default Logback, max-history=2, old days present" logback logs $L4 --logging.file.name=logs/app.log $BF

fresh L2a; seed_days $W/logs/app.log; seed_other $W/other-1.gz; seed_other $W/other-2.gz
run "L2a stashed 283b523 XML, logging.file.name=logs/app.log, cap 2048" log4j2 .,logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 --logging.config=file:$HERE/configs/stashed-283b523.xml --logging.file.name=logs/app.log $LF

fresh L2b; seed_days $W/app.log; seed_other $W/other-1.gz
run "L2b stashed 283b523 XML, logging.file.name=app.log (working dir), cap 2048" log4j2 . $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 --logging.config=file:$HERE/configs/stashed-283b523.xml --logging.file.name=app.log $LF

fresh L2c; seed_days $W/logs/spring.log; seed_other $W/logs/other-app.log.1.gz; seed_other $W/other-1.gz
run "L2c stashed 283b523 XML, logging.file.path=logs, cap 2048" log4j2 .,logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 --logging.config=file:$HERE/configs/stashed-283b523.xml --logging.file.path=logs $LF

fresh L3; seed_days $W/logs/app.log; seed_other $W/logs/other-app.log.1.gz; seed_other $W/other-1.gz
run "L3 cap with basePath=archive folder, glob=app.log.*.gz, cap 2048" log4j2 .,logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log $LF

fresh L4; seed_days $W/logs/app.log
run "L4 XML-only folder: basePath=\${sys:LOG_FILE}/.., cap 2048" log4j2 logs $L4 -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 --logging.config=file:$HERE/configs/xml-only-parent.xml --logging.file.name=logs/app.log $LF

fresh L5; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $(stamp $DY) $W/logs/app.log
run "L5 clean-history-on-start via OnStartupTriggeringPolicy + cap 2048, old app.log present, no logging after start" log4j2 logs -DLOG4J2_ROLLINGPOLICY_CLEAN_HISTORY_ON_START=true -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log $LF
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT

fresh L6; seed_days $W/logs/app.log
run "L6 same as L5 but no app.log from a previous run" log4j2 logs -DLOG4J2_ROLLINGPOLICY_CLEAN_HISTORY_ON_START=true -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log $LF

fresh B5; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $(stamp $DY) $W/logs/app.log
run "B5 Logback clean-history-on-start=true, max-history=2, old app.log present, no logging after start" logback logs --logging.logback.rollingpolicy.clean-history-on-start=true --logging.file.name=logs/app.log $BF
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT

TH=$(date -v-1H +%Y%m%d%H%M)
fresh L7; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $TH $W/logs/app.log
run "L7 as L5, but app.log from the previous run was written earlier today" log4j2 logs -DLOG4J2_ROLLINGPOLICY_CLEAN_HISTORY_ON_START=true -DLOG4J2_ROLLINGPOLICY_TOTAL_SIZE_CAP=2048 -DARCHIVE_DIR=logs '-DARCHIVE_GLOB=app.log.*.gz' --logging.config=file:$HERE/configs/fixed-cap.xml --logging.file.name=logs/app.log $LF
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT

fresh B7; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $TH $W/logs/app.log
run "B7 Logback clean-history-on-start=true, max-history=2, app.log from the previous run written earlier today" logback logs --logging.logback.rollingpolicy.clean-history-on-start=true --logging.file.name=logs/app.log $BF
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT

fresh B8; seed_days $W/logs/app.log; echo "line from the previous run" > $W/logs/app.log; touch -t $TH $W/logs/app.log
run "B8 control: as B7 with clean-history-on-start=false" logback logs --logging.file.name=logs/app.log $BF
echo "   first line of logs/app.log: $(head -1 $W/logs/app.log | cut -c1-60)" | tee -a $OUT
