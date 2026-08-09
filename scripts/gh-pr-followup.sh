#!/usr/bin/env bash
#
# gh-pr-followup.sh — what moved on a reviewed pull request since you reviewed it.
#
#   ./bench followup                 every PR in the ledger, one line each
#   ./bench followup 4234            one PR, in full
#   ./bench followup --changed       only the ones that moved
#   ./bench followup --mine          only where the last word is not yours
#   ./bench followup --sync 4234     record the current head as reviewed
#
# `./bench pr <n>` is a snapshot: it tells you what a PR looks like now. It
# cannot tell you whether the author pushed after you commented, whether a
# maintainer replied, or whether the thing you asked for was done — because it
# has nothing to compare against.
#
# This does. docs/pr-reviews/ledger.tsv records the head SHA each PR was at when
# it was reviewed; everything below is a diff against that.
#
# Read-only. Touches no clone and no branch. --sync writes the ledger only.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Overridable so the detection logic can be exercised against a fixture ledger
# without editing the real one. Nothing else should point this elsewhere.
LEDGER="${BENCH_PR_LEDGER:-$ROOT/docs/pr-reviews/ledger.tsv}"
REVIEW_DIR="$ROOT/docs/pr-reviews"
REPO="${BENCH_UPSTREAM_REPO:-apache/logging-log4j2}"
ME="${BENCH_GH_USER:-}"

die()  { printf '\033[31merror\033[0m %s\n' "$*" >&2; exit 1; }
dim()  { printf '\033[2m%s\033[0m\n' "$*"; }

command -v gh >/dev/null || die "gh is not installed"
[[ -f $LEDGER ]] || die "no ledger at $LEDGER"

ONLY=""; CHANGED=0; MINE=0; SYNC=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --changed) CHANGED=1; shift ;;
    --mine)    MINE=1;    shift ;;
    --sync)    SYNC="${2:-}"; [[ -n $SYNC ]] || die "--sync needs a PR number"; shift 2 ;;
    --repo)    REPO="$2"; shift 2 ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)        die "unknown flag: $1" ;;
    *)         ONLY="$1"; shift ;;
  esac
done

[[ -n $ME ]] || ME="$(gh api user --jq .login 2>/dev/null || true)"

# ── ledger access ───────────────────────────────────────────────────────────
ledger_rows() { grep -vE '^\s*(#|$)' "$LEDGER"; }
ledger_field() { # <pr> <1-based column>
  ledger_rows | awk -F'\t' -v pr="$1" -v c="$2" '$1==pr {print $c; exit}'
}

# ── --sync: re-record a PR at its current head ──────────────────────────────
if [[ -n $SYNC ]]; then
  ledger_field "$SYNC" 1 | grep -q . || die "PR $SYNC is not in the ledger"
  head=$(gh pr view "$SYNC" -R "$REPO" --json headRefOid --jq .headRefOid) \
    || die "could not read PR $SYNC from $REPO"
  # A full UTC timestamp, not a date. The badge test compares this against a
  # comment's createdAt, and "2026-08-09T15:18:04Z" > "2026-08-09" lexically --
  # so a date alone can never clear a badge raised by activity earlier the same
  # day, and the row looks permanently stuck. Date-only rows still compare
  # correctly, so old ledgers keep working.
  today=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  tmp=$(mktemp)
  awk -F'\t' -v OFS='\t' -v pr="$SYNC" -v h="$head" -v d="$today" \
    '/^[[:space:]]*(#|$)/ {print; next} $1==pr {$3=d; $4=h} {print}' "$LEDGER" > "$tmp"
  mv "$tmp" "$LEDGER"
  printf '\033[32m✓\033[0m PR %s re-recorded at %s (%s)\n' "$SYNC" "${head:0:8}" "$today"
  dim "  Only do this after actually re-reading it at that head."
  exit 0
fi

# ── fetch one PR's current state ────────────────────────────────────────────
fetch() { # <pr> -> tab-separated: state head mergedAt lastCommitDate lastEventAt lastEventBy lastEventKind failing
  gh pr view "$1" -R "$REPO" \
    --json state,headRefOid,mergedAt,commits,comments,reviews,statusCheckRollup \
    --jq '
      [ .state,
        .headRefOid,
        (.mergedAt // "-"),
        (if (.commits|length)>0 then .commits[-1].committedDate else "-" end),
        ( [ (.comments[]? | {at:.createdAt, by:.author.login, kind:"comment"}),
            (.reviews[]?  | {at:.submittedAt, by:.author.login, kind:(.state|ascii_downcase)}) ]
          | sort_by(.at)
          | if length>0 then (last | [.at,.by,.kind]) else ["-","-","-"] end ),
        ( [ .statusCheckRollup[]? | select(.conclusion=="FAILURE") | .name ] | join(",") )
      ] | flatten | @tsv' 2>/dev/null
}

# ── report one PR ───────────────────────────────────────────────────────────
report() { # <pr>
  local pr=$1 row verdict reviewed head_at author posted note
  row=$(ledger_rows | awk -F'\t' -v pr="$pr" '$1==pr')
  [[ -n $row ]] || die "PR $pr is not in the ledger"
  IFS=$'\t' read -r _ verdict reviewed head_at author posted note <<<"$row"

  local cur; cur=$(fetch "$pr") || { printf '  %-5s  \033[31munreachable\033[0m\n' "$pr"; return; }
  local state head merged lastcommit ev_at ev_by ev_kind failing
  IFS=$'\t' read -r state head merged lastcommit ev_at ev_by ev_kind failing <<<"$cur"

  # what moved
  local moved=()
  [[ $head != "$head_at" ]]           && moved+=("pushed")
  [[ $state != OPEN ]]                && moved+=("${state,,}")
  [[ -n $ev_by && $ev_by != "$ME" && $ev_by != "-" && $ev_at > $reviewed ]] && moved+=("reply:$ev_by")
  [[ -n $failing ]]                   && moved+=("ci-fail")

  if (( CHANGED )) && [[ ${#moved[@]} -eq 0 ]]; then return; fi
  if (( MINE ))    && { [[ $ev_by == "$ME" ]] || [[ $ev_by == "-" ]]; }; then return; fi

  local badge="\033[2m—\033[0m"
  [[ ${#moved[@]} -gt 0 ]] && badge="\033[33m$(IFS=,; echo "${moved[*]}")\033[0m"

  if [[ -n $ONLY ]]; then
    printf '\n\033[1mPR #%s\033[0m  %s  \033[2m(%s)\033[0m\n' "$pr" "$state" "$author"
    printf '  verdict     %s%s\n' "$verdict" "$([[ $posted == no ]] && echo '   (comment not posted)')"
    printf '  note        %s\n' "$note"
    printf '  reviewed    %s at %s\n' "$reviewed" "${head_at:0:8}"
    printf '  head now    %s%s\n' "${head:0:8}" "$([[ $head != "$head_at" ]] && printf '   \033[33m<- author pushed\033[0m')"
    [[ $lastcommit != "-" ]] && printf '  last commit %s\n' "$lastcommit"
    if [[ $ev_by == "-" ]]; then
      printf '  last word   \033[2mno comments or reviews yet\033[0m\n'
    else
      printf '  last word   %s by %s at %s\n' "$ev_kind" "$ev_by" "$ev_at"
    fi
    [[ -n $failing ]] && printf '  \033[31mCI failing\033[0m  %s\n' "$failing"
    [[ $merged != "-" ]] && printf '  merged      %s\n' "$merged"
    # Some reviews cover several PRs in one file (4240-4230-4232-*.md), so match
    # the number anywhere in the name, not only as a prefix.
    local f; f=$(find "$REVIEW_DIR" -name "*${pr}*.md" | sort | head -1)
    [[ -n $f ]] && printf '  review      %s\n' "${f#"$ROOT"/}"
    if [[ $head != "$head_at" ]]; then
      printf '\n  \033[33mRe-read before trusting the verdict above.\033[0m\n'
      printf '    ./bench pr %s --diff\n' "$pr"
      printf '    ./bench followup --sync %s     # once you have\n' "$pr"
    fi
  else
    printf '  %-5s %-14s %-16s %b\n' "$pr" "$verdict" "$author" "$badge"
  fi
}

# ── run ─────────────────────────────────────────────────────────────────────
if [[ -n $ONLY ]]; then
  report "$ONLY"
else
  printf '\033[1mReviewed PRs on %s\033[0m\n' "$REPO"
  dim   "  verdict from ${LEDGER#"$ROOT"/}; badge = what moved since"
  printf '\n'
  while IFS=$'\t' read -r pr _; do report "$pr"; done < <(ledger_rows)
  printf '\n'
  dim "  ./bench followup <n>          one PR in full"
  dim "  ./bench followup --changed    only what moved"
fi
