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
# gh-pr-followup.sh — what moved on a reviewed pull request since you reviewed it.
#
#   ./bench followup                 every PR in the ledger, one line each
#   ./bench followup 4234            one PR, in full
#   ./bench followup --changed       only the ones that moved
#   ./bench followup --mine          only where the last word is not yours
#   ./bench followup --sync 4234     record the current head as reviewed
#   ./bench followup --comment 4234  print just the paste-ready comment, to pipe
#   ./bench followup --since 4234    what the author pushed since you reviewed
#   ./bench followup --since 4234 --write   ... and append it to the review file
#
# `./bench pr <n>` is a snapshot: it tells you what a PR looks like now. It
# cannot tell you whether the author pushed after you commented, whether a
# maintainer replied, or whether the thing you asked for was done — because it
# has nothing to compare against.
#
# This does. docs/pr-reviews/ledger.tsv records the head SHA each PR was at when
# it was reviewed; everything below is a diff against that.
#
# Read-only against GitHub. Touches no clone and no branch. --sync writes the
# ledger, --write appends to a file under docs/pr-reviews/. Neither ever writes
# to the upstream repository.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Overridable so the detection logic can be exercised against a fixture ledger
# without editing the real one. Nothing else should point this elsewhere.
LEDGER="${BENCH_PR_LEDGER:-$ROOT/docs/pr-reviews/ledger.tsv}"
# Overridable for the same reason as the ledger: --since --write appends to a
# review file, and that path has to be exercisable against a copy.
REVIEW_DIR="${BENCH_PR_REVIEW_DIR:-$ROOT/docs/pr-reviews}"
REPO="${BENCH_UPSTREAM_REPO:-apache/logging-log4j2}"
ME="${BENCH_GH_USER:-}"

die()  { printf '\033[31merror\033[0m %s\n' "$*" >&2; exit 1; }
dim()  { printf '\033[2m%s\033[0m\n' "$*"; }

command -v gh >/dev/null || die "gh is not installed"
[[ -f $LEDGER ]] || die "no ledger at $LEDGER"

ONLY=""; CHANGED=0; MINE=0; SYNC=""; COMMENT=""; SINCE=""; WRITE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --changed) CHANGED=1; shift ;;
    --mine)    MINE=1;    shift ;;
    --sync)    SYNC="${2:-}"; [[ -n $SYNC ]] || die "--sync needs a PR number"; shift 2 ;;
    --comment) COMMENT="${2:-}"; [[ -n $COMMENT ]] || die "--comment needs a PR number"; shift 2 ;;
    --since)   SINCE="${2:-}"; [[ -n $SINCE ]] || die "--since needs a PR number"; shift 2 ;;
    --write)   WRITE=1; shift ;;
    --repo)    REPO="$2"; shift 2 ;;
    -h|--help) sed -n '/limitations under the License\./,$p' "${BASH_SOURCE[0]}" \
                 | awk 'NR==1 {next} /^#/ {sub(/^# ?/, ""); print; next} {exit}'; exit 0 ;;
    -*)        die "unknown flag: $1" ;;
    *)         ONLY="$1"; shift ;;
  esac
done

# `(( WRITE ))` is 0-is-false, so this has to be an `if` -- as the tail of an
# `&&` chain at top level it would return 1 and `set -e` would kill the script
# on the ordinary no-flag path.
if (( WRITE )) && [[ -z $SINCE ]]; then
  die "--write only means something with --since <pr>"
fi

[[ -n $ME ]] || ME="$(gh api user --jq .login 2>/dev/null || true)"

# ── ledger access ───────────────────────────────────────────────────────────
ledger_rows() { grep -vE '^\s*(#|$)' "$LEDGER"; }
ledger_field() { # <pr> <1-based column>
  ledger_rows | awk -F'\t' -v pr="$1" -v c="$2" '$1==pr {print $c; exit}'
}

# ── --comment: print only the paste-ready block ─────────────────────────────
# A review file is mostly notes to yourself -- provenance, what was checked, what
# is blocking. Posting the whole file upstream sends all of that. Only the block
# under `── paste-ready comment ──` is addressed to the author, so that is what
# this prints, and nothing else.
if [[ -n $COMMENT ]]; then
  f=$(find "$REVIEW_DIR" -name "*${COMMENT}*.md" | sort | head -1)
  [[ -n $f ]] || die "no review file for PR $COMMENT in ${REVIEW_DIR#"$ROOT"/}"
  # A file may carry one block per PR it covers; prefer the one naming this PR.
  body=$(awk -v pr="$COMMENT" '
    /^## .*paste-ready comment/ {
      want = ($0 ~ ("#" pr) || $0 !~ /for #/); f = want; next
    }
    /^## / { f = 0 }
    f' "$f")
  [[ -n ${body//[$'\n\t ']/} ]] || die "no paste-ready block for PR $COMMENT in ${f#"$ROOT"/}"
  printf '%s\n' "$body"
  exit 0
fi

# ── --since: what the author pushed after the review ────────────────────────
# The question this answers is "the branch grew -- what is in it now that I did
# not read?". The obvious implementation is wrong: comparing head_at_review with
# the current head via the compare API attributes every commit the author merged
# in from the base branch to the author. Measured on #4185: comparing two of its
# own commits reported 20 commits and 89 files, almost all of them dependabot
# bumps merged in from 2.x. That is the opposite of the signal wanted.
#
# So commits come from the PR's own commit list (which excludes base commits),
# and files come from each commit individually, skipping merges -- GitHub diffs
# a merge against its first parent, so a merge of the base branch reports the
# whole base branch as its file list.
since_report() { # <pr>
  local pr=$1 row verdict reviewed head_at author posted note
  row=$(ledger_rows | awk -F'\t' -v pr="$pr" '$1==pr')
  [[ -n $row ]] || die "PR $pr is not in the ledger"
  IFS=$'\t' read -r _ verdict reviewed head_at author posted note <<<"$row"
  [[ -n ${head_at:-} ]] || die "PR $pr has no head_at_review recorded; nothing to diff against"

  local meta state head base title
  meta=$(gh pr view "$pr" -R "$REPO" --json state,headRefOid,baseRefName,title \
          --jq '[.state,.headRefOid,.baseRefName,.title]|@tsv') \
    || die "could not read PR $pr from $REPO"
  IFS=$'\t' read -r state head base title <<<"$meta"

  if [[ $head == "$head_at" ]]; then
    printf '\n\033[1mPR #%s\033[0m  %s  \033[2m(%s)\033[0m\n' "$pr" "$state" "$author"
    dim   "  head is still ${head_at:0:8} — nothing pushed since the review on $reviewed"
    return 0
  fi

  # The PR's own commits, oldest first, ending at the head.
  local commits_tsv
  commits_tsv=$(gh pr view "$pr" -R "$REPO" --json commits \
    --jq '.commits[] | [.oid, .committedDate, ((.authors[0].login // .authors[0].name) // "?"), .messageHeadline] | @tsv')

  # If the reviewed head is still in the list, everything after it is new. If it
  # is not, the branch was rebased or force-pushed and that history is gone --
  # fall back to the review timestamp and say so, rather than reporting nothing.
  local rebased=0 new_tsv
  if awk -F'\t' -v h="$head_at" '$1==h{found=1} END{exit !found}' <<<"$commits_tsv"; then
    new_tsv=$(awk -F'\t' -v h="$head_at" 'seen{print} $1==h{seen=1}' <<<"$commits_tsv")
  else
    rebased=1
    new_tsv=$(awk -F'\t' -v d="$reviewed" '$2 > d' <<<"$commits_tsv")
  fi

  local n_new; n_new=$(grep -c . <<<"$new_tsv" || true)

  # Per-commit detail. One API call each; new commits are normally few.
  local tmpd; tmpd=$(mktemp -d); trap 'rm -rf "$tmpd"' RETURN
  local oid n_merge=0
  while IFS=$'\t' read -r oid _; do
    [[ -n $oid ]] || continue
    gh api "repos/$REPO/commits/$oid" > "$tmpd/$oid.json" 2>/dev/null || continue
    if [[ $(jq '.parents|length' "$tmpd/$oid.json") -gt 1 ]]; then
      mv "$tmpd/$oid.json" "$tmpd/$oid.merge"
      n_merge=$((n_merge + 1))
    fi
  done <<<"$new_tsv"

  # Aggregate the per-file counts across the non-merge commits.
  local stat_tsv=""
  if compgen -G "$tmpd/*.json" >/dev/null; then
    stat_tsv=$(jq -s -r '
      [ .[] | .files[]? ]
      | group_by(.filename)
      | map({f: .[0].filename, a: (map(.additions)|add), d: (map(.deletions)|add)})
      | sort_by(-(.a + .d))[] | [.f, .a, .d] | @tsv' "$tmpd"/*.json)
  fi

  # ── terminal ──
  printf '\n\033[1mPR #%s\033[0m  %s  \033[2m(%s)\033[0m\n' "$pr" "$state" "$author"
  printf '  %s\n' "$title"
  printf '  verdict     %s  \033[2m%s\033[0m\n' "$verdict" "$note"
  printf '  reviewed    %s at %s  →  head now %s\n' "$reviewed" "${head_at:0:8}" "${head:0:8}"
  (( rebased )) && printf '  \033[33mrebased/force-pushed\033[0m — %s is no longer on the branch; listing by date instead\n' "${head_at:0:8}"

  if (( n_new == 0 )); then
    dim "  head moved but no commit is newer than the review — likely a rebase of the same work"
  else
    printf '\n  \033[1m%s new commit%s\033[0m%s\n' "$n_new" "$([[ $n_new == 1 ]] || echo s)" \
      "$( (( n_merge )) && printf ' (%s a merge of %s — files not attributed)' "$n_merge" "$base")"
    while IFS=$'\t' read -r oid at who subj; do
      [[ -n $oid ]] || continue
      printf '    %s  %-16s %s%s\n' "${oid:0:8}" "$who" "$subj" \
        "$([[ -f $tmpd/$oid.merge ]] && printf '   \033[2m[merge]\033[0m')"
    done <<<"$new_tsv"
  fi

  if [[ -n $stat_tsv ]]; then
    printf '\n  \033[1mfiles the author touched\033[0m\n'
    while IFS=$'\t' read -r f a d; do
      [[ -n $f ]] || continue
      printf '    \033[32m+%-5s\033[0m \033[31m-%-5s\033[0m %s\n' "$a" "$d" "$f"
    done <<<"$stat_tsv"
  elif (( n_new > 0 )); then
    dim "  no file changes outside merges"
  fi

  # ── against the review ──
  # Purely mechanical: the source files the review file names, crossed with the
  # files these commits touch. Touched is not the same as addressed, and the
  # output says so -- deciding that is the re-read this is meant to prompt.
  local rf; rf=$(find "$REVIEW_DIR" -name "*${pr}*.md" | sort | head -1)
  local named=""
  if [[ -n $rf ]]; then
    named=$(grep -oE '[A-Za-z0-9_]+\.(java|xml|adoc|properties|json|yaml)' "$rf" | sort -u)
    if [[ -n $named ]]; then
      printf '\n  \033[1mfiles named in the review\033[0m  \033[2m(%s)\033[0m\n' "${rf#"$ROOT"/}"
      local base_name hit
      while read -r base_name; do
        [[ -n $base_name ]] || continue
        hit=$(awk -F'\t' -v b="/$base_name" 'index($1, b) || $1==substr(b,2) {print $1}' <<<"$stat_tsv" | head -1)
        if [[ -n $hit ]]; then
          printf '    \033[33m● touched\033[0m   %s\n' "$base_name"
        else
          printf '    \033[2m○ untouched %s\033[0m\n' "$base_name"
        fi
      done <<<"$named"
      dim "  touched ≠ addressed — read the hunks before changing the verdict"
    fi
  fi

  # ── said since ──
  # `gh --jq` takes one expression and has no `--arg`, so the cutoff crosses
  # into jq through the environment rather than as a named argument.
  local said
  said=$(REVIEWED_AT="$reviewed" gh pr view "$pr" -R "$REPO" --json comments,reviews --jq '
      [ (.comments[]? | {at:.createdAt,  by:(.author.login // "?"), kind:"comment", body:(.body // "")}),
        (.reviews[]?  | {at:.submittedAt, by:(.author.login // "?"), kind:(.state|ascii_downcase), body:(.body // "")}) ]
      | map(select(.at > env.REVIEWED_AT))
      | sort_by(.at)[]
      | [.at, .by, .kind, ((.body | gsub("[\r\n]+"; " ") | .[0:160]))] | @tsv' 2>/dev/null || true)
  if [[ -n ${said//[$'\n\t ']/} ]]; then
    printf '\n  \033[1msaid since the review\033[0m\n'
    local at who kind body
    while IFS=$'\t' read -r at who kind body; do
      [[ -n $at ]] || continue
      printf '    %s  \033[36m%s\033[0m %s\n' "${at:0:10}" "$who" "$kind"
      [[ -n $body ]] && printf '      \033[2m%s\033[0m\n' "$body"
    done <<<"$said"
  fi

  if (( ! WRITE )); then
    printf '\n'
    dim "  ./bench followup --since $pr --write    append this to the review file"
    dim "  ./bench followup --sync $pr             once you have re-read it"
    return 0
  fi

  # ── --write ──
  [[ -n $rf ]] || die "no review file for PR $pr in ${REVIEW_DIR#"$ROOT"/} — nothing to append to"
  if grep -q "<!-- since:$head -->" "$rf"; then
    printf '\n'
    dim "  ${rf#"$ROOT"/} already records head ${head:0:8} — not appending twice"
    return 0
  fi

  local stamp; stamp=$(date -u +%Y-%m-%d)
  {
    printf '\n---\n\n'
    printf '<!-- since:%s -->\n' "$head"
    printf '## Since the review — %s\n\n' "$stamp"
    printf 'Appended by `./bench followup --since %s --write`. The review above was\n' "$pr"
    printf 'written at `%s` on %s; the head is now `%s`.\n\n' "${head_at:0:8}" "$reviewed" "${head:0:8}"
    (( rebased )) && printf '> **Rebased or force-pushed.** `%s` is no longer on the branch, so this\n> lists commits by date rather than by position. The old history is gone.\n\n' "${head_at:0:8}"

    if (( n_new == 0 )); then
      printf 'The head moved, but no commit is newer than the review — most likely a\nrebase of the same work.\n'
    else
      printf '### %s new commit%s\n\n' "$n_new" "$([[ $n_new == 1 ]] || echo s)"
      printf '| commit | author | subject |\n|---|---|---|\n'
      while IFS=$'\t' read -r oid at who subj; do
        [[ -n $oid ]] || continue
        subj=${subj//|/\\|}
        printf '| `%s` | %s | %s%s |\n' "${oid:0:8}" "$who" "$subj" \
          "$([[ -f $tmpd/$oid.merge ]] && printf ' _(merge of `%s`)_' "$base")"
      done <<<"$new_tsv"
      (( n_merge )) && printf '\n%s of these is a merge of `%s`. GitHub diffs a merge against its first\nparent, so its file list is the whole base branch — it is excluded from the\ncounts below, which are the author'"'"'s own edits only.\n' "$n_merge" "$base"
    fi

    if [[ -n $stat_tsv ]]; then
      printf '\n### Files the author touched\n\n'
      printf '| file | + | − |\n|---|---:|---:|\n'
      while IFS=$'\t' read -r f a d; do
        [[ -n $f ]] || continue
        printf '| `%s` | %s | %s |\n' "$f" "$a" "$d"
      done <<<"$stat_tsv"
    fi

    if [[ -n $named ]]; then
      printf '\n### Against what the review named\n\n'
      printf 'Mechanical: files this review file mentions, crossed with the files these\n'
      printf 'commits touch. **Touched is not addressed** — it says where to look.\n\n'
      printf '| file named in the review | in these commits |\n|---|---|\n'
      while read -r base_name; do
        [[ -n $base_name ]] || continue
        if awk -F'\t' -v b="/$base_name" 'index($1, b) || $1==substr(b,2) {f=1} END{exit !f}' <<<"$stat_tsv"; then
          printf '| `%s` | **touched** |\n' "$base_name"
        else
          printf '| `%s` | — |\n' "$base_name"
        fi
      done <<<"$named"
    fi

    if [[ -n ${said//[$'\n\t ']/} ]]; then
      printf '\n### Said since\n\n'
      while IFS=$'\t' read -r at who kind body; do
        [[ -n $at ]] || continue
        printf -- '- **%s** — %s, %s\n' "$who" "$kind" "$at"
        [[ -n $body ]] && printf '  > %s\n' "$body"
      done <<<"$said"
    fi

    # The hunks last: they are the longest part, and everything above is the
    # index into them.
    if compgen -G "$tmpd/*.json" >/dev/null; then
      printf '\n### The hunks\n\n'
      local cj csha
      while IFS=$'\t' read -r oid at who subj; do
        cj="$tmpd/$oid.json"
        [[ -f $cj ]] || continue
        jq -r --arg sha "${oid:0:8}" --arg subj "$subj" '
          "#### `\($sha)` — \($subj)\n",
          ( .files[]?
            | "`\(.filename)` (+\(.additions) −\(.deletions))\n",
              ( if .patch == null then "_no textual patch (binary, renamed, or too large)_\n"
                else "```diff\n" + (.patch | .[0:6000]) + "\n```\n"
                     + (if (.patch|length) > 6000 then "\n_patch truncated at 6000 characters._\n" else "" end)
                end ) )' "$cj"
      done <<<"$new_tsv"
    fi
  } >> "$rf"

  printf '\n\033[32m✓\033[0m appended to %s\n' "${rf#"$ROOT"/}"
  dim "  Review it, then: ./bench followup --sync $pr"
}

if [[ -n $SINCE ]]; then
  since_report "$SINCE"
  exit 0
fi

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
      printf '    ./bench followup --since %s            # what landed since\n' "$pr"
      printf '    ./bench followup --since %s --write    # ... into the review file\n' "$pr"
      printf '    ./bench followup --sync %s             # once you have re-read it\n' "$pr"
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
  dim "  ./bench followup --since <n>  what the author pushed since you reviewed"
fi
