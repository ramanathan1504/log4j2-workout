# STAGE 1: Build the Fixed Snapshot (2.27.0-SNAPSHOT)
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /log4j-src
RUN apt-get update && apt-get install -y git sed
RUN git clone --depth 1 --branch 2.x https://github.com/apache/logging-log4j2.git .

# Apply your fix: Force 7.2.3 in the local parent POM
RUN sed -i 's/<bnd.annotation.version>7.1.0<\/bnd.annotation.version>/<bnd.annotation.version>7.2.3<\/bnd.annotation.version>/g' log4j-parent/pom.xml

RUN sed -i '/distributionSha256Sum/d' .mvn/wrapper/maven-wrapper.properties
RUN ./mvnw install -DskipTests -pl log4j-api-java9,log4j-api -am

# STAGE 2: Verification using JDK 8 (Strict Proof)
FROM eclipse-temurin:8-jdk
RUN apt-get update && apt-get install -y wget unzip
RUN wget https://services.gradle.org/distributions/gradle-8.5-bin.zip -P /tmp && \
    unzip -d /opt/gradle /tmp/gradle-8.5-bin.zip && \
    ln -s /opt/gradle/gradle-8.5/bin/gradle /usr/bin/gradle

WORKDIR /repro
COPY --from=builder /root/.m2/repository /root/.m2/repository

# Create the test file
RUN mkdir -p src/main/java
RUN echo 'import org.apache.logging.log4j.util.PropertiesUtil; \
public class Reproduce { \
    public static void main(String[] args) { \
        PropertiesUtil.getProperties(); \
        System.out.println("Success!"); \
    } \
}' > src/main/java/Reproduce.java

# Create build.gradle
RUN echo "plugins { id 'java' }\n\
repositories { mavenLocal(); mavenCentral() }\n\
dependencies {\n\
    implementation \"org.apache.logging.log4j:log4j-api:\${log4jVersion}\"\n\
}\n\
tasks.withType(JavaCompile) {\n\
    options.compilerArgs << '-Xlint:classfile' << '-Werror'\n\
}" > build.gradle

# TEST 1: Confirm 2.26.0 is BROKEN
RUN echo "--- TESTING LOG4J 2.26.0 (EXPECTED TO FAIL) ---"
RUN gradle compileJava -Plog4jVersion=2.26.0 || echo "RESULT: 2.26.0 FAILED AS EXPECTED"

# TEST 2: Confirm 2.27.0-SNAPSHOT is FIXED
RUN echo "--- TESTING LOG4J 2.27.0-SNAPSHOT (EXPECTED TO PASS) ---"
RUN gradle compileJava -Plog4jVersion=2.27.0-SNAPSHOT && echo "RESULT: 2.27.0-SNAPSHOT PASSED"