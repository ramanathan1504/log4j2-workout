# Every `gh` command this workflow needs

So the whole loop — read the report, run it, file the finding, ship the fix,
watch CI — happens in the terminal. Nothing here needs a browser.

Two wrappers cover the commands you type most; the rest is raw `gh`.

| Wrapper | Replaces |
|---|---|
| `oss issue <n>` | `gh issue view` with the fields that matter. In the core, not here — see below |
| `oss pr <n>` | the facts about any pull request, in any repository. In the core |
| `./bench pr <n>` | which `log4j-*` modules the diff lands in, and `--checkout --install` to make the PR selectable by `./bench` |
| `oss followup` | nothing — `gh` has no notion of *what changed since you reviewed*. See [§5](#5-following-a-pr-after-you-reviewed-it) |

Source: [`../scripts/gh-pr.sh`](../scripts/gh-pr.sh). Read-only unless you pass
`--checkout`, which touches the **Log4j clone** and never this repository.

**Reading an issue moved to `oss`.** It needs one API call and nothing else, so
it belongs in the core rather than behind a bench you must attach first — and it
works against any repository, not only Log4j. `./bench issue` prints where it
went rather than failing silently.

Throughout, `-R apache/logging-log4j2` is the upstream repo. Omit `-R` inside
this clone and `gh` targets this repository instead — which is the single
easiest way to file a bug report in the wrong place.

---

## 0. Once

```bash
gh auth login                      # scopes: repo, read:org, workflow
gh auth status                     # confirm which account is active
gh repo set-default                # stop gh guessing when a clone has many remotes
```

---

## 1. Upstream issues

```bash
oss issue 4143 --repo apache/logging-log4j2          # the report, formatted
gh issue view 4143 -R apache/logging-log4j2 --web    # only if you want the browser
```

Finding work, without a browser:

```bash
gh issue list -R apache/logging-log4j2 --limit 30
gh issue list -R apache/logging-log4j2 --label bug --state open
gh issue list -R apache/logging-log4j2 --label waiting-for-maintainer
gh issue list -R apache/logging-log4j2 --search "JsonTemplateLayout in:title"
gh issue list -R apache/logging-log4j2 --search "sort:updated-desc"
gh search issues "ThrowableStackTraceRenderer" --repo apache/logging-log4j2   # open and closed
```

Just the fields you want, for scripting:

```bash
gh issue view 4143 -R apache/logging-log4j2 --json title,state,labels,body \
  --jq '{title, state, labels: [.labels[].name]}'

gh issue list -R apache/logging-log4j2 --state open --limit 100 \
  --json number,title,labels \
  --jq '.[] | select(.labels|map(.name)|index("layouts")) | "\(.number)  \(.title)"'
```

### Filing and commenting

> Draft first, then file. New findings get written to Apache's bug template
> under [`issue-drafts/`](issue-drafts/) and read once as text before any of
> these commands run. `docs/ISSUES.md` records what was raised.

```bash
# file from a draft file, never from an inline heredoc you cannot re-read
gh issue create -R apache/logging-log4j2 \
  --title "AbstractDatabaseManager keeps accepting writes after failed startup" \
  --body-file docs/issue-drafts/<draft>.md \
  --label bug

# add the reproduction results to an existing report
gh issue comment 4143 -R apache/logging-log4j2 --body-file repros/issue-4143/README.md

# your own filed issues
gh issue list -R apache/logging-log4j2 --author "@me" --state all
```

The zip cannot be attached from the CLI — GitHub has no API for issue
attachments. Drag `repros/issue-<n>/log4j-issue-<n>-repro.zip` into the comment
box, or push it somewhere and link it.

---

## 2. Upstream pull requests

```bash
oss pr 4240 --repo apache/logging-log4j2    # the facts, for any repository
./bench pr 4240                     # which log4j-* modules it lands in
./bench pr 4240 --diff              # the patch
./bench pr 4240 --checkout          # fetch into ~/apache/logging-log4j2 as pr-4240
./bench pr 4240 --checkout --install    # ...and publish it as 2.27.0-SNAPSHOT
./bench pr 4240 --3x --checkout --install   # 3.x clone, publishes 3.0.0-SNAPSHOT
```

The raw equivalents, when you want something the wrapper does not print:

```bash
gh pr view 4240 -R apache/logging-log4j2
gh pr diff 4240 -R apache/logging-log4j2                    # full patch
gh pr diff 4240 -R apache/logging-log4j2 --name-only        # paths only
gh pr checks 4240 -R apache/logging-log4j2
gh pr view 4240 -R apache/logging-log4j2 --json reviews,comments
gh pr list -R apache/logging-log4j2 --limit 30
gh pr list -R apache/logging-log4j2 --search "review-requested:@me"
```

> `gh pr list` defaults to **`--state open`**, and `--limit` truncates by count,
> not by date. "The last N PRs" and "the PRs from the last N days" are different
> questions, and the first one silently drops everything merged or closed in the
> window. Once, that was ten of twenty-four. When the window is what you mean:
>
> ```bash
> gh pr list -R apache/logging-log4j2 --state all --limit 60 \
>   --search "created:>=2026-07-25" \
>   --json number,title,author,state,createdAt \
>   --jq 'sort_by(.createdAt)|reverse|.[]|"\(.number)\t\(.state)\t\(.createdAt[0:10])\t\(.author.login)\t\(.title[0:58])"'
> ```
>
> The closed ones state the bar more clearly than the open ones do.

Getting the branch by hand — what `--checkout` does:

```bash
cd ~/apache/logging-log4j2
git fetch origin pull/4240/head:pr-4240
git switch pr-4240
mvn install -DskipTests            # now ./bench's 2.27.0-SNAPSHOT is this PR
git switch main                    # when you are done
```

`gh pr checkout 4240` also works and sets up tracking, but it needs the clone to
be the PR's repository; the explicit fetch works from any remote configuration.

### Reviewing

```bash
gh pr comment 4240 -R apache/logging-log4j2 --body-file notes.md
gh pr review  4240 -R apache/logging-log4j2 --comment  --body-file notes.md
gh pr review  4240 -R apache/logging-log4j2 --approve  --body "Ran the bench across 2.24.1→3.0.0-SNAPSHOT on JDK 8/17/21; results below."
gh pr review  4240 -R apache/logging-log4j2 --request-changes --body-file notes.md
```

Say what you ran, on which versions and JDKs, and what you saw. A verdict with
no matrix behind it is worth less than the matrix with no verdict.

---

## 3. This repository

`main` refuses direct pushes — `GH006`, even under `--no-verify`. Everything
goes through a pull request, including yours.

```bash
git switch main && git pull --ff-only
git switch -c my-change
# work
git push -u origin my-change
gh pr create --base main --title "..." --body-file /tmp/body.md
```

```bash
gh pr status                                  # what is open, and where it stands
gh pr view --web                              # the current branch's PR
gh pr checks                                  # CI on the current branch
gh pr merge <n> --squash --delete-branch      # feature → main
```

If you are not a maintainer here, fork first — the flow is otherwise identical,
and `gh repo fork --clone` does it in one command.

`main` is the only long-lived branch. There was a `development` branch in front
of it until the repository went public; the second branch existed to keep CI off
main, and a fork-and-pull-request model already does that. Squash on the way in,
which keeps the history linear.

`git branch --merged` under-reports in this repository — squash merges mean the
branch commits never become ancestors. Ask GitHub instead:

```bash
gh pr list --state merged --limit 20 --json number,title,mergedAt
```

---

## 4. CI

`.github/workflows/bench.yml` runs ~200 cells in ~13 minutes on every PR into
`main`. It skips `**.md`, `docs/**`, `.githooks/**`, `.gitignore`,
`.gitattributes`, `infra/output/**` and `log4j-samples/**` — a docs-only PR
reports no run at all, which is the filter working, not a stuck check.

```bash
gh run list --limit 10
gh run list --branch my-change
gh run watch                       # follow the newest run to completion
gh run view <id>                   # job breakdown
gh run view <id> --log-failed      # only what failed — start here
gh run rerun <id> --failed
gh workflow list
gh workflow run bench.yml --ref my-change     # trigger by hand
```

Upstream CI, for a PR you are reviewing:

```bash
gh run list -R apache/logging-log4j2 --branch <headRefName> --limit 5
gh pr checks 4240 -R apache/logging-log4j2 --watch
```

---

## 5. Following a PR after you reviewed it

`gh` can tell you what a PR looks like now. It cannot tell you what changed since
you last looked, because it has nothing to compare against.
`~/.oss-cli/reviews/ledger.tsv` records the head SHA each
PR was at when it was reviewed, and `oss followup` diffs against it.

```bash
oss followup                 # every reviewed PR, one line each
oss followup --changed       # only the ones that moved
oss followup --mine          # only where the last word is not yours
oss followup 4234            # one PR, in full
oss followup --record 4234     # re-record, AFTER re-reading it
oss followup --comment 4234  # print ONLY the paste-ready block
```

```
  4185  stale-approval SebTardif        pushed,reply:SebTardif
  4234  blocked        katstack         —
```

`pushed` is the one that matters most: the head moved, so your recorded verdict
describes code that is no longer there. The case it exists for is an approval
that went stale — GitHub keeps showing the green check as the branch advances,
so whoever merges reads it as covering code no review ever saw.

`--sync` is deliberately manual. Running it automatically would erase the signal
it exists to show.

The raw equivalents, if you want one without the ledger:

```bash
gh pr view 4234 -R apache/logging-log4j2 --json headRefOid,state,mergedAt

# everything said on it, oldest first
gh pr view 4234 -R apache/logging-log4j2 --json comments,reviews \
  --jq '[(.comments[]|{at:.createdAt,by:.author.login,kind:"comment"}),
         (.reviews[]|{at:.submittedAt,by:.author.login,kind:.state})] | sort_by(.at)[]
        | "\(.at)  \(.kind)  \(.by)"'

# unresolved inline threads — REST cannot express this, GraphQL can
gh api graphql -f query='
{ repository(owner:"apache", name:"logging-log4j2") {
    pullRequest(number:4234) {
      reviewThreads(last:20) { nodes { isResolved isOutdated path
        comments(first:1) { nodes { author{login} body } } } } } } }' \
  --jq '.data.repository.pullRequest.reviewThreads.nodes[]
        | select(.isResolved|not) | "UNRESOLVED \(.path): \(.comments.nodes[0].body[0:80])"'
```

The full playbook this belongs to is [`PR-REVIEW.md`](PR-REVIEW.md).

---

## 6. Recipes worth keeping

Open issues touching a module you just changed:

```bash
gh search issues "log4j-layout-template-json" --repo apache/logging-log4j2 \
  --state open --json number,title --jq '.[] | "\(.number)  \(.title)"'
# --state on `gh search` takes only open|closed — omit it to search both
```

What changed upstream since the snapshot you last built:

```bash
cd ~/apache/logging-log4j2 && git fetch origin
git log --oneline HEAD..origin/main | head -20
```

Every version the bench can select, as a `--log4j` list:

```bash
./bench list --versions | sed 's/^/--log4j /' | tr '\n' ' '
```

Which upstream release a fix landed in:

```bash
gh api repos/apache/logging-log4j2/commits/<sha>/pulls --jq '.[].milestone.title'
```

Rate limit, when a loop of `gh` calls starts failing for no visible reason:

```bash
gh api rate_limit --jq '.rate | "\(.remaining)/\(.limit), resets \(.reset)"'
```
