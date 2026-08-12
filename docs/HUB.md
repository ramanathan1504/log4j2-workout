# `bench hub` — the local site over all three repos

One page on `http://localhost:8787/`, served from the working trees as they are
right now. No build step, no watcher: commit in any of the three and reload.

```
bench hub                  serve, and open a browser
bench hub --pr 4245        straight into the review composer for one PR
bench hub --no-open        serve only (what launchd runs)
bench hub --once           write index.html and exit
bench hub --sync all       run every sync job, then exit
bench hub --sync report    one job — fetch, todo, report, triage or all
bench hub --install        install the launchd agent, so it is always up
```

## The views

| View | Reads | Writes |
|---|---|---|
| To do | GitHub, cached in `.bench/hub/todo.json` | — |
| Send a review | a PR's diff, cached by head SHA | **posts to GitHub** |
| Daily report | the three clones + your own GitHub repos | its own cache |
| Overview & status | the three working trees | — |
| Reviews | `~/.oss-cli/reviews/`, the ledger (`docs/pr-reviews/` is the archive) | — |
| Backlog triage | knowledge-creator's `triage.sh` report | — |

Exactly one of them writes to GitHub. It posts nothing until Send is pressed
**and** confirmed, and `BENCH_HUB_READONLY=1` refuses it on the server.

## Daily report

Answers *what happened today, across the three repos* — and only those three.
Upstream is deliberately not in it; the one view that looks at
`apache/logging-log4j2` is the To-do board, and it reads.

Per repo, for the day: commits on **every local branch** (a day's work often
sits on a feature branch that was never merged), lines added and removed, the
branch, anything uncommitted or unpushed, and pull requests on your own repo
that opened, merged or closed that day.

- One file per day under `.bench/hub/report/YYYY-MM-DD.json`. A day that has
  passed does not change, so it is never rewritten; today's is rebuilt every
  half hour while the hub runs.
- **It becomes daily on its own.** At midnight the day's filename changes, that
  file does not exist, and the next pass of the background refresher writes it.
  Nothing has to be pressed and nothing has to be scheduled separately.
- The last 60 days are kept. Older files are deleted as new ones are written.
- Past days are one click away, under *Days before this one*, or `?day=YYYY-MM-DD`.
- Offline or logged out of `gh`, the git half still works and the pull-request
  half is empty rather than failing the report.

To fill in history from before the report existed, derive past days from git:

```python
python3 - <<'EOF'
import importlib.util
from datetime import date, timedelta
spec = importlib.util.spec_from_file_location("hub", "scripts/hub.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
for i in range(1, 30):
    m.report_build((date.today() - timedelta(days=i)).strftime("%Y-%m-%d"))
EOF
```

## Always up, including after a restart

`bench hub --install` renders `infra/launchd/com.ramanathan.bench-hub.plist`
into `~/Library/LaunchAgents/` and bootstraps it. It declares `RunAtLoad` and
`KeepAlive`, so it starts at login — **after a reboot the hub is up before you
open a browser** — and comes back in about 25 seconds if it dies.

The template is kept in the repository so a rebuilt machine reinstalls it from
here rather than from memory. `launchd`'s default `PATH` has no `git`, `gh` or
`python3`, so the plist sets one; without it the page still serves and the
boards silently stay empty, which looks like it is working.

```
launchctl print gui/$(id -u)/com.ramanathan.bench-hub   # state, pid, last exit
launchctl kickstart -k gui/$(id -u)/com.ramanathan.bench-hub   # restart it
tail -f .bench/hub/agent.err.log                        # what it is doing
```

**After changing `scripts/hub.py`, kickstart the agent** — the running one is on
the old code until it is restarted.

## Sending a review, safely

The composer loads the diff at a known head SHA, you write line comments against
it, and Send names the repository, the PR and the event in a confirmation before
anything is posted. Every anchor is checked against the diff **here**, before the
POST: a line that is not part of the diff is a sentence on the page rather than a
422 with a review already created upstream. See `docs/UPSTREAM-INCIDENT.md` for
why that check exists.
