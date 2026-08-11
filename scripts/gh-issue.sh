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
# gh-issue.sh — read an upstream issue without leaving the terminal.
#
#   ./bench issue 4143                 the report, as filed
#   ./bench issue 4143 --comments      plus the discussion
#   ./bench issue 4143 --repo apache/logging-log4j-tools
#
# Prints what you need before touching the bench: which versions the reporter
# named, what they configured, and what they expected. Read-only — this never
# comments, labels or closes anything.

set -euo pipefail

die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m▸\033[0m %s\n' "$*" >&2; }
rule() { printf '\033[90m%s\033[0m\n' "────────────────────────────────────────────────────────────"; }

REPO="apache/logging-log4j2"
COMMENTS=0
ID=""

[[ $# -gt 0 ]] || die "usage: ./bench issue <number> [--comments] [--repo OWNER/NAME]"
ID="$1"; shift
[[ "$ID" =~ ^[0-9]+$ ]] || die "'$ID' is not an issue number"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --comments) COMMENTS=1; shift ;;
    --repo)     REPO="$2";  shift 2 ;;
    *) die "unexpected argument '$1'" ;;
  esac
done

command -v gh >/dev/null || die "gh is not installed (brew install gh)"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated (gh auth login)"

# A pull request is an issue as far as the REST API is concerned, but `gh issue
# view` refuses it. Say which it is rather than printing gh's bare 404.
if ! gh issue view "$ID" --repo "$REPO" >/dev/null 2>&1; then
  if gh pr view "$ID" --repo "$REPO" >/dev/null 2>&1; then
    die "$REPO#$ID is a pull request — use: ./bench pr $ID"
  fi
  die "no issue $ID in $REPO"
fi

gh issue view "$ID" --repo "$REPO" \
  --json number,title,state,createdAt,updatedAt,author,labels,milestone,body,url \
  --template '{{printf "#%v" .number}} [{{.state}}] {{.title}}
{{.url}}
opened {{timeago .createdAt}} by {{.author.login}}{{if .labels}}   labels: {{range $i, $l := .labels}}{{if $i}}, {{end}}{{$l.name}}{{end}}{{end}}{{if .milestone}}   milestone: {{.milestone.title}}{{end}}

{{.body}}
'

if [[ $COMMENTS -eq 1 ]]; then
  rule
  gh issue view "$ID" --repo "$REPO" --comments
fi

rule
info "next: reproduce on the version reported —"
printf '    ./bench run core-java --config <cfg> --log4j <version>\n' >&2
printf '    ./bench matrix --apps core-java --configs <cfg> --javas 17 --scenario <s>\n' >&2
printf '    ./bench repro %s --config <cfg> --scenario <s>\n' "$ID" >&2