#!/bin/zsh
set -e
cd "$(dirname "$0")"
J8=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
J17=/Users/ramanathan/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home
REL=2.26.1
M2=~/.m2/repository/org/apache/logging/log4j
SRC=~/apache/logging-log4j2
[ -f $M2/log4j-core/$REL/log4j-core-$REL.jar ] || ~/apache/logging-log4j2/mvnw -q dependency:get -Dartifact=org.apache.logging.log4j:log4j-core:$REL
typeset -A CP
CP[release-$REL]=$M2/log4j-api/$REL/log4j-api-$REL.jar:$M2/log4j-core/$REL/log4j-core-$REL.jar
CP[2.x-base]=target/base/log4j-api.jar:target/base/log4j-core.jar
CP[pr-head]=$SRC/log4j-api/target/log4j-api-2.27.0-SNAPSHOT.jar:$SRC/log4j-core/target/log4j-core-2.27.0-SNAPSHOT.jar
mkdir -p ../output
for k in release-$REL 2.x-base pr-head; do
  for J in $J8 $J17; do
    out=target/classes-$k; rm -rf $out; mkdir -p $out
    $J8/bin/javac -nowarn -cp ${CP[$k]} -d $out src/main/java/UdpRepro.java && cp src/main/resources/log4j2.xml $out/
    v=$($J/bin/java -version 2>&1 | head -1 | grep -oE '"[^"]+"' | tr -d '"')
    echo "== $k on java $v" | tee -a ../output/run.txt
    $J/bin/java -cp $out:${CP[$k]} UdpRepro 2>&1 | grep -vE '^\s+at ' | cut -c1-200 | tee -a ../output/run.txt
  done
done
