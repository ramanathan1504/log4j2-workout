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
