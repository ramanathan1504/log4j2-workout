#!/usr/bin/env bash
#
# pr-redgreen.sh — does a contributor PR's test actually test its fix?
#
#   ./bench redgreen 4218
#   ./bench redgreen 4218 --verify        full `mvn verify`, not just tests
#   ./bench redgreen 4185 --jdk 17 --keep
#
# Four gates, in order. Any one of them failing is a review finding:
#
#   1. BUILD     the PR branch compiles and its own tests pass
#   2. SPOTLESS  it is formatted the way the project requires
#   3. RED       base + the PR's *test files only*  → must FAIL
#   4. GREEN     base + tests + the PR's *main files* → must PASS
#
# Gate 3 is the whole point. A test that passes without the production change
# proves the code loads, not that it was fixed — that is what #4218's first
# revision shipped, and the only way to see it is to run it. This automates the
# hand-check "I reverted your fix and your tests still pass".
#
# It never touches your Log4j clone's checkout: all work happens in a throwaway
# git worktree under .bench/redgreen/, so `2.x` stays where you left it and
# ~/.m2 is never overwritten. Nothing here talks to GitHub except `gh pr view`.

set -euo pipefail

die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m▸\033[0m %s\n' "$*" >&2; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$*" >&2; }
bad()  { printf '\033[31m✗\033[0m %s\n' "$*" >&2; }
warn() { printf '\033[33m!\033[0m %s\n' "$*" >&2; }
rule() { printf '\033[90m%s\033[0m\n' "────────────────────────────────────────────────────────────" >&2; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="apache/logging-log4j2"
CLONE="${BENCH_LOG4J_CLONE:-$HOME/apache/logging-log4j2}"
BASE=""
JDK="${BENCH_JDK:-17}"
GOAL="test"
KEEP=0

[[ $# -gt 0 ]] || die "usage: ./bench redgreen <number> [--base REF] [--verify] [--jdk N] [--keep] [--3x] [--repo OWNER/NAME] [--clone PATH]"
ID="$1"; shift
[[ "$ID" =~ ^[0-9]+$ ]] || die "'$ID' is not a pull request number"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base)   BASE="$2"; shift 2 ;;
    --verify) GOAL="verify"; shift ;;
    --jdk)    JDK="$2"; shift 2 ;;
    --keep)   KEEP=1; shift ;;
    --3x)     CLONE="${BENCH_LOG4J_CLONE:-$HOME/apache/log4j-main}"; shift ;;
    --repo)   REPO="$2"; shift 2 ;;
    --clone)  CLONE="$2"; shift 2 ;;
    *) die "unexpected argument '$1'" ;;
  esac
done

command -v gh >/dev/null || die "gh is not installed (brew install gh)"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated (gh auth login)"
[[ -d "$CLONE/.git" ]] || die "no git clone at $CLONE (pass --clone PATH)"

if [[ "$(uname -s)" == "Darwin" ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v "$JDK" 2>/dev/null)" \
    || die "no JDK $JDK installed (/usr/libexec/java_home -V lists them)"
  export JAVA_HOME
fi
# java_home answers an unknown version with the newest JDK and exit 0, so ask the
# JVM what it actually is. Log4j's enforcer pins [17,18) and fails in log4j-bom,
# which would otherwise read as a legitimate RED.
GOT="$("${JAVA_HOME:-/usr}/bin/java" -XshowSettings:properties -version 2>&1 \
        | sed -n 's/.*java\.specification\.version = //p')"
[[ "$GOT" == "$JDK" ]] || die "asked for JDK $JDK, got $GOT (${JAVA_HOME:-inherited}) — Log4j 2.x enforces [17,18)"
info "JDK $GOT — ${JAVA_HOME:-inherited}"

# ── Fetch the PR without disturbing the clone's checkout ────────────────────
[[ -n "$BASE" ]] || BASE="$(gh pr view "$ID" --repo "$REPO" --json baseRefName --jq .baseRefName)"
[[ -n "$BASE" ]] || die "could not resolve the base branch of $REPO#$ID"
HEAD_REF="pr-$ID"

info "fetching $REPO#$ID (base $BASE)"
git -C "$CLONE" fetch origin "pull/$ID/head:$HEAD_REF" --force --quiet
git -C "$CLONE" fetch origin "$BASE" --quiet
BASE_SHA="$(git -C "$CLONE" rev-parse FETCH_HEAD)"
MERGE_BASE="$(git -C "$CLONE" merge-base "$BASE_SHA" "$HEAD_REF")"

WORK="$ROOT/.bench/redgreen/pr-$ID"
LOGS="$ROOT/.bench/redgreen/pr-$ID-logs"
rm -rf "$LOGS"; mkdir -p "$LOGS"
git -C "$CLONE" worktree remove --force "$WORK" 2>/dev/null || true
rm -rf "$WORK"
info "worktree: $WORK"
git -C "$CLONE" worktree add --quiet --detach "$WORK" "$HEAD_REF"
cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    warn "keeping $WORK — remove with: git -C $CLONE worktree remove --force $WORK"
  else
    git -C "$CLONE" worktree remove --force "$WORK" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# ── Split the diff: what is a test, what is the fix, what is neither ────────
TESTS=""; MAINS=""; OTHER=""; MODULES=""; TESTCLASSES=""
while IFS=$'\t' read -r status path; do
  [[ -n "${path:-}" ]] || continue
  case "$status" in
    D*|R*) warn "$status $path — not applied by this script, check it by hand"; continue ;;
  esac
  case "$path" in
    */src/test/*)  TESTS="$TESTS $path" ;;
    */src/main/*)  MAINS="$MAINS $path" ;;
    *)             OTHER="$OTHER $path"; continue ;;
  esac
  MODULES="$MODULES ${path%%/*}"
  case "$path" in
    */src/test/java/*.java) f="${path##*/}"; TESTCLASSES="$TESTCLASSES,${f%.java}" ;;
  esac
done < <(git -C "$WORK" diff --name-status "$MERGE_BASE" "$HEAD_REF")

MODULES="$(printf '%s\n' $MODULES | sort -u | paste -sd, -)"
TESTCLASSES="${TESTCLASSES#,}"
[[ -n "$MODULES" ]] || die "no source changes in $REPO#$ID — nothing to build"

rule
printf '  modules      %s\n' "$MODULES" >&2
printf '  test files   %s\n' "$(printf '%s\n' $TESTS | grep -c . || true)" >&2
printf '  main files   %s\n' "$(printf '%s\n' $MAINS | grep -c . || true)" >&2
printf '  other        %s\n' "$(printf '%s\n' $OTHER | grep -c . || true)" >&2
[[ -n "$TESTCLASSES" ]] && printf '  -Dtest=      %s\n' "$TESTCLASSES" >&2
rule

MVN=(./mvnw -pl "$MODULES" -am -Dsurefire.failIfNoSpecifiedTests=false)
[[ -n "$TESTCLASSES" ]] && MVN+=(-Dtest="$TESTCLASSES")

run_gate() { # name goal logfile -> exit code, never aborts the script
  local name="$1" goal="$2" log="$3" rc=0
  info "$name — ./mvnw $goal -pl $MODULES ..."
  ( cd "$WORK" && "${MVN[@]}" "$goal" ) >"$log" 2>&1 || rc=$?
  return $rc
}

R_BUILD=1; R_SPOTLESS=1; R_RED=1; R_GREEN=1; RED_KIND=""

# ── 1. BUILD — the PR branch on its own ────────────────────────────────────
run_gate "1/4 BUILD" "$GOAL" "$LOGS/1-build.log" && R_BUILD=0 || R_BUILD=$?
if [[ $R_BUILD -eq 0 ]]; then
  ok "1/4 BUILD passed"
else
  bad "1/4 BUILD failed — $LOGS/1-build.log"
  grep -m3 -E '^\[ERROR\]' "$LOGS/1-build.log" | sed 's/^/    /' >&2 || true
  # Stop here on purpose. A broken build makes every later gate meaningless —
  # RED would "fail as required" for a reason that has nothing to do with the fix.
  die "stopping: gates 2–4 cannot be trusted once the PR branch does not build"
fi

# ── 2. SPOTLESS — formatting the project actually enforces ─────────────────
info "2/4 SPOTLESS — ./mvnw spotless:check -pl $MODULES"
if ( cd "$WORK" && ./mvnw -pl "$MODULES" spotless:check ) >"$LOGS/2-spotless.log" 2>&1; then
  R_SPOTLESS=0; ok "2/4 SPOTLESS passed"
else
  R_SPOTLESS=1; bad "2/4 SPOTLESS failed — $LOGS/2-spotless.log (author should run spotless:apply)"
fi

# ── 3. RED — base plus the PR's tests only. This must FAIL. ────────────────
if [[ -z "$TESTS" ]]; then
  warn "3/4 RED skipped — the PR ships no test files. That is itself the finding."
  RED_KIND="no-tests"
elif [[ -z "$MAINS" ]]; then
  warn "3/4 RED skipped — the PR changes no main sources (test- or docs-only)."
  RED_KIND="no-fix"
else
  info "3/4 RED — resetting to $BASE, then applying test files only"
  ( cd "$WORK" && git checkout --quiet --detach "$MERGE_BASE" && git checkout --quiet "$HEAD_REF" -- $TESTS )
  if run_gate "3/4 RED" "$GOAL" "$LOGS/3-red.log"; then
    R_RED=1; RED_KIND="passed"
    bad "3/4 RED — the tests PASS without the fix. They do not test this change."
  # A non-zero exit is not yet a red. Insist on evidence the tests actually ran
  # and failed, or that they failed to compile against the unfixed sources —
  # anything else is the build breaking for an unrelated reason.
  elif grep -qE 'Tests run:.*(Failures: [1-9]|Errors: [1-9])' "$LOGS/3-red.log"; then
    R_RED=0; RED_KIND="assert"
    ok "3/4 RED — tests fail without the fix, as they should"
    grep -m3 -E 'Tests run:.*(Failures: [1-9]|Errors: [1-9])' "$LOGS/3-red.log" | sed 's/^/    /' >&2
  elif grep -q 'COMPILATION ERROR' "$LOGS/3-red.log" && grep -q '/src/test/' "$LOGS/3-red.log"; then
    R_RED=0; RED_KIND="compile"
    ok "3/4 RED — the test does not compile without the fix (valid red, but it pins an API, not a behaviour)"
  else
    R_RED=1; RED_KIND="inconclusive"
    bad "3/4 RED — build failed without running the tests. Not a red; read the log."
    grep -m3 -E '^\[ERROR\]' "$LOGS/3-red.log" | sed 's/^/    /' >&2 || true
  fi

  # ── 4. GREEN — add the fix back. This must PASS. ─────────────────────────
  info "4/4 GREEN — applying main files on top"
  ( cd "$WORK" && git checkout --quiet "$HEAD_REF" -- $MAINS )
  run_gate "4/4 GREEN" "$GOAL" "$LOGS/4-green.log" && R_GREEN=0 || R_GREEN=$?
  [[ $R_GREEN -eq 0 ]] && ok "4/4 GREEN passed" || bad "4/4 GREEN failed — $LOGS/4-green.log"
fi

# ── Verdict, in a shape you can paste into the review file ─────────────────
rule
verdict() { [[ "$1" -eq 0 ]] && printf 'pass' || printf 'FAIL'; }
printf '\n## Mechanical gates — %s#%s (base %s, JDK %s)\n\n' "$REPO" "$ID" "$BASE" "$JDK"
printf '| Gate | Result |\n|---|---|\n'
printf '| BUILD (`%s`) | %s |\n' "$GOAL" "$(verdict $R_BUILD)"
printf '| SPOTLESS | %s |\n' "$(verdict $R_SPOTLESS)"
case "$RED_KIND" in
  assert)   printf '| RED — tests without the fix | **fail, as required** |\n' ;;
  compile)  printf '| RED — tests without the fix | **compile error** — valid, but the test asserts an API, not a behaviour |\n' ;;
  passed)   printf '| RED — tests without the fix | **PASS — the tests do not test the fix** |\n' ;;
  inconclusive) printf '| RED — tests without the fix | **inconclusive** — the build broke before the tests ran |\n' ;;
  no-tests) printf '| RED | skipped — **the PR ships no tests** |\n' ;;
  no-fix)   printf '| RED | skipped — no main-source change |\n' ;;
esac
[[ -n "$MAINS" && -n "$TESTS" ]] && printf '| GREEN — tests with the fix | %s |\n' "$(verdict $R_GREEN)"
printf '\nLogs: `%s`\n' "${LOGS/#$ROOT\//}"
rule

# ── The half this script cannot do ─────────────────────────────────────────
info "these four gates are necessary, not sufficient — next, by hand:"
cat >&2 <<NEXT
    docs/PR-REVIEW.md §2   does the fix match the bug, or overshoot it?
                           check every implementation, not the one that motivated it
    ./bench pr $ID --files                       which module, so which app reaches it
    ./bench coverage                             does any app put that module on a classpath
    gh pr view $ID -R $REPO --json body --jq .body   is there a linked issue, and who filed it
    gh pr list -R $REPO --author <login> --state all --limit 30
    ./bench repro $ID --pr --config <cfg> --scenario <s> --log4j 2.25.5 --log4j 2.26.1
    docs/pr-reviews/$ID-<slug>.md                write it up, paste-ready block last
NEXT
[[ $R_BUILD -eq 0 && $R_SPOTLESS -eq 0 && $R_RED -eq 0 && $R_GREEN -eq 0 ]] || exit 1
