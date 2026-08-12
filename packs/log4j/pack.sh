# shellcheck shell=bash
#
# packs/log4j/pack.sh — what this bench tests, as data.
#
# `bench` is the engine: it forks JVMs, walks a matrix, caches classpaths and
# reports cells. None of that is specific to Log4j. THIS file is the part that
# is, and it is the only file you replace to point the same engine at something
# else:
#
#   BENCH_PACK=mine ./bench list        # loads packs/mine/pack.sh instead
#
# A pack declares five things and nothing more. Everything it sets is read by
# the engine and never written back, so a pack cannot change how the engine
# behaves -- only what it runs against. That boundary is the reason a broken
# pack fails at load with a named variable rather than somewhere deep in a sweep.
#
#   PACK_NAME / PACK_DESC   what this is, for `bench list` and error messages
#   VERSIONS                the version axis, oldest -> newest
#   DEFAULT_VERSION         what --log4j defaults to
#   APPS                    the app axis
#   APPS_2X_ONLY            apps the newest major cannot build (matrix SKIPs them)
#   pack_module_path        app name -> directory, relative to the repo root
#
# Paths stay where they are on purpose. Moving configs/ and apps/ into the pack
# directory would have been tidier and would also have rewritten every path in
# the CI workflow, the docs and seven repro directories -- a large diff whose
# only content is renames, on a tool whose CI is ~200 cells. A pack points AT
# its content instead.

PACK_NAME="log4j"
PACK_DESC="Apache Log4j across a version x config x app matrix, on real JVMs"

# Where this pack's content lives, relative to the repository root.
PACK_CONFIGS_DIR="configs"
PACK_APPS_DIR="apps"

# ── The version axis ────────────────────────────────────────────────────────
# Ordered oldest → newest. `matrix` walks this list.
VERSIONS=(
  2.24.1
  2.25.4
  2.25.5
  2.26.0
  2.26.1
  # 2.27.0 is deliberately absent: it is unreleased (404 on Central, whose
  # newest is 2.26.1), so every cell on it fails to resolve rather than
  # testing anything. Listing it cost ~1/8 of a full matrix in guaranteed
  # noise. Add it back when it ships.
  2.27.0-SNAPSHOT
  3.0.0-SNAPSHOT
)
DEFAULT_VERSION=2.27.0-SNAPSHOT

APPS=(core-java spring-boot-maven db log4j1-bridge jakarta-web spring-boot-gradle
      java8-baseline bridges-in bridges-out bridges-to-jul custom-plugins jpa smtp javax-web jdbc-jndi jms spring-cloud-config network nosql)

# Apps that cannot build on 3.x, because the Log4j artifacts they need have no
# 3.x release. `matrix` skips these rather than reporting a spurious failure.
APPS_2X_ONLY=(log4j1-bridge jakarta-web spring-boot-gradle
              bridges-in bridges-out bridges-to-jul jpa smtp javax-web jms
              custom-plugins)

# app name -> module directory. Several apps share one module (db and nosql are
# the same project under two configurations), which is why this is a mapping and
# not a string concatenation.
pack_module_path() {
  case "$1" in
    core-java)          echo "apps/core-java" ;;
    spring-boot-maven)  echo "apps/spring-boot-maven" ;;
    spring-boot-gradle) echo "apps/spring-boot-gradle" ;;
    db|nosql)           echo "apps/db" ;;
    log4j1-bridge)      echo "apps/log4j1-bridge" ;;
    jakarta-web)        echo "apps/jakarta-web" ;;
    java8-baseline)     echo "apps/java8-baseline" ;;
    custom-plugins)     echo "apps/custom-plugins" ;;
    jpa)                echo "apps/jpa" ;;
    smtp)               echo "apps/smtp" ;;
    javax-web)          echo "apps/javax-web" ;;
    jdbc-jndi)          echo "apps/jdbc-jndi" ;;
    jms)                echo "apps/jms" ;;
    network)            echo "apps/network" ;;
    spring-cloud-config) echo "apps/spring-cloud-config" ;;
    bridges-in)         echo "apps/bridges-in" ;;
    bridges-out)        echo "apps/bridges-out" ;;
    bridges-to-jul)     echo "apps/bridges-to-jul" ;;
    *) return 1 ;;
  esac
}

# ── What only this pack knows ───────────────────────────────────────────────
# Moved down out of the engine. Each of these is a fact about Log4j or about an
# application in this pack, and an engine that knew them could only ever drive
# this one project. They are the difference between a matrix runner and a Log4j
# matrix runner.

# Apps whose main() is a server: they start a container, print an endpoint and
# serve until interrupted. They are driven by hand or by an HTTP client, and can
# never complete a matrix cell — under a bound they burn the whole timeout and
# then FAIL, which is 300 seconds spent to learn nothing.
#
# Empty, and kept for the next app that needs it. Every server app in the bench
# now drives its own endpoints and exits: spring-boot-maven and
# spring-boot-gradle through SelfTestRunner, jakarta-web and javax-web through
# the selfTest() in each launcher. See extra_jvm_args_for, which is what turns
# the mode on. A server app added without one belongs here, so a sweep states
# the reason rather than reporting a false failure.
PACK_INTERACTIVE_APPS=()
# The oldest JDK an app's own bytecode can run on. Everything is compiled at
# release 17 except the Java 8 baseline module, which exists precisely so the
# oldest JDK Log4j 2 supports is testable at all.
pack_min_java_for() {
  case "$1" in
    java8-baseline) echo 8 ;;
    *)              echo 17 ;;
  esac
}
# The oldest 2.x line an app can run on, where one of its Log4j modules is
# younger than the oldest version in VERSIONS. Empty means "any".
pack_min_version_for() {
  case "$1" in
    # log4j-jakarta-jms was first released at 2.25.0; before that only the
    # javax JMS appender in log4j-core existed.
    jms) echo "2.25.0" ;;
    *)   echo "" ;;
  esac
}
# The appender an app asserts on. An app that checks rows reached a JDBC
# appender cannot pass under a console-only configuration — it is not a failure,
# it is a question with no meaning. The sweep's cross product generates plenty
# of those: `db` under xml/baseline-console produced 81 of them in one run,
# every one reported as FAIL.
#
# Maps app -> a substring every configuration able to satisfy it contains.
pack_requires_config_for() {
  case "$1" in
    db)        echo "appender-jdbc" ;;
    jdbc-jndi) echo "appender-jdbc-jndi" ;;
    jpa)       echo "appender-jpa" ;;
    nosql)     echo "appender-nosql" ;;
    smtp)      echo "appender-smtp" ;;
    jms)       echo "appender-jms" ;;
    network)   echo "appender-network" ;;
    *)         echo "" ;;
  esac
}
# The mirror of pack_requires_config_for: configurations whose destinations are
# supplied in-process by one particular app, and which therefore cannot work
# under any other. `appender-network` posts to localhost:4560, :5514 and :8123 —
# listeners that apps/network's NetworkBench opens itself. Loaded by core-java
# those are simply Connection refused, once per appender per event.
#
# Left unconstrained this produced 21 failures in the first 2000 cells and would
# have produced thousands more: every app, every one of these configs, every JDK
# and version. All of them saying nothing except "the listener was not there".
#
# appender-nosql is deliberately absent: Mongo, CouchDB and Cassandra run in
# containers, so any app can write to them. Only in-process infrastructure
# creates this coupling.
pack_requires_app_for() {
  case "$1" in
    */appender-network)    echo "network" ;;     # in-process socket/HTTP listeners
    */appender-smtp)       echo "smtp" ;;        # embedded GreenMail
    */appender-jms)        echo "jms" ;;         # embedded ActiveMQ Artemis
    */appender-jpa)        echo "jpa" ;;         # its own persistence unit
    */appender-jdbc-jndi)  echo "jdbc-jndi" ;;   # in-process InitialContext
    *)                     echo "" ;;
  esac
}
pack_is_2x_only() {
  local app="$1" a
  for a in "${APPS_2X_ONLY[@]}"; do [[ "$a" == "$app" ]] && return 0; done
  return 1
}
pack_main_class_for() {
  case "$1" in
    core-java)          echo "org.apache.logging.bench.Bench" ;;
    spring-boot-maven)  echo "org.apache.logging.bench.spring.BenchApplication" ;;
    spring-boot-gradle) echo "org.apache.logging.bench.spring.BenchApplication" ;;
    db)                 echo "org.apache.logging.bench.db.DbBench" ;;
    nosql)              echo "org.apache.logging.bench.db.NoSqlBench" ;;
    log4j1-bridge)      echo "org.apache.logging.bench.log4j1.Log4j1Bench" ;;
    jakarta-web)        echo "org.apache.logging.bench.web.WebBench" ;;
    java8-baseline)     echo "org.apache.logging.bench.java8.Java8Bench" ;;
    custom-plugins)     echo "org.apache.logging.bench.plugins.CustomPluginBench" ;;
    jpa)                echo "org.apache.logging.bench.jpa.JpaBench" ;;
    smtp)               echo "org.apache.logging.bench.smtp.SmtpBench" ;;
    javax-web)          echo "org.apache.logging.bench.javaxweb.JavaxWebBench" ;;
    jdbc-jndi)          echo "org.apache.logging.bench.jndi.JdbcJndiBench" ;;
    jms)                echo "org.apache.logging.bench.jms.JmsBench" ;;
    network)            echo "org.apache.logging.bench.network.NetworkBench" ;;
    spring-cloud-config) echo "org.apache.logging.bench.cloud.CloudConfigBench" ;;
    bridges-in)         echo "org.apache.logging.bench.bridges.BridgesInBench" ;;
    bridges-out)        echo "org.apache.logging.bench.bridges.BridgesOutBench" ;;
    bridges-to-jul)     echo "org.apache.logging.bench.bridges.ToJulBench" ;;
  esac
}


# Why a cell of THIS pack cannot run. Prints a reason, or nothing.
#
# The engine has its own rules -- an app compiled at a newer release than the
# JDK, a server app that never exits, a config whose appender the app does not
# assert on. These are the ones only Log4j knows.
pack_skip_reason() {
  local app="$1" config="$2" java="$3" version="$4"

  if [[ "$version" == 3.* ]]; then
    pack_is_2x_only "$app" && { echo "$app has no 3.x release path"; return; }
    [[ "$java" -lt 17 ]] && { echo "Log4j 3 requires Java 17+"; return; }
    [[ "$config" == properties/* ]] && {
      echo "properties format removed in 3.x (JavaPropsConfigurationFactory uses other keys)"; return; }
  fi

  # The 1.x config formats are only readable where log4j-1.2-api is present.
  if [[ "$config" == log4j1/* && "$app" != log4j1-bridge ]]; then
    echo "1.x config needs the log4j-1.2-api bridge, which only log4j1-bridge has"
    return
  fi
  return 0
}

# How a version reaches Maven. One flag per line, because bash 3.2 -- which is
# what macOS ships -- has no mapfile to capture an array from a function.
pack_build_flags() {
  local version="$1"
  printf '%s\n' "-Dlog4j.version=$version"
  if [[ "$version" == 3.* ]]; then
    printf '%s\n' "-Plog4j-3x" "-Dlog4j3=true"
  fi
}


# How to point the application under test at a configuration.
#
# The single most pack-specific thing there is, and the one that punishes a guess
# rather than failing: pass 2.x's property name to Log4j 3 and it does not error,
# it falls back to DefaultConfiguration and logs to the console. An entire 3.x
# column can pass while testing nothing at all.
pack_config_args() {
  local app="$1" resolved="$2" version="$3"

  if [[ "$resolved" == */configs/log4j1/* ]]; then
    # 1.x config formats need the bridge's factories, off by default, and are
    # selected by a different property: log4j.configuration (no "File"), a URL.
    printf '%s\n' "-Dlog4j1.compatibility=true" "-Dlog4j.configuration=file:$resolved"
  elif [[ "$version" == 3.* ]]; then
    # Log4j 3 rebuilt its property system: log4j.configuration.location
    # (CoreProperties.ConfigurationProperties). log4j.configurationFile is simply
    # not read.
    printf '%s\n' "-Dlog4j.configuration.location=$resolved"
  else
    printf '%s\n' "-Dlog4j.configurationFile=$resolved"
  fi

  # Spring Boot's Log4J2LoggingSystem reconfigures Log4j during startup and
  # ignores Log4j's own property -- it honours `logging.config` instead.
  if [[ "$app" == spring-boot-* && "$resolved" != */configs/log4j1/* ]]; then
    printf '%s\n' "-Dlogging.config=$resolved"
  fi
}
