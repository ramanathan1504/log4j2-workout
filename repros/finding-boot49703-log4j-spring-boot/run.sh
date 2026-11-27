#!/bin/zsh
cd "$(dirname "$0")/app"
export JAVA_HOME=/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home
OUT=../output/run.txt; : > $OUT
for v in 2.7.18 3.5.16 4.0.3 4.0.4 4.2.0; do
  for mode in without with; do
    P=""; [[ $mode == with ]] && P="-Pwith"
    mvn -q -f pom-$v.xml $P clean compile dependency:build-classpath -Dmdep.outputFile=target-$v/cp.txt >/dev/null 2>&1 || { echo "== Boot $v $mode log4j-spring-boot: BUILD FAILED" | tee -a $OUT; continue; }
    for prof in default dev; do
      echo "== Boot $v, $mode log4j-spring-boot, profile=$prof, classpath run" | tee -a $OUT
      $JAVA_HOME/bin/java -cp target-$v/classes:$(cat target-$v/cp.txt) repro.App --spring.profiles.active=$prof 2>&1 | grep -E 'RESULT|ERROR|WARN|Exception' | grep -v '^\s*at ' | sed -E 's/^[0-9T:.Z-]+ //' | awk '!seen[$0]++' | head -14 | tee -a $OUT
    done
  done
done
for v in 4.0.4 4.2.0; do
  mvn -q -f pom-$v.xml -Pwith clean package -DskipTests >/dev/null 2>&1
  echo "== Boot $v, with log4j-spring-boot, fat jar (java -jar)" | tee -a $OUT
  $JAVA_HOME/bin/java -jar target-$v/boot49703-$v-1.jar 2>&1 | grep -E 'RESULT|Caused by|IllegalAccess|NoSuchMethod' | head -6 | tee -a $OUT
done
for v in 3.5.16 4.2.0; do
  mvn -q -f pom-$v.xml -Pwith clean compile dependency:build-classpath -Dmdep.outputFile=target-$v/cp.txt >/dev/null 2>&1
  CP=$(cat target-$v/cp.txt); LSB=$(tr ':' '\n' <<<"$CP" | grep log4j-spring-boot); REST=$(tr ':' '\n' <<<"$CP" | grep -v log4j-spring-boot | paste -sd: -)
  for order in first last; do
    if [[ $order == first ]]; then C=target-$v/classes:$LSB:$REST; else C=target-$v/classes:$REST:$LSB; fi
    echo "== Boot $v, with log4j-spring-boot $order on the classpath, profile=dev" | tee -a $OUT
    $JAVA_HOME/bin/java -cp $C repro.App --spring.profiles.active=dev 2>&1 | grep -E 'RESULT (startup|lookup|arbiter|property|log line: .* probe)' | cut -c1-200 | tee -a $OUT
  done
  echo "== Boot $v, log4j-spring-boot first, -Dlog4j2.disableCloudConfigLoggingSystem=true, profile=dev" | tee -a $OUT
  $JAVA_HOME/bin/java -Dlog4j2.disableCloudConfigLoggingSystem=true -cp target-$v/classes:$LSB:$REST repro.App --spring.profiles.active=dev 2>&1 | grep -E 'RESULT (startup|lookup|arbiter|property|log line: .* probe)' | cut -c1-200 | tee -a $OUT
  echo "   (no 'log line: ... probe' above = the application's log events were not written)" | tee -a $OUT
done
