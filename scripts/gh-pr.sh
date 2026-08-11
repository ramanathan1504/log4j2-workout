#!/usr/bin/env bash
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# gh-pr.sh — read an upstream pull request, and put it on your classpath.
#
#   ./bench pr 4133                    metadata, checks, reviews, files touched
#   ./bench pr 4133 --diff             the patch itself
#   ./bench pr 4133 --checkout         fetch it into the Log4j clone as pr-4133
#   ./bench pr 4133 --checkout --install    ...and mvn install it as a SNAPSHOT
#   ./bench pr 4133 --3x --checkout --install
#
# Without --checkout this is read-only and touches nothing. With it, the *Log4j
# clone* is switched to a new branch — never this repository, which is left
# alone in every mode.
#
# --install is what makes the PR selectable by the bench: it publishes
# 2.27.0-SNAPSHOT (or 3.0.0-SNAPSHOT with --3x) into ~/.m2, which is where
# ./bench resolves those versions from. Baseline against a release *before*
# running it, because the snapshot you overwrite is whatever was there before.

set -euo pipefail

die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m▸\033[0m %s\n' "$*" >&2; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$*" >&2; }
warn() { printf '\033[33m!\033[0m %s\n' "$*" >&2; }
rule() { printf '\033[90m%s\033[0m\n' "────────────────────────────────────────────────────────────"; }

REPO="apache/logging-log4j2"
CLONE="${BENCH_LOG4J_CLONE:-$HOME/apache/logging-log4j2}"
SNAPSHOT="2.27.0-SNAPSHOT"
DIFF=0
FILES=0
CHECKOUT=0
INSTALL=0
ID=""

[[ $# -gt 0 ]] || die "usage: ./bench pr <number> [--diff] [--files] [--checkout] [--install] [--3x] [--repo OWNER/NAME] [--clone PATH]"
ID="$1"; shift
[[ "$ID" =~ ^[0-9]+$ ]] || die "'$ID' is not a pull request number"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --diff)     DIFF=1;     shift ;;
    --files)    FILES=1;    shift ;;
    --checkout) CHECKOUT=1; shift ;;
    --install)  INSTALL=1;  shift ;;
    --3x)       CLONE="${BENCH_LOG4J_CLONE:-$HOME/apache/log4j-main}"
                SNAPSHOT="3.0.0-SNAPSHOT"; shift ;;
    --repo)     REPO="$2";  shift 2 ;;
    --clone)    CLONE="$2"; shift 2 ;;
    *) die "unexpected argument '$1'" ;;
  esac
done

command -v gh >/dev/null || die "gh is not installed (brew install gh)"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated (gh auth login)"
gh pr view "$ID" --repo "$REPO" >/dev/null 2>&1 || die "no pull request $ID in $REPO"

# ── What it is ──────────────────────────────────────────────────────────────
gh pr view "$ID" --repo "$REPO" \
  --json number,title,state,isDraft,createdAt,author,baseRefName,headRefName,additions,deletions,changedFiles,labels,url,body \
  --template '{{printf "#%v" .number}} [{{.state}}{{if .isDraft}}/draft{{end}}] {{.title}}
{{.url}}
opened {{timeago .createdAt}} by {{.author.login}}   {{.headRefName}} → {{.baseRefName}}
{{.changedFiles}} file(s)  +{{.additions}} −{{.deletions}}{{if .labels}}   labels: {{range $i, $l := .labels}}{{if $i}}, {{end}}{{$l.name}}{{end}}{{end}}

{{.body}}
'

# ── Where it lands, which is what decides how you test it ───────────────────
rule
info "files touched"
gh pr diff "$ID" --repo "$REPO" --name-only | sed 's/^/    /'

rule
info "modules touched"
gh pr diff "$ID" --repo "$REPO" --name-only \
  | sed -n 's#^\(log4j-[a-z0-9-]*\)/.*#\1#p' | sort -u | sed 's/^/    /'
printf '    (cross-check with ./bench coverage: which app puts that module on a classpath)\n' >&2

rule
info "checks"
gh pr checks "$ID" --repo "$REPO" 2>/dev/null | sed 's/^/    /' || printf '    none reported\n'

info "reviews"
gh pr view "$ID" --repo "$REPO" --json reviews \
  --jq '.reviews[] | "    \(.author.login)  \(.state)"' 2>/dev/null || true

[[ $FILES -eq 1 ]] && { rule; gh pr diff "$ID" --repo "$REPO" --name-only; }
[[ $DIFF  -eq 1 ]] && { rule; gh pr diff "$ID" --repo "$REPO"; }

# ── Optionally, make it runnable ────────────────────────────────────────────
if [[ $CHECKOUT -eq 1 ]]; then
  rule
  [[ -d "$CLONE/.git" ]] || die "no git clone at $CLONE (pass --clone PATH)"
  if [[ -n "$(git -C "$CLONE" status --porcelain)" ]]; then
    die "$CLONE has uncommitted changes — commit or stash them first"
  fi

  info "fetching $REPO#$ID into $CLONE as pr-$ID"
  git -C "$CLONE" fetch origin "pull/$ID/head:pr-$ID" --force
  git -C "$CLONE" switch "pr-$ID"
  ok "$CLONE is on pr-$ID ($(git -C "$CLONE" log --oneline -1))"

  if [[ $INSTALL -eq 1 ]]; then
    warn "installing over $SNAPSHOT in ~/.m2 — baseline against a release first if you have not"
    info "mvn install -DskipTests   (this takes a while)"
    ( cd "$CLONE" && mvn install -DskipTests -q )
    ok "$SNAPSHOT in ~/.m2 is now $REPO#$ID"
  fi

  rule
  info "next: run it, then compare against the release either side —"
  printf '    ./bench run core-java --config <cfg>                  # = %s = this PR\n' "$SNAPSHOT" >&2
  printf '    ./bench run core-java --config <cfg> --log4j 2.26.1   # the release before\n' >&2
  printf '    ./bench matrix --apps core-java --configs <cfg> --javas 17 --scenario <s>\n' >&2
  printf '    ./bench repro %s --pr --config <cfg> --scenario <s>\n' "$ID" >&2
  printf '\n' >&2
  info "when done: git -C $CLONE switch main"
fi