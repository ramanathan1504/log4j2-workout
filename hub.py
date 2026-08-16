#!/usr/bin/env python3
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
"""
hub.py — one local site over all three repos.

    oss run hub                 serve on http://localhost:8787, and open it
    oss run hub --pr 4245       ...straight into the review composer for one PR
    oss run hub --port 9000
    oss run hub --no-open       serve only, no browser
    oss run hub --once          write index.html and exit, no server
    oss run hub --sync all      pull everything the page can pull, then exit
    oss run hub --sync triage   one job only — fetch, todo, triage or all

The page is regenerated on every request, from the working tree as it is right
now. There is no build step, no cache and no watcher to fall out of sync: if you
commit in any of the three repos and reload, you are looking at the new state.

Stdlib only, deliberately. This machine has no markdown library and the site has
to keep working on a laptop with no network, so the renderer below is a small
subset renderer rather than a dependency.

It reads the three working trees; it never fetches, pulls or commits in them.
Two views are the exception, because they answer questions the working tree
cannot: the To-do board calls the GitHub API, and Backlog triage shells out to
`oss backlog`, which writes its report there. Both are cached,
both say how old they are, and neither runs on the request path.

One view writes: **Send a review** posts to GitHub, through `gh`, as you. It is
the only one, it posts nothing until Send is pressed and confirmed, and
BENCH_HUB_READONLY=1 takes even that away.
"""

import argparse
import fcntl
import html
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import webbrowser
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, urlparse
from urllib.request import urlopen

HOME = Path.home()
# The pack root, which is where this file now sits. It used to be scripts/hub.py,
# so `.parent.parent` was right; the pack-split commit moved it up one level and
# left this line alone, and WORKOUT has pointed at ~/apache -- the parent of every
# clone -- ever since. Nothing announced it: the template lookup, the .bench cache
# and the todo board all just resolved somewhere that does not exist.
WORKOUT = Path(__file__).resolve().parent
OSSCLI = Path(os.environ.get("BENCH_OSSCLI_DIR") or HOME / "own repo" / "oss-cli")
KB = Path(os.environ.get("BENCH_KB_DIR") or HOME / "own repo" / "knowledge-creator")

# The core first, then what plugs into it. Order is the message: three peers in a
# list read as three projects you have to hold in your head, which is what this
# page looked like and what it is not. OSS-CLI is the one that knows; a bench and
# a kb are the two things it cannot do alone.
# This page is about this bench. The other repositories are presented by
# `oss-cli serve`, which owns that job -- listing them here too meant two pages
# describing the same thing, which is two pages that must be kept in step.
REPOS = [
    ("log4j2-workout", WORKOUT, "the bench", "real apps, real JVMs, the version × config × app matrix"),
]

# Same ordering, and the label carries the relationship rather than just a repo
# name. `kind` is what the sidebar groups by.
# The operator documents moved to the knowledge base, where they are indexed and
# findable without knowing this repository exists. What is left is what still
# describes the checkout itself.
DOCS = [
    ("log4j2-workout", "bench", WORKOUT, ["README.md", "CLAUDE.md", "CONTRIBUTING.md"]),
]

# ---------------------------------------------------------------- markdown ---
INLINE = [
    (re.compile(r"`([^`]+)`"), lambda m: f"<code>{html.escape(m.group(1))}</code>"),
    (re.compile(r"\*\*([^*]+)\*\*"), lambda m: f"<strong>{m.group(1)}</strong>"),
    (re.compile(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])"), lambda m: f"<em>{m.group(1)}</em>"),
    (re.compile(r"~~([^~]+)~~"), lambda m: f"<del>{m.group(1)}</del>"),
    (re.compile(r"\[([^\]]+)\]\(([^)]+)\)"),
     lambda m: f'<a href="{html.escape(m.group(2), quote=True)}">{m.group(1)}</a>'),
]


def inline(text):
    """Escape first, then apply spans — so a stray < in prose cannot open a tag."""
    out = html.escape(text)
    # code spans first: their content must not be re-processed for emphasis
    parts, last = [], 0
    for m in re.finditer(r"`([^`]+)`", out):
        parts.append(("text", out[last:m.start()]))
        parts.append(("code", m.group(1)))
        last = m.end()
    parts.append(("text", out[last:]))
    rendered = []
    for kind, chunk in parts:
        if kind == "code":
            rendered.append(f"<code>{chunk}</code>")
            continue
        for pat, fn in INLINE[1:]:
            chunk = pat.sub(fn, chunk)
        rendered.append(chunk)
    return "".join(rendered)


def md_to_html(text):
    lines, out, i = text.split("\n"), [], 0
    list_stack = []

    def close_lists(to=0):
        while len(list_stack) > to:
            out.append(f"</{list_stack.pop()}>")

    while i < len(lines):
        line = lines[i]

        if line.startswith("```"):
            lang = line[3:].strip()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            close_lists()
            cls = f' class="lang-{html.escape(lang)}"' if lang else ""
            out.append(f"<pre{cls}><code>{html.escape(chr(10).join(buf))}</code></pre>")
            continue

        if not line.strip():
            close_lists()
            i += 1
            continue

        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            close_lists()
            lvl = len(m.group(1))
            text_ = inline(m.group(2))
            slug = re.sub(r"[^a-z0-9]+", "-", re.sub("<[^>]+>", "", m.group(2)).lower()).strip("-")
            out.append(f'<h{lvl} id="{slug}">{text_}</h{lvl}>')
            i += 1
            continue

        if re.match(r"^\s*([-*_])\s*\1\s*\1[\s\-*_]*$", line):
            close_lists()
            out.append("<hr>")
            i += 1
            continue

        # table: a header row followed by a |---| separator
        if line.lstrip().startswith("|") and i + 1 < len(lines) \
                and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            close_lists()
            def cells(row):
                return [c.strip() for c in row.strip().strip("|").split("|")]
            head = cells(line)
            i += 2
            body = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                body.append(cells(lines[i]))
                i += 1
            th = "".join(f"<th>{inline(c)}</th>" for c in head)
            rows = "".join(
                "<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in body)
            out.append(f'<div class="tw"><table><thead><tr>{th}</tr></thead>'
                       f"<tbody>{rows}</tbody></table></div>")
            continue

        if line.lstrip().startswith(">"):
            close_lists()
            buf = []
            while i < len(lines) and lines[i].lstrip().startswith(">"):
                buf.append(lines[i].lstrip()[1:].lstrip())
                i += 1
            out.append(f"<blockquote>{md_to_html(chr(10).join(buf))}</blockquote>")
            continue

        m = re.match(r"^(\s*)([-*+]|\d+[.)])\s+(.*)$", line)
        if m:
            indent, marker, rest = len(m.group(1)), m.group(2), m.group(3)
            tag = "ol" if marker[0].isdigit() else "ul"
            depth = indent // 2 + 1
            while len(list_stack) > depth:
                out.append(f"</{list_stack.pop()}>")
            if len(list_stack) < depth:
                out.append(f"<{tag}>")
                list_stack.append(tag)
            out.append(f"<li>{inline(rest)}</li>")
            i += 1
            continue

        close_lists()
        # Always consume the current line first. Every guard below can be true of
        # the line we are standing on — a `|` row whose table has no separator is
        # the common one — and without this the loop body appends nothing, never
        # advances i, and the generator hangs instead of failing.
        buf = [lines[i]]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(("#", "```", ">")) \
                and not re.match(r"^\s*([-*+]|\d+[.)])\s+", lines[i]) \
                and not lines[i].lstrip().startswith("|"):
            buf.append(lines[i])
            i += 1
        out.append("<p>" + inline(" ".join(buf)) + "</p>")

    close_lists()
    return "\n".join(out)


# ------------------------------------------------------------------- state ---
def git(repo, *args):
    try:
        r = subprocess.run(["git", "-C", str(repo), *args],
                           capture_output=True, text=True, timeout=10)
        return r.stdout.strip() if r.returncode == 0 else ""
    except Exception:
        return ""


def repo_state(path, command=None):
    """Working-tree state, or -- for a repo that is INSTALLED rather than cloned -- what is
    installed instead.

    oss-cli ships through Homebrew now, so the normal state of that entry is "no clone here, and
    that is correct". Reporting it as a missing checkout made a healthy machine look broken, and
    invited re-cloning something that does not need to be cloned. A core you install and extensions
    you attach is the whole shape of this thing; the page should say so."""
    if not (path / ".git").exists():
        if command:
            found = installed(command)
            if found:
                ver = ""
                try:
                    r = subprocess.run([command, "--version"], capture_output=True, text=True, timeout=20)
                    # NOT the first non-empty line: the CLI prints "Initializing local SQLite
                    # database connection..." before its own output, so first-line wins put that
                    # in the version pill. Take the line that actually names the tool.
                    ver = next((l.strip() for l in (r.stdout or "").splitlines()
                                if l.strip().startswith(command + " ")), "")
                except Exception:
                    pass
                return {"ok": True, "installed": True, "path": found,
                        "head": ver or "installed", "when": "not a clone — installed"}
        return {"ok": False, "why": f"no git clone at {path}"}
    dirty = [l for l in git(path, "status", "--porcelain").split("\n") if l.strip()]
    counts = git(path, "rev-list", "--left-right", "--count", "@{u}...HEAD")
    behind = ahead = 0
    if counts and len(counts.split()) == 2:
        behind, ahead = (int(x) for x in counts.split())
    return {
        "ok": True,
        "path": str(path),
        "branch": git(path, "rev-parse", "--abbrev-ref", "HEAD"),
        "head": git(path, "log", "--oneline", "-1"),
        "when": git(path, "log", "-1", "--format=%cr"),
        "dirty": len(dirty),
        "dirty_files": dirty[:12],
        "behind": behind,
        "ahead": ahead,
    }


def installed(cmd):
    r = subprocess.run(["/bin/bash", "-lc", f"command -v {cmd} || true"],
                       capture_output=True, text=True, timeout=10)
    return r.stdout.strip()


# --------------------------------------------------------------------- todo ---
# Whose turn is it? That is the only question worth a page, and answering it
# needs GitHub, not the working tree. So it is cached: the page renders from
# .bench/hub/todo.json instantly and says how old it is, and a refresh runs in
# the background rather than blocking a page load behind twenty API calls.
TODO_CACHE = WORKOUT / ".bench" / "hub" / "todo.json"
UPSTREAM = os.environ.get("BENCH_UPSTREAM_REPO", "apache/logging-log4j2")


def gh_json(*args, timeout=60):
    try:
        r = subprocess.run(["gh", *args], capture_output=True, text=True, timeout=timeout)
        return json.loads(r.stdout) if r.returncode == 0 and r.stdout.strip() else None
    except Exception:
        return None


# ---------------------------------------------------------------- oss ask ---
# One bridge to the CLI, deliberately narrow.
#
# The question "have I worked this out before?" belongs on the row you are
# deciding about, not on a page of its own. A separate Search section would be a
# second place to go and a second thing to remember, and you would still have to
# retype the title you were already looking at.
#
# What makes this safe is that it is not a shell. ASKABLE is a fixed map from a
# name the page may use to an argv template, every command is read-only, and the
# only thing the caller supplies is the text -- passed as one argv element, never
# through a shell, so quoting and metacharacters have nothing to act on. A name
# that is not in the map is a 404 before anything is executed.
#
# It runs `oss`, not this file's own logic, so the answer is the same answer the
# terminal gives. If the two ever disagreed, the page would be the one lying.
ASKABLE = {
    # have I seen this before -- the whole reason this bridge exists
    "search": ["oss", "search"],
    # what moved on the things I already reviewed
    "followup": ["oss", "followup", "--changed"],
}
ASK_LIMIT = 8000


def ask(name, text):
    """Run one allowlisted read-only `oss` command and return its output as text."""
    argv = ASKABLE.get(name)
    if argv is None:
        return {"error": f"not askable: {name}"}
    text = (text or "").strip()
    if argv[-1] == "search":
        if not text:
            return {"error": "nothing to search for"}
        # One element. Not a shell, so a title full of quotes and backticks is
        # just a title.
        argv = [*argv, text[:200]]
    try:
        r = subprocess.run(argv, capture_output=True, text=True, timeout=90)
    except FileNotFoundError:
        return {"error": "oss is not on PATH for this server"}
    except subprocess.TimeoutExpired:
        return {"error": "timed out after 90s"}
    out = (r.stdout or "") + (("\n" + r.stderr) if r.returncode != 0 and r.stderr else "")
    out = out.strip()
    # A command that answers nothing is not an error, and saying "no output"
    # is a worse answer than saying what it means.
    if not out:
        out = "nothing recorded yet" if argv[1] == "search" else "nothing has moved"
    return {"cmd": " ".join(argv), "out": out[:ASK_LIMIT]}


def ledger_map():
    """Pull request number -> what you recorded about it, and where.

    Column 0 is the REPOSITORY, not the pull request. The ledger gained that
    column when it went multi-project -- "PR 4234" means nothing once you follow
    two projects -- and this reader was never updated. Every key became the
    string "apache/logging-log4j2", and the to-do board then died on
    int("apache/logging-log4j2") before it could draw a single row.

    Invisible until now, because the launchd agent pointed at a script that no
    longer existed, so the page this feeds never started.

    The older seven-column shape is still read: a ledger written before the
    change is still a ledger, and it was all against the one repository.
    """
    out = {}
    f = REVIEW_DIR / "ledger.tsv"
    if f.exists():
        for line in f.read_text().split("\n"):
            if not line.strip() or line.startswith("#"):
                continue
            c = line.split("\t")
            if len(c) >= 8:
                repo, row = c[0], c[1:]
            elif len(c) >= 7:
                repo, row = UPSTREAM, c
            else:
                continue
            if not row[0].strip().isdigit():
                continue  # a header or a malformed line must not become a target
            out[row[0]] = {"repo": repo, "verdict": row[1], "when": row[2], "sha": row[3],
                           "author": row[4], "posted": row[5], "note": row[6]}
    return out


def todo_fetch():
    """One row per PR you have touched. Bucketed by whose move it is."""
    r = subprocess.run(["gh", "api", "user", "--jq", ".login"],
                       capture_output=True, text=True, timeout=30)
    me = r.stdout.strip() or "me"

    # Two questions, two queries. What am I reviewing (Log4j, where the ledger
    # lives), and what have I opened anywhere in the open-source world.
    # --visibility=public keeps work repos out; they are not this dashboard's job.
    involved = gh_json("search", "prs", "--involves=@me", f"--repo={UPSTREAM}",
                       "--state=open", "--limit=60",
                       "--json", "number,title,author,updatedAt") or []
    authored = gh_json("search", "prs", "--author=@me", "--state=open",
                       "--visibility=public", "--limit=60",
                       "--json", "number,title,repository,updatedAt") or []
    led = ledger_map()

    targets = {(UPSTREAM, str(p["number"])) for p in involved}
    # Each row carries its own repository now, so a ledger spanning two projects
    # no longer files every one of them under the Log4j upstream.
    targets |= {(v.get("repo") or UPSTREAM, n) for n, v in led.items()}
    targets |= {(p["repository"]["nameWithOwner"], str(p["number"])) for p in authored}
    # Your own repos are not follow-up: nobody is waiting on you there, you just
    # merge them. This board is for work in someone else's project.
    targets = {(r, n) for r, n in targets if not r.lower().startswith(f"{me.lower()}/")}

    rows = []
    # Sorted defensively: one non-numeric target used to abort the whole board
    # with a ValueError rather than skip the row that caused it.
    for repo, n in sorted(targets, key=lambda t: (t[0], -(int(t[1]) if t[1].isdigit() else 0))):
        d = gh_json("pr", "view", n, "--repo", repo, "--json",
                    "number,title,author,state,isDraft,updatedAt,headRefOid,"
                    "reviews,comments,mergeable,reviewDecision,statusCheckRollup",
                    timeout=45)
        if not d:
            continue
        # Match the row's own repository rather than assuming the upstream one.
        l = led.get(n)
        if l and (l.get("repo") or UPSTREAM) != repo:
            l = None
        mine = [x for x in (d.get("reviews") or []) if (x.get("author") or {}).get("login") == me]
        my_last = mine[-1] if mine else None
        events = [(c.get("createdAt", ""), (c.get("author") or {}).get("login", ""))
                  for c in (d.get("comments") or [])]
        events += [(x.get("submittedAt", ""), (x.get("author") or {}).get("login", ""))
                   for x in (d.get("reviews") or [])]
        events = [e for e in events if e[0]]
        events.sort()
        last_word = events[-1][1] if events else ""
        moved = bool(l and l["sha"] and d.get("headRefOid") and l["sha"] != d["headRefOid"])

        author = (d.get("author") or {}).get("login", "")
        role = "mine" if author == me else "review"
        decision = d.get("reviewDecision") or ""
        checks = [c.get("conclusion") or c.get("state") or ""
                  for c in (d.get("statusCheckRollup") or [])]
        ci_bad = any(c in ("FAILURE", "TIMED_OUT", "CANCELLED", "ERROR", "ACTION_REQUIRED")
                     for c in checks)
        unsent = bool(l and l["posted"] == "no")

        # Whose move is it? Decided from GitHub state, not from the ledger — an
        # unsent draft is a flag on the row, not a bucket, or every reviewed PR
        # piles into "yours" and the list stops sorting anything.
        if d["state"] != "OPEN":
            bucket, why = "closed", d["state"].lower()
        elif role == "mine":
            if decision == "CHANGES_REQUESTED":
                bucket, why = "you", "changes requested on your PR"
            elif ci_bad:
                bucket, why = "you", "CI is failing on your PR"
            elif last_word and last_word != me:
                bucket, why = "you", f"@{last_word} replied and you have not"
            elif decision == "APPROVED":
                bucket, why = "them", "approved — waiting on a committer to merge"
            else:
                bucket, why = "them", "waiting on review"
        elif d.get("isDraft"):
            bucket, why = "them", "still a draft"
        elif moved:
            bucket, why = "you", "author pushed since you reviewed"
        elif last_word and last_word != me:
            bucket, why = "you", f"@{last_word} had the last word"
        elif not l and not mine:
            bucket, why = "you", "you are involved but have not reviewed it"
        elif (my_last or {}).get("state") == "CHANGES_REQUESTED":
            bucket, why = "them", "you requested changes; nothing new since"
        elif unsent:
            bucket, why = "you", "your review is written but never posted"
        else:
            bucket, why = "them", "reviewed; nothing has moved"
        if unsent and bucket != "you":
            why += " · draft unsent"

        rows.append({
            "pr": n, "repo": repo, "title": d.get("title", ""),
            "author": author, "role": role,
            "state": d.get("state", ""), "draft": d.get("isDraft", False),
            "updated": (d.get("updatedAt") or "")[:10],
            "verdict": (l or {}).get("verdict", "—"),
            "posted": (l or {}).get("posted", "—"), "unsent": unsent,
            "reviewed": (l or {}).get("when", "—"),
            "my_review": (my_last or {}).get("state", "—"),
            "decision": decision or "—", "ci_bad": ci_bad,
            "moved": moved, "bucket": bucket, "why": why,
        })
    return {"me": me, "repo": UPSTREAM, "rows": rows,
            "at": datetime.now(timezone.utc).isoformat(timespec="seconds")}


def todo_load():
    if not TODO_CACHE.exists():
        return None, None
    try:
        d = json.loads(TODO_CACHE.read_text())
        age = (datetime.now(timezone.utc)
               - datetime.fromisoformat(d["at"])).total_seconds()
        return d, age
    except Exception:
        return None, None


def todo_refresh():
    TODO_CACHE.parent.mkdir(parents=True, exist_ok=True)
    d = todo_fetch()
    TODO_CACHE.write_text(json.dumps(d, indent=1))
    return d


def reviews():
    """The ledger is the record of what was reviewed; .bench/reviews is evidence."""
    rows = []
    ledger = REVIEW_DIR / "ledger.tsv"
    if ledger.exists():
        for line in ledger.read_text().split("\n"):
            if not line.strip() or line.startswith("#"):
                continue
            f = line.split("\t")
            if len(f) >= 7:
                rows.append({"pr": f[0], "state": f[1], "when": f[2],
                             "author": f[4], "posted": f[5], "note": f[6]})
    rows.sort(key=lambda r: r["pr"], reverse=True)
    evidence = sorted(
        (p.parent.name for p in (WORKOUT / ".bench" / "reviews").glob("*/00-SUMMARY.md")),
        reverse=True)
    files = {p.name.split("-")[0]: p.name
             for p in REVIEW_DIR.glob("*.md")
             if p.name[0].isdigit()}
    return rows, evidence, files


# ----------------------------------------------------------------- compose ---
# The one view here that writes to GitHub. Everything else reads: the working
# tree, a cache, a report. A review leaves this machine and lands under your name
# in someone else's project, so it is three deliberate steps — load the diff at a
# known head SHA, write the comments, then a Send that names the repository, the
# pull request and the event in a confirmation before anything is posted. Loading
# a page, opening a PR and typing all post nothing.
PR_DIR = WORKOUT / ".bench" / "hub" / "pr"
# Reviews and their ledger moved out of this repository when following a pull
# request moved into the core: they outlive every checkout that produced them,
# and `oss followup` reads the same files. OSS_CLI_HOME relocates both together.
REVIEW_DIR = Path(os.environ.get("OSS_CLI_HOME", str(Path.home() / ".oss-cli"))) / "reviews"

# One switch that makes the composer read-only — for an unattended hub under
# launchd, or a machine you are not the only one at. The boxes still open and the
# preview still renders; Send refuses, on the server, not just in the page.
READONLY = (os.environ.get("BENCH_HUB_READONLY") or "0") not in ("0", "", "no", "false")

# What GitHub calls the review event, and what it means when you are the one
# choosing. ISSUE_COMMENT is not a review at all — it is the plain conversation
# comment, kept here because "just say something" is half of what a review round
# actually is, and it is the only one of the four that cannot carry line comments.
EVENTS = [
    ("COMMENT", "Comment", "feedback, no verdict"),
    ("REQUEST_CHANGES", "Request changes", "blocks the merge until you clear it"),
    ("APPROVE", "Approve", "you cannot approve your own PR"),
    ("ISSUE_COMMENT", "Plain comment", "on the conversation — not a review"),
]
EVENT_NAMES = {e[0] for e in EVENTS}


def draft_body(pr):
    """The paste-ready block from this PR's write-up, or nothing.

    Same rule as `oss run followup --comment`, and for the same reason: a review
    file is mostly notes to yourself, and only the block under
    `── paste-ready comment ──` is addressed to the author. A file may carry one
    block per PR it covers, so prefer the one naming this number.
    """
    files = sorted(REVIEW_DIR.glob(f"*{pr}*.md"))
    if not files:
        return "", ""
    f = files[0]
    keep, out = False, []
    for line in f.read_text(errors="replace").split("\n"):
        if re.match(r"^##\s+.*paste-ready comment", line):
            keep = (f"#{pr}" in line) or ("for #" not in line)
            continue
        if line.startswith("## "):
            keep = False
            continue
        if keep:
            out.append(line)
    return "\n".join(out).strip("\n"), f.name


def parse_patch(patch):
    """A unified diff into hunks whose every line knows both its line numbers.

    That is the whole point of parsing it here rather than showing the raw patch:
    GitHub anchors a review comment by `path` + `line` + `side`, where the number
    is the line in the file on that side — not an offset into the diff. Counting
    it wrong does not fail, it comments on the wrong code.
    """
    hunks, old, new = [], 0, 0
    # rstrip first: a patch that ends in a newline would otherwise gain a phantom
    # empty context line, and with it a commentable line number past the hunk.
    for raw in (patch or "").rstrip("\n").split("\n"):
        m = re.match(r"^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)$", raw)
        if m:
            old, new = int(m.group(1)), int(m.group(2))
            hunks.append({"header": raw, "lines": []})
            continue
        if not hunks or raw.startswith("\\"):  # "\ No newline at end of file"
            continue
        t, text = raw[:1], raw[1:]
        if t == "+":
            hunks[-1]["lines"].append({"t": "+", "old": None, "new": new, "text": text})
            new += 1
        elif t == "-":
            hunks[-1]["lines"].append({"t": "-", "old": old, "new": None, "text": text})
            old += 1
        else:
            # A context line, including the empty one a patch writes as "".
            hunks[-1]["lines"].append({"t": " ", "old": old, "new": new, "text": text})
            old += 1
            new += 1
    return hunks


def pr_bundle(repo, pr, force=False):
    """Head, files and hunks for one PR, cached by head SHA.

    Cached by SHA rather than by number, which is oss-cli's rule and the right one
    here too: a push moves the SHA and invalidates the cache by itself, so the
    composer can never anchor a comment to a line that has since been rewritten.
    """
    meta = gh_json("pr", "view", str(pr), "--repo", repo, "--json",
                   "number,title,author,state,isDraft,url,headRefOid,baseRefName,"
                   "additions,deletions,changedFiles", timeout=45)
    if not meta:
        raise RuntimeError(f"could not read {repo}#{pr} — check `gh auth status`")
    sha = meta.get("headRefOid") or ""
    cache = PR_DIR / f"{repo.replace('/', '__')}-{pr}.json"

    d = None
    if not force and cache.exists():
        try:
            c = json.loads(cache.read_text())
            if c.get("sha") == sha:
                d = c
                d["cached"] = True
        except Exception:
            d = None

    if d is None:
        r = subprocess.run(["gh", "api", f"repos/{repo}/pulls/{pr}/files",
                            "--paginate", "--jq", ".[]"],
                           capture_output=True, text=True, timeout=180)
        if r.returncode != 0:
            raise RuntimeError((r.stderr.strip() or "gh api failed").split("\n")[-1])
        files = []
        for line in r.stdout.split("\n"):
            if not line.strip():
                continue
            f = json.loads(line)
            files.append({
                "path": f.get("filename", ""), "status": f.get("status", ""),
                "adds": f.get("additions", 0), "dels": f.get("deletions", 0),
                # A binary, renamed-only or too-large file comes back with no
                # patch. Say so — an empty box reads as "nothing changed here".
                "hunks": parse_patch(f.get("patch")), "nopatch": not f.get("patch")})
        d = {"repo": repo, "pr": str(pr), "sha": sha, "files": files,
             "at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
             "cached": False}
        PR_DIR.mkdir(parents=True, exist_ok=True)
        cache.write_text(json.dumps(d))

    # Everything below is cheap and changes without the SHA moving — the title on
    # an edit, the state on a merge, the draft when you save the write-up — so it
    # is refreshed on every load rather than frozen into the cache.
    draft, draft_file = draft_body(pr)
    led = ledger_map().get(str(pr)) or {}
    d.update({
        "title": meta.get("title", ""), "url": meta.get("url", ""),
        "author": (meta.get("author") or {}).get("login", ""),
        "state": meta.get("state", ""), "wip": meta.get("isDraft", False),
        "base": meta.get("baseRefName", ""), "adds": meta.get("additions", 0),
        "dels": meta.get("deletions", 0), "changed": meta.get("changedFiles", 0),
        "draft": draft, "draft_file": draft_file,
        "verdict": led.get("verdict", ""), "posted": led.get("posted", ""),
        "in_ledger": bool(led), "readonly": READONLY})
    return d


# Which repository this server has been approved to write to, for this run only.
# Set from `--approve-upstream owner/name` at startup; never persisted, never
# defaulted, and never inferred from the repository being viewed.
APPROVED_UPSTREAM = ""


def guard_ok(target_repo, typed):
    """Refuse any outward write that was not approved by name and confirmed now.

    Two independent things must both be true, and neither can be switched on
    permanently:

      1. The server was started with `--approve-upstream owner/name` naming THIS
         repository. An approval for one repository is not an approval for
         another, which is why the name is compared rather than merely present.
      2. The sender retyped the repository name for this specific send. A yes/no
         dialog is answered by reflex; retyping the target is the smallest thing
         that cannot be done without reading it.

    There is no stored credential and no setting that satisfies this, because
    either becomes a thing switched on once and then forgotten -- after which the
    protection exists only in the belief that it exists.
    """
    target = (target_repo or "").strip()
    if not target:
        raise RuntimeError("refused — the target repository is not known, so no approval can apply")
    if not APPROVED_UPSTREAM:
        raise RuntimeError(
            f"refused — this hub was not started with --approve-upstream {target}. "
            "Nothing was sent. Restart it with that flag to permit writes to that repository.")
    if APPROVED_UPSTREAM.lower() != target.lower():
        raise RuntimeError(
            f"refused — this hub is approved for {APPROVED_UPSTREAM}, not {target}. "
            "An approval for one repository is not an approval for another.")
    if (typed or "").strip().lower() != target.lower():
        raise RuntimeError(f"refused — type {target} exactly to confirm this post. Nothing was sent.")
    return True


def api_post(path, payload):
    """POST through `gh`, so this uses the same credential the CLI does and no
    token is ever read, stored or logged by this server."""
    r = subprocess.run(["gh", "api", "--method", "POST", path, "--input", "-"],
                       input=json.dumps(payload), capture_output=True,
                       text=True, timeout=120)
    if r.returncode != 0:
        msg = (r.stderr.strip() or r.stdout.strip() or "gh api failed")
        # GitHub's own message is far better than gh's exit line — "line must be
        # part of the diff" is the one you will actually hit — so prefer it.
        try:
            j = json.loads(r.stdout)
            msg = j.get("message") or msg
            if j.get("errors"):
                msg += " — " + "; ".join(
                    str(e.get("message") or e.get("field") or e) for e in j["errors"])
        except Exception:
            pass
        raise RuntimeError(" ".join(msg.split())[:500])
    return json.loads(r.stdout) if r.stdout.strip() else {}


def clean_comment(c):
    """One line comment, validated here rather than at GitHub. `side` decides
    which file the number counts in: RIGHT is the head, LEFT is the base."""
    path = (c.get("path") or "").strip()
    body = (c.get("body") or "").strip()
    side = (c.get("side") or "RIGHT").upper()
    if not path:
        raise RuntimeError("a line comment with no file")
    if not body:
        raise RuntimeError(f"a line comment on {path} with no text")
    if side not in ("LEFT", "RIGHT"):
        raise RuntimeError(f"bad side: {side}")
    try:
        line = int(c.get("line"))
    except (TypeError, ValueError):
        raise RuntimeError(f"a line comment on {path} with no line number")
    out = {"path": path, "body": body, "line": line, "side": side}
    start = c.get("start_line")
    if start not in (None, "", line):
        out["start_line"] = int(start)
        out["start_side"] = (c.get("start_side") or side).upper()
    return out


def commentable(repo, pr):
    """Which lines GitHub will accept a comment on, per file and per side.

    A review comment must anchor to a line that is *part of the diff*, and the
    only answer GitHub gives for a line that is not is a 422 on the POST — after
    the review exists. The bundle already carries every hunk line with its old
    and new number, so the same question is answerable here, before anything is
    sent. RIGHT counts in the head file, LEFT in the base file, which is why
    they are two sets and not one.

    Returns None when the diff is not available, and the caller falls back to
    letting GitHub decide — a check that cannot run is not a reason to refuse.
    """
    try:
        d = pr_bundle(repo, pr)
    except Exception:
        return None
    out = {}
    for f in d.get("files") or []:
        right, left = set(), set()
        for h in f.get("hunks") or []:
            for ln in h.get("lines") or []:
                if ln.get("new") is not None:
                    right.add(int(ln["new"]))
                if ln.get("old") is not None:
                    left.add(int(ln["old"]))
        out[f.get("path", "")] = {"RIGHT": right, "LEFT": left}
    return out or None


def spans(nums):
    """`92-104, 118` — the commentable lines of a file, said the short way."""
    out, run = [], []
    for n in sorted(nums):
        if run and n == run[-1] + 1:
            run.append(n)
        else:
            if run:
                out.append(run)
            run = [n]
    if run:
        out.append(run)
    return ", ".join(str(r[0]) if len(r) == 1 else f"{r[0]}-{r[-1]}" for r in out)


def check_anchors(repo, pr, comments):
    """Refuse a bad line here, naming the lines that would have worked.

    This is the check that makes posting to a live pull request an unnecessary
    way to find out whether an anchor resolves. See the knowledge base:
    Reference/unauthorised-writes-to-apachelogging-log4j2.
    """
    ok = commentable(repo, pr)
    if not ok:
        return
    for c in comments:
        path, side = c["path"], c["side"]
        if path not in ok:
            raise RuntimeError(
                f"{path} is not in this diff — nothing changed in it, so no line "
                f"of it can be commented on")
        for label, num, sd in (("line", c["line"], side),
                               ("start_line", c.get("start_line"), c.get("start_side", side))):
            if num is None:
                continue
            if int(num) not in ok[path][sd]:
                where = "head" if sd == "RIGHT" else "base"
                have = spans(ok[path][sd])
                raise RuntimeError(
                    f"{path.split('/')[-1]}:{num} ({label}, {where} side) is not part "
                    f"of the diff — commentable {where} lines in that file: "
                    f"{have or 'none'}")


def ledger_mark_posted(pr, sha):
    """Sending makes two ledger columns true at the same moment: the review is
    posted, and it was posted against this head. `oss run followup --sync` writes
    the second; there is no reason to make you run it by hand after a Send."""
    f = REVIEW_DIR / "ledger.tsv"
    if not f.exists():
        return ""
    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out, hit = [], False
    for line in f.read_text().split("\n"):
        c = line.split("\t")
        if not line.strip() or line.startswith("#") or len(c) < 7 or c[0] != str(pr):
            out.append(line)
            continue
        c[2], c[5] = stamp, "yes"
        if sha:
            c[3] = sha
        out.append("\t".join(c))
        hit = True
    if not hit:
        return ""
    f.write_text("\n".join(out))
    return f"ledger: #{pr} marked posted at {sha[:8]}"


def send_review(req):
    """The Send. Everything it rejects, it rejects before touching the network."""
    if READONLY:
        raise RuntimeError("BENCH_HUB_READONLY is set — this hub does not post")
    repo = (req.get("repo") or UPSTREAM).strip()
    pr = str(req.get("pr") or "").strip()
    event = (req.get("event") or "COMMENT").strip()
    body = (req.get("body") or "").strip()
    comments = req.get("comments") or []
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repo):
        raise RuntimeError(f"that is not a repository: {repo}")
    if not re.fullmatch(r"\d+", pr):
        raise RuntimeError(f"that is not a PR number: {pr}")
    if event not in EVENT_NAMES:
        raise RuntimeError(f"unknown event: {event}")

    # Before any POST, and before check_anchors spends API calls: this is the
    # last point at which nothing has left the machine.
    guard_ok(repo, req.get("confirm") or "")

    if event == "ISSUE_COMMENT":
        if comments:
            raise RuntimeError("a plain comment cannot carry line comments — "
                               "choose Comment to send those as a review")
        if not body:
            raise RuntimeError("nothing to send — the comment is empty")
        r = api_post(f"repos/{repo}/issues/{pr}/comments", {"body": body})
        kind = "comment"
    else:
        if not body and not comments:
            raise RuntimeError("nothing to send — write a summary, or comment on a line")
        payload = {"event": event, "body": body}
        # Pinning the review to the SHA the diff was read at is what makes a
        # stale tab fail loudly instead of commenting on rewritten code.
        if req.get("sha"):
            payload["commit_id"] = req["sha"]
        if comments:
            payload["comments"] = [clean_comment(c) for c in comments]
            # Every anchor is checked against the diff before the POST, so a bad
            # line number is a sentence here rather than a 422 with a review
            # already created upstream.
            check_anchors(repo, pr, payload["comments"])
        r = api_post(f"repos/{repo}/pulls/{pr}/reviews", payload)
        kind = "review"

    note = ledger_mark_posted(pr, req.get("sha") or "") if repo == UPSTREAM else ""
    return {"ok": True, "kind": kind, "url": r.get("html_url", ""),
            "count": len(comments), "ledger": note}


# -------------------------------------------------------------------- page ---
CSS = """
/* Teal and brass, the same values as site/index.html and `oss serve`. This page
   was the last surface still on the old cream-and-rust palette, which made the
   thing you reach for every morning look like a different product from the tool
   that starts it.

   The first seven tokens are copied from `oss serve` unchanged. The last four
   are this page's own -- a diff has a gutter, an added line and a removed one,
   and no other surface renders a diff -- so they are mixed from the same family
   rather than invented: gutter is the code ground lifted a step, add leans on
   --ok, del on --bad. */
:root{--bg:#EDF2F3;--fg:#08161D;--mut:#536A74;--line:#CBDADD;--card:#F8FBFB;
--acc:#8A6A0F;--ok:#1B6259;--bad:#8C3A22;--code:#E2EBEC;
--warn:#A2571B;--add:#DDEFE9;--del:#F6DFD8;--gut:#E7EEEF;}
@media (prefers-color-scheme:dark){:root:not([data-theme=light]){
--bg:#07141A;--fg:#E6EFF0;--mut:#7B949C;--line:#1A3540;--card:#0D202A;
--acc:#D8B23A;--ok:#5FBFB0;--bad:#E08066;--code:#040E13;
--warn:#E0A24E;--add:#0C2A26;--del:#2A1512;--gut:#0A1B23;}}
:root[data-theme=dark]{--bg:#07141A;--fg:#E6EFF0;--mut:#7B949C;--line:#1A3540;
--card:#0D202A;--acc:#D8B23A;--ok:#5FBFB0;--bad:#E08066;--code:#040E13;
--warn:#E0A24E;--add:#0C2A26;--del:#2A1512;--gut:#0A1B23;}
/* The answer the CLI gave, under the row it was asked about. Left border rather
   than a box: it is a continuation of that row, not a new thing on the page. */
.ans>td,.ans>div{background:var(--gut);border-left:3px solid var(--acc);padding:8px 12px}
.ans pre{margin:0;background:transparent;padding:0;white-space:pre-wrap;font-size:12.5px}
.ans .sub{font-size:12px;margin-bottom:6px}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);
font:15px/1.65 ui-sans-serif,-apple-system,"Segoe UI",sans-serif;}
code,pre{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
.wrap{display:grid;grid-template-columns:250px minmax(0,1fr);gap:0;min-height:100vh}
nav{border-right:1px solid var(--line);padding:20px 14px;position:sticky;top:0;
height:100vh;overflow:auto;background:var(--card)}
nav h1{font-size:14px;letter-spacing:.06em;text-transform:uppercase;color:var(--mut);margin:0 0 4px}
nav .grp{font-size:11px;text-transform:uppercase;letter-spacing:.08em;color:var(--mut);
margin:18px 0 6px}
nav a{display:block;padding:4px 8px;border-radius:6px;color:var(--fg);text-decoration:none;
font-size:13.5px}
nav a:hover{background:var(--code)} nav a.on{background:var(--code);color:var(--acc);font-weight:600}
main{padding:28px 34px;max-width:none;overflow:hidden}
.hd{display:flex;align-items:baseline;gap:14px;flex-wrap:wrap;margin-bottom:6px}
.hd h2{margin:0;font-size:22px}
.sub{color:var(--mut);font-size:13px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:14px;margin:18px 0}
.card{border:1px solid var(--line);border-radius:10px;padding:14px 16px;background:var(--card)}
.card h3{margin:0 0 2px;font-size:15px}
.role{font-size:11px;text-transform:uppercase;letter-spacing:.09em;color:var(--acc)}
.kv{color:var(--mut);font-size:12.5px;margin-top:8px;word-break:break-word}
.pill{display:inline-block;padding:1px 8px;border-radius:99px;font-size:11.5px;
border:1px solid var(--line)}
.ok{color:var(--ok)} .warn{color:var(--warn)} .bad{color:var(--bad)}
.tw{overflow-x:auto;margin:14px 0}
table{border-collapse:collapse;width:100%;font-size:13.5px}
th,td{border-bottom:1px solid var(--line);padding:7px 10px;text-align:left;vertical-align:top}
th{color:var(--mut);font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.05em}
pre{background:var(--code);padding:12px 14px;border-radius:8px;overflow-x:auto;font-size:13px}
:not(pre)>code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:.9em}
blockquote{border-left:3px solid var(--acc);margin:14px 0;padding:2px 14px;color:var(--mut)}
h1,h2,h3,h4{line-height:1.3} main h1{font-size:24px} main h2{font-size:19px;margin-top:28px}
main h3{font-size:16px;margin-top:22px}
hr{border:0;border-top:1px solid var(--line);margin:22px 0}
a{color:var(--acc)}
.btn{font:inherit;font-size:12.5px;padding:3px 11px;border-radius:99px;cursor:pointer;
border:1px solid var(--line);background:var(--card);color:var(--acc)}
.btn:hover:not(:disabled){background:var(--code)}
.btn:disabled{cursor:progress;color:var(--mut)}
.btn.wide{display:block;width:100%;text-align:center;margin:2px 0 4px}
.doc{border-top:1px solid var(--line);margin-top:26px;padding-top:6px}
.foot{color:var(--mut);font-size:12px;margin-top:40px;border-top:1px solid var(--line);padding-top:12px}
@media(max-width:820px){.wrap{grid-template-columns:1fr}nav{position:static;height:auto}}

/* ---- review composer. A diff has to be read as code, so this half of the
   page is monospace, tight and full-bleed, unlike everything above it. */
.bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:14px 0}
.bar input{font:inherit;font-size:13px;padding:4px 9px;border-radius:7px;
border:1px solid var(--line);background:var(--card);color:var(--fg)}
.bar input.n{width:92px}
.file{border:1px solid var(--line);border-radius:10px;margin:12px 0;background:var(--card);
overflow:hidden}
.file>summary{padding:8px 12px;cursor:pointer;font:12.5px/1.5 ui-monospace,Menlo,monospace;
background:var(--gut);border-bottom:1px solid var(--line);word-break:break-all}
.file>summary::marker{color:var(--mut)}
table.dt{width:100%;border-collapse:collapse;font:12.5px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace}
.dt td{border:0;padding:0 8px;vertical-align:top;white-space:pre-wrap;word-break:break-word}
.dt td.n{width:1%;min-width:44px;text-align:right;color:var(--mut);background:var(--gut);
user-select:none;border-right:1px solid var(--line);position:relative}
.dt tr.add td.c{background:var(--add)} .dt tr.del td.c{background:var(--del)}
.dt tr.hh td{background:var(--code);color:var(--mut);padding:3px 8px}
.dt tr.cl:hover td.n{color:var(--acc)}
.dt td.n.hit{cursor:pointer}
.dt td.n.hit:hover::after{content:"+";position:absolute;right:2px;color:var(--card);
background:var(--acc);border-radius:4px;padding:0 4px;line-height:1.4}
.dt tr.sel td.c{outline:2px solid var(--acc);outline-offset:-2px}
.cw{padding:10px 12px 12px 52px;background:var(--card);border-top:1px solid var(--line)}
.cbox{border:1px solid var(--line);border-radius:9px;padding:10px;background:var(--bg);
max-width:820px}
.cbox textarea,.rbody{font:13px/1.6 ui-monospace,Menlo,monospace;width:100%;
border:1px solid var(--line);border-radius:7px;padding:9px 10px;background:var(--card);
color:var(--fg);resize:vertical}
.cbox textarea{min-height:88px}
.rbody{min-height:210px}
.tabs{display:flex;gap:6px;align-items:center;margin-bottom:7px;flex-wrap:wrap}
.tabs .sub{min-width:0;word-break:break-all}
.tab{font:inherit;font-size:12px;padding:2px 10px;border-radius:99px;cursor:pointer;
white-space:nowrap;border:1px solid var(--line);background:var(--card);color:var(--mut)}
.tab.on{color:var(--acc);border-color:var(--acc)}
.prev{border:1px dashed var(--line);border-radius:7px;padding:2px 12px;background:var(--card);
min-height:88px;font-size:14px}
.prev>*:first-child{margin-top:10px}
.thr{border-left:3px solid var(--acc);padding:8px 10px;background:var(--bg);border-radius:0 7px 7px 0;
max-width:820px;font:14px/1.6 ui-sans-serif,-apple-system,sans-serif}
.thr .who{font-size:12px;color:var(--mut);margin-bottom:4px}
.send{position:sticky;bottom:0;border:1px solid var(--line);border-top-width:3px;
border-top-color:var(--acc);border-radius:12px;background:var(--card);padding:14px 16px;
margin:18px 0 0;box-shadow:0 -6px 18px rgba(0,0,0,.06)}
.send label{display:inline-flex;gap:6px;align-items:baseline;margin-right:16px;font-size:13.5px}
.go{font:inherit;font-size:14px;font-weight:600;padding:7px 18px;border-radius:99px;
cursor:pointer;border:1px solid var(--acc);background:var(--acc);color:#fff}
.go:disabled{opacity:.5;cursor:not-allowed}
.pend{font-size:12.5px;color:var(--mut)}
.sent{border:1px solid var(--ok);border-radius:9px;padding:10px 14px;margin-top:12px}
"""

JS = """
const secs=[...document.querySelectorAll('section[id]')];
const links=[...document.querySelectorAll('nav a[data-t]')];
function show(id){
  secs.forEach(s=>s.hidden = s.id!==id);
  links.forEach(a=>a.classList.toggle('on',a.dataset.t===id));
  location.hash=id; window.scrollTo(0,0);
}
links.forEach(a=>a.onclick=e=>{e.preventDefault();show(a.dataset.t)});
// To do, not home. This opens every morning to answer one question -- what is
// waiting on me -- and it used to open on a status page instead, while the nav
// entry beside it said (26). The server already renders To do unhidden; only
// this line was hiding it again a frame later.
show(location.hash.slice(1)||'todo');

// ---- sync buttons. Every one of them is the same: start a named job on the
// server, poll it, then reload — the page is rendered from disk, so the reload
// is what makes the new data visible. The message survives it via sessionStorage.
const btns=j=>[...document.querySelectorAll(`button[data-job="${j}"]`)];
const msgs=j=>[...document.querySelectorAll(`[data-msg="${j}"]`)];
let busy=0;
function say(j,t,cls){msgs(j).forEach(m=>{m.textContent=t;m.className='sub '+(cls||'')})}
function paint(j,s){
  btns(j).forEach(b=>b.disabled=s.running);
  if(s.running){say(j,'syncing…')}
  else if(s.ok===true){say(j,`${s.msg} · ${s.took}s`,'ok')}
  else if(s.ok===false){say(j,s.msg,'bad')}
}
function done(j,s){
  sessionStorage.setItem('hubmsg',JSON.stringify({j:j,msg:s.msg,ok:s.ok,took:s.took}));
  location.reload();
}
function poll(j){
  fetch('job?name='+j).then(r=>r.json()).then(s=>{
    paint(j,s);
    if(s.running){setTimeout(()=>poll(j),2000);return}
    busy--;
    // "Sync everything" runs three jobs, so this one finishing does not mean the
    // page is settled. Reloading now would yank it out from under the rest;
    // whichever finishes last does the reload.
    fetch('job').then(r=>r.json())
      .then(all=>{if(Object.values(all).some(x=>x.running)){paint(j,s)}else{done(j,s)}})
      .catch(()=>done(j,s));
  }).catch(()=>{busy--;say(j,'lost the server','bad');btns(j).forEach(b=>b.disabled=false)});
}
document.querySelectorAll('button[data-job]').forEach(b=>b.onclick=()=>{
  const j=b.dataset.job;
  if(j==='reload'){location.reload();return}
  busy++; b.disabled=true; say(j,'starting…');
  fetch('job?name='+j+'&start=1').then(r=>r.json()).then(s=>{paint(j,s);poll(j)})
    .catch(()=>{busy--;say(j,'could not start','bad');b.disabled=false});
});
// A job started in another tab, or still running when this page was rendered.
fetch('job').then(r=>r.json()).then(all=>{
  Object.entries(all).forEach(([j,s])=>{if(s.running){busy++;paint(j,s);poll(j)}});
});
const last=sessionStorage.getItem('hubmsg');
if(last){sessionStorage.removeItem('hubmsg');const d=JSON.parse(last);
  say(d.j, d.ok?`${d.msg} · ${d.took}s`:d.msg, d.ok?'ok':'bad');}

// The server re-reads the repos on every request, so a reload is the refresh.
setInterval(()=>{
  if(busy)return;                       // never reload out from under a sync
  if(CM.pr)return;                      // ...nor out from under a half-written review
  fetch('status.json').then(r=>r.json()).then(d=>{
  if(window.__stamp && window.__stamp!==d.stamp) location.reload();
  window.__stamp=d.stamp;}).catch(()=>{})},15000);

// ---- the composer ----------------------------------------------------------
// Two rules everything below follows from. A comment is anchored by (path, line,
// side) taken from the parsed hunk the server sent — never by counting rows in
// the page — so what you click is what GitHub receives. And nothing leaves the
// machine until Send, which says what it is about to post, where, and as what.
const CM={repo:'',pr:'',sha:'',data:null,comments:[],seq:0,anchor:null,sent:false};
const cmp=document.getElementById('cmp');
const EVS=JSON.parse(document.getElementById('ev-json').textContent);
const EVL=Object.fromEntries(EVS.map(e=>[e[0],e[1]]));
const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,
  c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

// Markdown is rendered by the same renderer the rest of this site uses, so a
// preview is what the page would show — one round trip, and no second parser.
function render(md,el){
  if(!md.trim()){el.innerHTML='<p class="sub">nothing to preview yet</p>';return}
  el.innerHTML='<p class="sub">rendering…</p>';
  fetch('preview',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({text:md})}).then(r=>r.json())
    .then(d=>{el.innerHTML=d.html||''})
    .catch(()=>{el.textContent=md});
}

function openPR(repo,pr,force){
  if(CM.comments.length && !CM.sent && (repo!==CM.repo||pr!==CM.pr)
     && !confirm('Discard '+CM.comments.length+' unsent line comment(s) on #'+CM.pr+'?'))return;
  show('compose');
  const inp=document.getElementById('c-repo'), inn=document.getElementById('c-pr');
  if(inp)inp.value=repo; if(inn)inn.value=pr;
  cmp.innerHTML='<p class="sub">reading '+esc(repo)+' #'+esc(pr)+' from GitHub…</p>';
  fetch('pr.json?repo='+encodeURIComponent(repo)+'&pr='+encodeURIComponent(pr)+(force?'&reload=1':''))
    .then(r=>r.json()).then(d=>{
      if(d.error){cmp.innerHTML='<p class="bad">'+esc(d.error)+'</p>';return}
      CM.repo=d.repo;CM.pr=d.pr;CM.sha=d.sha;CM.data=d;CM.comments=[];CM.seq=0;
      CM.anchor=null;CM.sent=false;
      renderPR(d);
    }).catch(()=>{cmp.innerHTML='<p class="bad">lost the server</p>'});
}

function fileHtml(f,fi){
  if(f.nopatch)return '<div class="cw"><span class="sub">no textual diff — binary, '
    +'a pure rename, or too large for the API. Comment on it from GitHub.</span></div>';
  let out='<table class="dt">';
  f.hunks.forEach((h,hi)=>{
    out+='<tr class="hh"><td class="n"></td><td class="n"></td><td>'+esc(h.header)+'</td></tr>';
    h.lines.forEach((l,li)=>{
      // A deletion only exists on the base, so it is commented on LEFT; every
      // other line is addressed on the head. That is GitHub's rule, not a choice.
      const side=l.t==='-'?'LEFT':'RIGHT', line=l.t==='-'?l.old:l.new;
      const cls=l.t==='+'?'add':(l.t==='-'?'del':'ctx');
      out+='<tr class="cl '+cls+'" data-f="'+fi+'" data-h="'+hi+'" data-i="'+li+'"'
        +' data-path="'+esc(f.path)+'" data-line="'+line+'" data-side="'+side+'"'
        +' data-code="'+esc(l.text)+'">'
        +'<td class="n hit">'+(l.old==null?'':l.old)+'</td>'
        +'<td class="n hit">'+(l.new==null?'':l.new)+'</td>'
        +'<td class="c">'+esc(l.t)+esc(l.text)+'</td></tr>';
    });
  });
  return out+'</table>';
}

function renderPR(d){
  const flags=[d.state!=='OPEN'?'<span class="pill bad">'+esc(d.state.toLowerCase())+'</span>':'',
               d.wip?'<span class="pill">draft PR</span>':'',
               d.cached?'<span class="pill">from cache at this head</span>':''].join(' ');
  const draft=d.draft_file
    ?'prefilled from <code>~/.oss-cli/reviews/'+esc(d.draft_file)+'</code> — the paste-ready block, edit freely'
    :'no write-up under <code>~/.oss-cli/reviews/</code> for this PR — writing from scratch';
  const head='<div class="card"><div class="role">'+esc(d.repo)+' · '+esc(d.state.toLowerCase())+'</div>'
    +'<h3><a href="'+esc(d.url)+'">#'+esc(d.pr)+'</a> '+esc(d.title)+'</h3>'
    +'<div class="sub">@'+esc(d.author)+' → '+esc(d.base)+' · '+d.changed+' files · '
    +'<span class="ok">+'+d.adds+'</span> <span class="bad">−'+d.dels+'</span> · head '
    +'<code>'+esc(d.sha.slice(0,8))+'</code> '+flags+'</div>'
    +'<div class="bar"><button class="btn" id="c-reload">Re-read the diff from GitHub</button>'
    +'<span class="sub">click a line number to comment on it · shift-click a second one '
    +'for a range</span></div></div>';
  const files=d.files.map((f,i)=>
    '<details class="file" open><summary>'+esc(f.path)+'  <span class="sub">'+esc(f.status)
    +' · <span class="ok">+'+f.adds+'</span> <span class="bad">−'+f.dels+'</span></span></summary>'
    +fileHtml(f,i)+'</details>').join('');
  const evs=EVS.map((e,i)=>'<label><input type="radio" name="ev" value="'+e[0]+'"'
    +(i===0?' checked':'')+'><span>'+esc(e[1])+' <span class="sub">— '+esc(e[2])+'</span></span></label>').join('');
  const panel='<div class="send"><div class="hd"><h3 style="margin:0">Finish your review</h3>'
    +'<span class="sub" id="pcount"></span></div>'
    +'<div class="tabs"><button class="tab on" data-rt="w">Write</button>'
    +'<button class="tab" data-rt="p">Preview</button><span class="sub">'+draft+'</span></div>'
    +'<textarea class="rbody" id="rbody" placeholder="Summary of the review — markdown">'
    +esc(d.draft)+'</textarea><div class="prev" id="rprev" hidden></div>'
    +'<div class="bar">'+evs+'</div><div id="plist"></div>'
    +'<div class="bar"><button class="go" id="go"'+(d.readonly?' disabled':'')+'>'
    +(d.readonly?'Read-only — BENCH_HUB_READONLY is set'
               :'Send to '+esc(d.repo)+' #'+esc(d.pr))+'</button>'
    +'<span class="sub" id="sendmsg"></span></div><div id="sent"></div></div>';
  cmp.innerHTML=head+files+panel;
  paintPending();
}

// ---- line comments
function rowFor(path,line,side){
  return [...cmp.querySelectorAll('tr.cl')].find(t=>t.dataset.path===path
    && +t.dataset.line===line && t.dataset.side===side);
}
function closeBoxes(){cmp.querySelectorAll('tr.box[data-box]').forEach(b=>b.remove());
  cmp.querySelectorAll('tr.sel').forEach(t=>t.classList.remove('sel'));}

function openBox(tr,existing,start){
  closeBoxes();
  const p=tr.dataset.path,l=+tr.dataset.line,s=tr.dataset.side;
  const id=existing?existing.id:'c'+(++CM.seq);
  const where=(start&&start!==l)?start+'–'+l:l;
  const at=p.split('/').pop()+':'+where;
  const row=document.createElement('tr');
  row.className='box';row.dataset.box=id;
  row.innerHTML='<td class="cw" colspan="3"><div class="cbox" data-id="'+id+'" data-path="'+esc(p)
    +'" data-line="'+l+'" data-start="'+(start||'')+'" data-side="'+s+'">'
    +'<div class="tabs"><button class="tab on" data-ct="w">Write</button>'
    +'<button class="tab" data-ct="p">Preview</button><span class="sub" title="'+esc(p)+'">'
    +esc(at)+' · '+(s==='LEFT'?'base':'head')+' · markdown</span></div>'
    +'<textarea placeholder="Comment on this line…">'+esc(existing?existing.body:'')+'</textarea>'
    +'<div class="prev" hidden></div>'
    +'<div class="bar"><button class="btn" data-act="save">'
    +(existing?'Update':'Add')+' comment</button>'
    +'<button class="btn" data-act="cancel">Cancel</button></div></div></td>';
  const after=cmp.querySelector('tr[data-thread="'+id+'"]')||tr;
  after.parentNode.insertBefore(row,after.nextSibling);
  // Show the range you are about to comment on, so "the wrong line" is visible
  // before it is posted rather than after.
  if(start&&start!==l){
    [...tr.parentNode.children].forEach(t=>{
      if(t.classList.contains('cl')&&t.dataset.path===p&&t.dataset.side===s
         &&+t.dataset.line>=start&&+t.dataset.line<=l)t.classList.add('sel');});
  }else tr.classList.add('sel');
  row.querySelector('textarea').focus();
}

function saveBox(box){
  const body=box.querySelector('textarea').value.trim();
  if(!body){box.querySelector('textarea').focus();return}
  const id=box.dataset.id,path=box.dataset.path,line=+box.dataset.line;
  const side=box.dataset.side,start=box.dataset.start?+box.dataset.start:null;
  const tr=rowFor(path,line,side);
  const code=tr?tr.dataset.code:'';
  const i=CM.comments.findIndex(c=>c.id===id);
  const c={id:id,path:path,line:line,side:side,start_line:start,body:body,code:code};
  if(i<0)CM.comments.push(c); else CM.comments[i]=c;
  closeBoxes();
  cmp.querySelectorAll('tr[data-thread="'+id+'"]').forEach(t=>t.remove());
  if(!tr){paintPending();return}
  const row=document.createElement('tr');
  row.className='box';row.dataset.thread=id;
  const at=path.split('/').pop()+':'+((start&&start!==line)?start+'–'+line:line);
  row.innerHTML='<td class="cw" colspan="3"><div class="thr"><div class="who">'
    +'<strong>pending</strong> · <span title="'+esc(path)+'">'+esc(at)+'</span>'
    +' · sends with the review</div>'
    +'<div class="prev" data-body></div>'
    +'<div class="bar"><button class="btn" data-act="edit" data-id="'+id+'">Edit</button>'
    +'<button class="btn" data-act="del" data-id="'+id+'">Delete</button></div></div></td>';
  tr.parentNode.insertBefore(row,tr.nextSibling);
  render(body,row.querySelector('[data-body]'));
  paintPending();
}

function paintPending(){
  const n=CM.comments.length;
  const c=document.getElementById('pcount');
  if(c)c.textContent=n?(n+' line comment'+(n===1?'':'s')+' pending'):'no line comments yet';
  const list=document.getElementById('plist');
  if(!list)return;
  list.innerHTML=n?('<div class="tw"><table><thead><tr><th>where</th><th>the line</th>'
    +'<th>comment</th><th></th></tr></thead><tbody>'
    +CM.comments.map(x=>'<tr><td><code>'+esc(x.path.split("/").pop())+':'
      +(x.start_line&&x.start_line!==x.line?x.start_line+'–'+x.line:x.line)+'</code>'
      +'<div class="sub">'+(x.side==='LEFT'?'base':'head')+'</div></td>'
      +'<td><code>'+esc((x.code||'').trim().slice(0,72))+'</code></td>'
      +'<td>'+esc(x.body.length>90?x.body.slice(0,90)+'…':x.body)+'</td>'
      +'<td><button class="btn" data-act="jump" data-id="'+x.id+'">show</button> '
      +'<button class="btn" data-act="del" data-id="'+x.id+'">delete</button></td></tr>').join('')
    +'</tbody></table></div>'):'';
}

function send(){
  const ev=(cmp.querySelector('input[name=ev]:checked')||{}).value||'COMMENT';
  const body=document.getElementById('rbody').value;
  const n=CM.comments.length;
  const msg=document.getElementById('sendmsg'), go=document.getElementById('go');
  if(ev==='ISSUE_COMMENT'&&n){
    msg.textContent='a plain comment cannot carry line comments — choose Comment';
    msg.className='sub bad';return;
  }
  if(!confirm('Post to '+CM.repo+' #'+CM.pr+' now?\\n\\n'+EVL[ev]
      +'\\n'+n+' line comment'+(n===1?'':'s')
      +'\\nsummary: '+(body.trim()?body.trim().length+' characters':'(empty)')
      +'\\n\\nThis is public, under your account.'))return;
  // Second gate, and a different KIND of gate. The confirm above is a reflex
  // check anyone clicks through; this one cannot be satisfied by clicking. The
  // server refuses without it, so a stray Enter cannot post.
  const typed=prompt('This posts to '+CM.repo+'. Type the repository name exactly to confirm:');
  if(typed===null){msg.className='sub';msg.textContent='not sent';return}
  go.disabled=true;msg.className='sub';msg.textContent='posting…';
  fetch('review',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({repo:CM.repo,pr:CM.pr,sha:CM.sha,event:ev,body:body,
      confirm:typed,
      comments:CM.comments.map(c=>({path:c.path,line:c.line,side:c.side,
        start_line:c.start_line,body:c.body}))})})
  .then(r=>r.json()).then(d=>{
    if(d.error){msg.textContent=d.error;msg.className='sub bad';go.disabled=false;return}
    CM.sent=true;msg.textContent='';
    document.getElementById('sent').innerHTML='<div class="sent"><strong class="ok">Sent.</strong> '
      +esc(d.kind)+' with '+d.count+' line comment'+(d.count===1?'':'s')+' — '
      +'<a href="'+esc(d.url)+'">open it on GitHub</a>'
      +(d.ledger?'<div class="sub">'+esc(d.ledger)+'</div>':'')
      +'<div class="sub">The To-do board still shows the old state until it is synced.</div>'
      +'</div>';
    go.textContent='Sent';
  }).catch(()=>{msg.textContent='lost the server — nothing was posted';
    msg.className='sub bad';go.disabled=false});
}

cmp.addEventListener('click',e=>{
  const gut=e.target.closest('td.n.hit');
  if(gut){
    const tr=gut.closest('tr.cl');
    const p=tr.dataset.path,l=+tr.dataset.line,s=tr.dataset.side;
    // Shift-click extends the last click into a range, but only within one file
    // and one side — a range that crosses either is not a thing GitHub accepts.
    let start=null;
    if(e.shiftKey&&CM.anchor&&CM.anchor.path===p&&CM.anchor.side===s&&CM.anchor.line<l)
      start=CM.anchor.line;
    else CM.anchor={path:p,line:l,side:s};
    openBox(tr,null,start);return;
  }
  const tab=e.target.closest('.tab');
  if(tab&&tab.dataset.ct){
    const box=tab.closest('.cbox'),ta=box.querySelector('textarea'),pv=box.querySelector('.prev');
    box.querySelectorAll('.tab').forEach(t=>t.classList.toggle('on',t===tab));
    const p=tab.dataset.ct==='p';
    ta.hidden=p;pv.hidden=!p;if(p)render(ta.value,pv);return;
  }
  if(tab&&tab.dataset.rt){
    const ta=document.getElementById('rbody'),pv=document.getElementById('rprev');
    tab.parentNode.querySelectorAll('.tab').forEach(t=>t.classList.toggle('on',t===tab));
    const p=tab.dataset.rt==='p';
    ta.hidden=p;pv.hidden=!p;if(p)render(ta.value,pv);return;
  }
  const act=e.target.closest('[data-act]');
  if(!act)return;
  const a=act.dataset.act;
  if(a==='cancel'){closeBoxes();return}
  if(a==='save'){saveBox(act.closest('.cbox'));return}
  if(a==='del'){
    const id=act.dataset.id;
    CM.comments=CM.comments.filter(c=>c.id!==id);
    cmp.querySelectorAll('tr[data-thread="'+id+'"]').forEach(t=>t.remove());
    paintPending();return;
  }
  if(a==='edit'||a==='jump'){
    const c=CM.comments.find(x=>x.id===act.dataset.id);
    if(!c)return;
    const tr=rowFor(c.path,c.line,c.side);
    if(!tr)return;
    const box=tr.closest('details'); if(box)box.open=true;
    tr.scrollIntoView({block:'center'});
    if(a==='edit')openBox(tr,c,c.start_line);
    return;
  }
});
document.getElementById('cmp').addEventListener('click',e=>{
  if(e.target.id==='c-reload'&&CM.pr)openPR(CM.repo,CM.pr,true);
});
// Ask the CLI about the row you are already looking at, and answer under it.
// Inline rather than anywhere else on purpose: the question is about THIS pull
// request, and a separate panel would mean losing your place in the list to read
// the answer, then finding the row again.
async function askInline(btn){
  // Works in a table row and outside one. In a row the answer has to be a <tr>
  // spanning every column or the browser drops it; anywhere else a <div> is
  // enough. Same function either way, so there is one place this behaves.
  const tr=btn.closest('tr');
  const anchor=tr||btn.closest('p,div')||btn;
  let out=anchor.nextElementSibling;
  if(out&&out.classList.contains('ans')){out.remove();btn.textContent=btn.dataset.label;return}
  btn.dataset.label=btn.dataset.label||btn.textContent;
  btn.textContent='…';btn.disabled=true;
  if(tr){
    out=document.createElement('tr');out.className='ans';
    out.innerHTML=`<td colspan="${tr.children.length}"><pre class="sub">asking…</pre></td>`;
  }else{
    out=document.createElement('div');out.className='ans';
    out.innerHTML='<div><pre class="sub">asking…</pre></div>';
  }
  anchor.after(out);
  try{
    const p=new URLSearchParams({name:btn.dataset.ask,q:btn.dataset.q||''});
    const d=await(await fetch('/ask.json?'+p)).json();
    const pre=document.createElement('pre');
    pre.textContent=d.error?('error  '+d.error):d.out;
    const cmd=document.createElement('div');
    cmd.className='sub';cmd.textContent=d.cmd?('$ '+d.cmd):'';
    const td=out.firstElementChild;td.textContent='';
    if(d.cmd)td.appendChild(cmd);
    td.appendChild(pre);
  }catch(err){
    out.firstElementChild.innerHTML='<pre class="sub">could not reach the server</pre>';
  }
  out.scrollIntoView({block:'nearest'});
  btn.disabled=false;btn.textContent='Hide';
}
document.addEventListener('click',e=>{
  const a=e.target.closest('[data-ask]');
  if(a){e.preventDefault();askInline(a);return}
  const o=e.target.closest('[data-open]');
  if(o){e.preventDefault();const[r,n]=o.dataset.open.split('#');openPR(r,n,false)}
  if(e.target.id==='go')send();
  if(e.target.id==='c-load'){
    const r=document.getElementById('c-repo').value.trim();
    const n=document.getElementById('c-pr').value.trim().replace(/^#/,'');
    if(r&&n)openPR(r,n,false);
  }
});
document.addEventListener('keydown',e=>{
  if(e.key==='Enter'&&e.target.id==='c-pr'){e.preventDefault();
    document.getElementById('c-load').click()}
});
// A tab closed with comments in it loses them — they live in the page, never on
// the server, so nothing half-written can be posted by anything but you.
window.addEventListener('beforeunload',e=>{
  if(CM.comments.length&&!CM.sent){e.preventDefault();e.returnValue=''}
});

// ?pr=4245 — one link straight into the composer, so `oss run hub --pr 4245`, a
// bookmark and a paste into a chat all land on the diff rather than the board.
const qs=new URLSearchParams(location.search);
if(qs.get('pr'))openPR((qs.get('repo')||'').trim()||document.getElementById('c-repo').value.trim(),
                       qs.get('pr').replace(/^#/,''),false);
"""


def newest_triage():
    """`oss backlog` already answers 'what is happening in the
    backlog'. Surface its newest report rather than writing a second one."""
    reports = sorted(KB.glob("triage-*.html"), key=lambda p: p.stat().st_mtime, reverse=True)
    return reports[0] if reports else None


def human_age(seconds):
    if seconds is None:
        return "never"
    m = int(seconds // 60)
    if m < 1:
        return "just now"
    if m < 60:
        return f"{m} min ago"
    h = m // 60
    return f"{h} h ago" if h < 48 else f"{h // 24} days ago"


# ------------------------------------------------------------ triage sweep ---
# A read-only GitHub sweep, but an expensive one — ~80 PRs and ~160 issues, a few
# hundred API calls — writing its report into knowledge-creator. So it never runs
# on the request path: the button and the background thread both start it here,
# and the page polls for the result.
#
# It was knowledge-creator/triage.sh. That script is now `oss backlog`, and the
# old path was still being looked for on every sweep — failing, once per run,
# into a log nobody reads. Same flags, same OWNER/REPO positional, same
# triage-<date>-<repo>.html written into the working directory, which is why
# cwd stays KB and newest_triage() still finds it.
TRIAGE_CMD = ["oss", "backlog"]
TRIAGE_MAX_AGE_H = float(os.environ.get("BENCH_TRIAGE_MAX_AGE_H", "24"))


def triage_age():
    t = newest_triage()
    return None if not t else datetime.now().timestamp() - t.stat().st_mtime


def triage_sweep():
    if not shutil.which(TRIAGE_CMD[0]):
        raise FileNotFoundError(f"no '{TRIAGE_CMD[0]}' on PATH — the sweep is `oss backlog`")
    r = subprocess.run([*TRIAGE_CMD, UPSTREAM, "--no-ai"], cwd=str(KB),
                       capture_output=True, text=True, timeout=1800)
    if r.returncode != 0:
        # The last non-empty line is the failure; the rest is progress.
        tail = [l for l in (r.stderr or r.stdout).strip().split("\n") if l.strip()]
        raise RuntimeError(tail[-1] if tail else f"exit {r.returncode}")
    t = newest_triage()
    return t.name if t else "swept"


# The report is a standalone page from another repo, with its own light-only
# palette. Rather than restyle it — it is regenerated by `oss backlog` and any edit
# there would be overwritten — map its palette onto the hub's dark tokens on the
# way out, and let the iframe answer prefers-color-scheme like the hub does.
TRIAGE_DARK = """
:root{color-scheme:dark}
body{background:#07141A;color:#E6EFF0}
a{color:#D8B23A}
h3{color:#E6EFF0}
h2{border-bottom-color:#1A3540}
.hint,.stat-l,.ai-note,footer{color:#7B949C}
#sidebar{background:#0D202A;color:#C2D4D8}
.sb-repo a{color:#D8B23A}
.sb-meta,.sb-sec,.sb-n{color:#7B949C}
.sb-hr{border-top-color:#1A3540}
.nav-list a{color:#C2D4D8}
.nav-list a:hover{background:#040E13;color:#F2F8F8}
.sb-n{background:#040E13}
table,.ai-table{background:#0D202A;border-color:#1A3540}
th{background:#040E13;color:#C2D4D8;border-bottom-color:#1A3540}
td{border-top-color:#1A3540}
tr:hover td,.ai-table tr:hover td{background:#040E13}
.stat,.cluster,.ai{background:#0D202A;border-color:#1A3540}
.cluster{border-left-color:#D8B23A}
.ai{background:#0D202A;border-left-color:#a78bfa}
.ai-table th{background:#2a2233;color:#c4b5fd;border-bottom-color:#3b2f47}
.ai-table code{background:#040E13;border-color:#1A3540;color:#E6EFF0}
.rr{color:#C2D4D8;border-top-color:#1A3540}
.rl-bar{background:#1A3540}
.warn{background:#2A2110;border-color:#5C4A1A;color:#E0A24E}
footer{border-top-color:#1A3540}
.b{border-width:1px;border-style:solid}
.g{background:#0C2A26;color:#5FBFB0;border-color:#1B4A44}
.r{background:#2A1512;color:#E08066;border-color:#5A3225}
.y{background:#2A2110;color:#E0A24E;border-color:#5C4A1A}
.u{background:#151f2e;color:#8ab4e8;border-color:#2a3f5c}
.s{background:#040E13;color:#7B949C;border-color:#1A3540}
.p{background:#241c33;color:#c4b5fd;border-color:#3b2f47}
"""


def triage_page(theme="auto"):
    """The report as served: its own bytes, plus a dark palette it never had."""
    t = newest_triage()
    if not t:
        return None
    css = "" if theme == "light" else (
        TRIAGE_DARK if theme == "dark"
        else "@media (prefers-color-scheme:dark){%s}" % TRIAGE_DARK)
    body = t.read_text(encoding="utf-8", errors="replace")
    if css:
        # After the report's own <style>, so equal-specificity rules win.
        tag = f"<style>{css}</style>"
        body = (body.replace("</head>", tag + "\n</head>", 1)
                if "</head>" in body else tag + body)
    return body.encode()


# -------------------------------------------------------------------- jobs ---
# Every "pull new" button on the page is the same mechanism: a named job, started
# on its own thread, polled by the browser. Nothing runs on the request path,
# because this is a single-threaded server — a synchronous sweep would freeze the
# page it is refreshing. A job already running is joined, not started twice.
# ------------------------------------------------------------------ report ---
# What happened today, across the three repos and nothing else. Upstream is
# deliberately not in here: this is a report on your own work, and the one view
# that looks at apache/logging-log4j2 is the To-do board, which reads.
#
# One file per day under .bench/hub/report, because a day that has passed does
# not change and re-deriving it from git every load would be slower and would
# lose the days when a clone was elsewhere. Today's file is rewritten on every
# refresh; yesterday's is left exactly as it was written.
REPORT_DIR = WORKOUT / ".bench" / "hub" / "report"
REPORT_KEEP = 60


def today_str():
    """Local, not UTC. A daily report is read by someone in a timezone."""
    return datetime.now().strftime("%Y-%m-%d")


def repo_slug(path):
    """owner/name from origin, for the API half. Returns "" for a clone with no
    origin, which is not an error — the git half of the report still works."""
    url = git(path, "remote", "get-url", "origin")
    m = re.search(r"[:/]([\w.-]+/[\w.-]+?)(?:\.git)?$", url)
    return m.group(1) if m else ""


def day_commits(path, day):
    """Commits authored on `day`, on every local branch, not just the checked-out
    one — a day's work often sits on a feature branch that was never merged."""
    fmt = "%H%x1f%s%x1f%an%x1f%cI%x1f%D"
    raw = git(path, "log", "--all", "--no-merges", "--date-order",
              f"--since={day} 00:00:00", f"--until={day} 23:59:59", f"--format={fmt}")
    out = []
    for line in raw.split("\n"):
        if not line.strip():
            continue
        parts = line.split("\x1f")
        if len(parts) < 4:
            continue
        sha, subject, who, when = parts[0], parts[1], parts[2], parts[3]
        stat = git(path, "show", "--shortstat", "--format=", sha)
        files = adds = dels = 0
        m = re.search(r"(\d+) files? changed", stat)
        if m:
            files = int(m.group(1))
        m = re.search(r"(\d+) insertions?", stat)
        if m:
            adds = int(m.group(1))
        m = re.search(r"(\d+) deletions?", stat)
        if m:
            dels = int(m.group(1))
        out.append({"sha": sha[:8], "subject": subject, "who": who, "when": when,
                    "files": files, "adds": adds, "dels": dels})
    return out


def day_prs(slug, day):
    """Pull requests on your own repo that moved on `day`. Read-only, and it
    degrades to an empty list rather than failing the report when gh is not
    logged in or the laptop is offline."""
    if not slug:
        return []
    d = gh_json("pr", "list", "-R", slug, "--state", "all", "--limit", "50",
                "--search", f"updated:>={day}", "--json",
                "number,title,state,createdAt,mergedAt,closedAt,url") or []
    out = []
    for p in d:
        moved = []
        for label, key in (("opened", "createdAt"), ("merged", "mergedAt"),
                           ("closed", "closedAt")):
            v = p.get(key) or ""
            if v[:10] == day:
                moved.append(label)
        if moved:
            out.append({"number": p.get("number"), "title": p.get("title", ""),
                        "url": p.get("url", ""), "state": p.get("state", ""),
                        "moved": moved})
    return out


def report_build(day=None):
    """Derive one day and write it. Returns the report."""
    day = day or today_str()
    repos = []
    for name, path, role, _ in REPOS:
        if not (path / ".git").exists():
            repos.append({"name": name, "role": role, "missing": str(path)})
            continue
        s = repo_state(path)
        slug = repo_slug(path)
        commits = day_commits(path, day)
        repos.append({
            "name": name, "role": role, "slug": slug,
            "branch": s.get("branch", ""), "dirty": s.get("dirty", 0),
            "ahead": s.get("ahead", 0), "behind": s.get("behind", 0),
            "commits": commits,
            "adds": sum(c["adds"] for c in commits),
            "dels": sum(c["dels"] for c in commits),
            "prs": day_prs(slug, day) if day == today_str() else [],
        })
    d = {"day": day, "at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
         "repos": repos}
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    (REPORT_DIR / f"{day}.json").write_text(json.dumps(d, indent=1))
    for old in sorted(REPORT_DIR.glob("20*.json"))[:-REPORT_KEEP]:
        old.unlink()
    return d


def report_days():
    """Newest first. The filename is the day, so no file needs opening to sort."""
    return sorted((p.stem for p in REPORT_DIR.glob("20*.json")), reverse=True)


def report_load(day=None):
    """Today's report if it has been written, otherwise the newest there is."""
    days = report_days()
    want = day or today_str()
    if want not in days:
        want = days[0] if days else None
    if not want:
        return None
    try:
        return json.loads((REPORT_DIR / f"{want}.json").read_text())
    except Exception:
        return None


def report_sweep():
    d = report_build()
    n = sum(len(r.get("commits") or []) for r in d["repos"])
    p = sum(len(r.get("prs") or []) for r in d["repos"])
    return f"{d['day']}: {n} commits, {p} PRs"


def fetch_all():
    """`git fetch` in all three clones, so 'behind' means today rather than
    whenever you last fetched by hand. Fetch only — it never touches a worktree."""
    done, failed = [], []
    for name, path, _, _ in REPOS:
        if not (path / ".git").exists():
            continue
        r = subprocess.run(["git", "-C", str(path), "fetch", "--all", "--quiet",
                            "--prune"], capture_output=True, text=True, timeout=180)
        (done if r.returncode == 0 else failed).append(name)
    if failed:
        raise RuntimeError(f"fetch failed: {', '.join(failed)}")
    return f"fetched {', '.join(done)}" if done else "no clones to fetch"


def sync_all():
    """One button for the whole page. Sequential on purpose: three concurrent
    GitHub sweeps race each other for the same rate limit."""
    out = []
    for n in SYNC_ORDER:
        s = job_run(n)
        # job_run declines a job the background thread already has in flight, and
        # returns its live state. That is "wait for it", not "it failed" — so join
        # it rather than reporting a sweep that is running fine as a failure.
        while s["running"]:
            time.sleep(1)
            s = job_state(n)
        out.append(f"{JOBS[n][0].lower()}: {s['msg'] if s['ok'] else 'FAILED — ' + s['msg']}")
    return " · ".join(out)


JOBS = {
    "todo": ("To do", lambda: f"{len(todo_refresh()['rows'])} PRs"),
    "triage": ("Backlog triage", triage_sweep),
    "fetch": ("Repo status", fetch_all),
    "report": ("Daily report", report_sweep),
    "all": ("Everything", sync_all),
}
# report runs after fetch, so the day it writes counts commits that were only
# fetched a moment ago rather than whatever was local this morning.
SYNC_ORDER = ("fetch", "todo", "report", "triage")

LOCK_DIR = WORKOUT / ".bench" / "hub" / "locks"
_jobs_lock = threading.Lock()
_job_state = {}


def job_state(name):
    with _jobs_lock:
        return dict(_job_state.get(name) or
                    {"name": name, "running": False, "ok": None, "msg": "", "took": None})


def _job_claim(name):
    """Mark the job running, if it is not already. Claiming and executing are
    separate so that starting one on a thread cannot look, to the thread it just
    started, like a job already in flight."""
    with _jobs_lock:
        if (_job_state.get(name) or {}).get("running"):
            return False
        _job_state[name] = {"name": name, "running": True, "ok": None,
                            "msg": "running…", "took": None}
        return True


def _job_lock(name):
    """`oss run hub` runs from launchd all day, so a terminal `--sync` is a second
    process, not a second thread — and two triage.sh runs would write the same
    report at once. flock is held by the OS and released when the process dies,
    which a pidfile would not survive."""
    LOCK_DIR.mkdir(parents=True, exist_ok=True)
    f = open(LOCK_DIR / f"{name}.lock", "w")
    try:
        fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return f
    except OSError:
        f.close()
        return None


def _job_exec(name):
    started = datetime.now(timezone.utc)
    # "all" holds no lock of its own; each job it runs takes its own.
    lock = _job_lock(name) if name != "all" else True
    if lock is None:
        msg, ok = "already running in another oss run hub — left it to finish", True
    else:
        try:
            msg, ok = str(JOBS[name][1]()), True
        except Exception as e:
            msg, ok = str(e) or e.__class__.__name__, False
        finally:
            if lock is not True:
                fcntl.flock(lock, fcntl.LOCK_UN)
                lock.close()
    took = round((datetime.now(timezone.utc) - started).total_seconds())
    with _jobs_lock:
        _job_state[name] = {"name": name, "running": False, "ok": ok,
                            "msg": msg, "took": took}
        return dict(_job_state[name])


def job_run(name):
    """Run a job to completion. Returns its state; never raises."""
    if not _job_claim(name):
        return job_state(name)
    return _job_exec(name)


def job_start(name):
    """Start a job on its own thread and return its state immediately."""
    if _job_claim(name):
        threading.Thread(target=_job_exec, args=(name,), daemon=True).start()
    return job_state(name)


def button(job, label):
    return f'<button class="btn" data-job="{job}">{html.escape(label)}</button>'


BUCKETS = [
    ("you", "Your move", "nothing happens until you do something"),
    ("them", "Their move", "waiting on someone else — safe to ignore today"),
    ("closed", "Closed or merged", "no longer live"),
]


def todo_html(todo, age):
    if not todo:
        return f"""<div class="hd"><h2>To do</h2>{button("todo", "Sync now")}
        <span class="sub" data-msg="todo"></span></div>
        <p>No cache yet — this view needs GitHub, so it is fetched rather than read
        from the working tree. Press <em>Sync now</em>, or:</p>
        <pre><code>oss run hub --refresh</code></pre>
        <p class="sub">Once written, the page renders from the cache instantly and
        refreshes itself in the background while the server runs.</p>"""

    stale = age is not None and age > 3600
    head = (f'<div class="hd"><h2>To do</h2><span class="sub">'
            f'{html.escape(todo["repo"])} · as @{html.escape(todo["me"])} · '
            f'{"<span class=warn>" if stale else ""}fetched {human_age(age)}'
            f'{"</span>" if stale else ""}</span>'
            f'{button("todo", "Sync now")}<span class="sub" data-msg="todo"></span></div>')

    out = [head]
    if stale:
        out.append('<p class="sub">Older than an hour — the server refreshes in the '
                   'background, or press <em>Sync now</em>.</p>')

    for key, title, blurb in BUCKETS:
        rows = [r for r in todo["rows"] if r["bucket"] == key]
        if not rows:
            continue
        # Yours first, and within a bucket the PRs you wrote before the ones you
        # review — a failing CI on your own change outranks someone else's patch.
        rows.sort(key=lambda r: (r["role"] != "mine", r["repo"], -int(r["pr"])))
        body = ""
        for r in rows:
            flags = ""
            if r["moved"]:
                flags += ' <span class="pill warn">pushed since review</span>'
            if r["draft"]:
                flags += ' <span class="pill">draft</span>'
            if r["ci_bad"]:
                flags += ' <span class="pill bad">CI failing</span>'
            if r["unsent"]:
                flags += ' <span class="pill warn">review unsent</span>'
            # The verdict comes from ledger.tsv — GitHub has no idea what you
            # decided, only what you posted.
            verdict = ("" if r["verdict"] in ("—", "")
                       else f' · <em>{html.escape(r["verdict"])}</em>')
            who = ("<strong>yours</strong>" if r["role"] == "mine"
                   else f'review of @{html.escape(r["author"])}{verdict}')
            # Straight from the row into the composer, at the head it is at now.
            go = ("" if r["state"] != "OPEN" else
                  f'<button class="btn" data-open="{html.escape(r["repo"])}#{r["pr"]}">'
                  f"Review →</button>")
            # `oss search`, on the row, about this title. Deciding whether a PR is
            # worth your morning usually starts with "have I already worked this
            # out" -- and the answer is in your own notes, not on GitHub.
            go += (f'<button class="btn" data-ask="search" '
                   f'data-q="{html.escape(r["title"][:120])}">Seen this?</button>')
            body += (
                f'<tr><td class="sub">{html.escape(r["repo"])}</td>'
                f'<td><a href="https://github.com/{html.escape(r["repo"])}/pull/{r["pr"]}">'
                f'#{r["pr"]}</a></td>'
                f'<td>{html.escape(r["title"][:66])}{flags}</td>'
                f"<td>{who}</td>"
                f'<td>{html.escape(r["updated"])}</td>'
                f'<td class="sub">{html.escape(r["why"])}</td>'
                f"<td>{go}</td></tr>")
        cls = "bad" if key == "you" else ("warn" if key == "them" else "")
        out.append(
            f'<h3 class="{cls}">{title} <span class="sub">({len(rows)}) — {blurb}</span></h3>'
            f'<div class="tw"><table><thead><tr><th>repo</th><th>PR</th><th>title</th>'
            f'<th>role</th><th>updated</th><th>why</th><th></th></tr></thead>'
            f"<tbody>{body}</tbody></table></div>")

    out.append('<p class="sub">Two of these signals exist nowhere on GitHub, and come '
               'from <code>~/.oss-cli/reviews/ledger.tsv</code>: <strong>review unsent</strong> '
               '(you wrote a verdict and never posted it) and <strong>pushed since '
               'review</strong> (the head SHA moved past the one you reviewed at). '
               'GitHub knows what you <em>said</em>; the ledger knows what you '
               '<em>decided</em>. Keep it current with '
               '<code>oss run followup --sync &lt;n&gt;</code>.</p>'
               '<p><button class="btn" data-ask="followup">What moved since I '
               'reviewed it</button> <span class="sub">runs '
               '<code>oss followup --changed</code> here, and answers below.</span></p>'
               '<pre><code>oss run followup --changed    # the same question, in the terminal\n'
               'oss run review &lt;n&gt;              # the mechanical facts\n'
               'oss run hub --refresh          # re-fetch this view</code></pre>')
    return "".join(out)


def compose_html(todo):
    """The composer's server-rendered half: which PRs are waiting on a review
    from you, and the box that loads one. The diff, the comment boxes and the
    Send are built in the page, from JSON, because a comment is a draft until
    you send it and a draft has no business on a server."""
    rows = [r for r in (todo or {}).get("rows", [])
            if r["bucket"] == "you" and r["state"] == "OPEN"]
    # An unsent write-up first: it is the one where the work is already done and
    # only the posting is missing.
    rows.sort(key=lambda r: (not r["unsent"], r["repo"], -int(r["pr"])))

    cells = []
    for r in rows:
        draft, draft_file = draft_body(r["pr"])
        wrote = (f'<span class="pill ok">write-up</span> <span class="sub">{html.escape(draft_file)}</span>'
                 if draft else '<span class="sub">—</span>')
        flags = ' <span class="pill warn">unsent</span>' if r["unsent"] else ""
        flags += ' <span class="pill warn">pushed since review</span>' if r["moved"] else ""
        cells.append(f'<tr><td class="sub">{html.escape(r["repo"])}</td>'
                 f'<td><a href="https://github.com/{html.escape(r["repo"])}/pull/{r["pr"]}">'
                 f'#{r["pr"]}</a></td>'
                 f'<td>{html.escape(r["title"][:60])}{flags}</td>'
                 f'<td class="sub">{html.escape(r["why"])}</td><td>{wrote}</td>'
                 f'<td><button class="btn" data-open="{html.escape(r["repo"])}#{r["pr"]}">'
                 f"Review →</button></td></tr>")

    # The board can hold thirty of these. All of them above the composer buries
    # the thing you came here to use, so the top of the queue is the list and the
    # tail is one click away.
    head_n, rest = 10, ""
    if len(cells) > head_n:
        rest = (f'<details><summary class="sub">and {len(cells) - head_n} more '
                f'waiting on you</summary><div class="tw"><table><tbody>'
                f'{"".join(cells[head_n:])}</tbody></table></div></details>')
    body = "".join(cells[:head_n])
    unsent = sum(1 for r in rows if r["unsent"])

    note = ('<p class="bad"><strong>Read-only.</strong> <code>BENCH_HUB_READONLY</code> is set, '
            'so the boxes and the preview work and <em>Send</em> refuses.</p>' if READONLY else "")

    return f"""<div class="hd"><h2>Send a review</h2>
    <span class="sub">the diff, the line comments and the summary — posted with one Send</span>
    {button("todo", "Sync now")}<span class="sub" data-msg="todo"></span></div>
    {note}
    <p class="sub">The only view here that writes to GitHub. It posts through
    <code>gh</code>, as you, and posts nothing until you press <em>Send</em> and confirm
    what it names. The summary is prefilled from the paste-ready block of the write-up
    under <code>~/.oss-cli/reviews/</code>, if there is one — the same block
    <code>oss run followup --comment &lt;n&gt;</code> prints. <em>Preview</em> renders with this
    site's own markdown, which is close to GitHub's and not identical: it is here to catch a
    broken list or an unclosed code fence, not to be the last word on spacing.</p>

    <h3>Waiting on you <span class="sub">({len(rows)} from the To-do board ·
    {unsent} written and never posted)</span></h3>
    <div class="tw"><table><thead><tr><th>repo</th><th>PR</th><th>title</th><th>why</th>
    <th>write-up</th><th></th></tr></thead>
    <tbody>{body or '<tr><td colspan=6 class=sub>nothing is waiting on you — or the board has never been synced</td></tr>'}</tbody>
    </table></div>{rest}

    <h3>Or open any pull request</h3>
    <div class="bar">
      <input id="c-repo" value="{html.escape(UPSTREAM)}" size="30" aria-label="repository">
      <input id="c-pr" class="n" placeholder="4245" aria-label="PR number">
      <button class="btn" id="c-load">Load the diff</button>
      <span class="sub">any repository you can see — the composer is not Log4j-specific</span>
    </div>
    <div id="cmp"><p class="sub">Nothing loaded. Pick a PR above.</p></div>"""


def status_badge(s):
    if not s["ok"]:
        return f'<span class="pill bad">{html.escape(s["why"])}</span>'
    # An installed component has no branch, no dirty count and nothing to pull. Reusing the
    # working-tree badges for it would print "clean" about something that has no working tree.
    if s.get("installed"):
        return ('<span class="pill ok">installed</span>'
                f'<span class="pill">{html.escape(s.get("head", ""))}</span>')
    bits = [f'<span class="pill">{html.escape(s["branch"])}</span>']
    if s["dirty"]:
        bits.append(f'<span class="pill warn">{s["dirty"]} uncommitted</span>')
    else:
        bits.append('<span class="pill ok">clean</span>')
    if s["behind"]:
        bits.append(f'<span class="pill warn">{s["behind"]} behind — git pull</span>')
    if s["ahead"]:
        bits.append(f'<span class="pill">{s["ahead"]} ahead</span>')
    return " ".join(bits)


def report_html(rep, days):
    """Today across the three repos, then a strip of the days before it.

    Detail for one day only. A month of full commit lists on one page is not a
    report, it is a log — the strip is there to show the shape, and any day in it
    is one click away from being the detailed one.
    """
    if not rep:
        return f"""<div class="hd"><h2>Daily report</h2>{button("report", "Build today")}
        <span class="sub" data-msg="report"></span></div>
        <p>No report yet. This one is derived from the three clones and your own
        GitHub repos — nothing upstream — and it is written once a day by the
        launchd agent. Press <em>Build today</em>, or:</p>
        <pre><code>oss run hub --sync report</code></pre>"""

    day = rep.get("day", "")
    is_today = day == today_str()
    tot_c = sum(len(r.get("commits") or []) for r in rep["repos"])
    tot_a = sum(r.get("adds", 0) for r in rep["repos"])
    tot_d = sum(r.get("dels", 0) for r in rep["repos"])
    tot_p = sum(len(r.get("prs") or []) for r in rep["repos"])

    when = " · today" if is_today else ""
    if not is_today:
        # Distinguish "you clicked 2026-08-04" from "today has not been built yet,
        # so here is the last day that was" — they look identical otherwise.
        when = " · today has no report yet" if day == (days[0] if days else "") else ""
    head = (f'<div class="hd"><h2>Daily report</h2>'
            f'{button("report", "Rebuild today")}'
            f'<span class="sub" data-msg="report"></span></div>'
            f'<p class="sub">{html.escape(day)}{when}'
            f' · built {html.escape(rep.get("at", ""))}'
            f'{"" if is_today else " · <a href=\"?#report\">back to today</a>"}</p>')

    if not tot_c and not tot_p:
        head += ('<p><strong>Nothing committed in any of the three.</strong> '
                 'A quiet day is a result, so it is still written down.</p>')
    else:
        pr_bit = ""
        if tot_p:
            pr_bit = f", {tot_p} pull request{'' if tot_p == 1 else 's'} moved"
        head += (f'<p><strong>{tot_c} commit{"" if tot_c == 1 else "s"}</strong>, '
                 f'<span class="ok">+{tot_a}</span> <span class="bad">−{tot_d}</span>'
                 f'{pr_bit}.</p>')

    cards = []
    for r in rep["repos"]:
        if r.get("missing"):
            cards.append(f"""<div class="card"><h3>{html.escape(r["name"])}</h3>
            <div class="sub">no clone at <code>{html.escape(r["missing"])}</code></div></div>""")
            continue
        commits = r.get("commits") or []
        rows = "".join(
            f'<tr><td><code>{html.escape(c["sha"])}</code></td>'
            f'<td>{html.escape(c["subject"])}</td>'
            f'<td class="num"><span class="ok">+{c["adds"]}</span> '
            f'<span class="bad">−{c["dels"]}</span></td>'
            f'<td class="num sub">{html.escape(c["when"][11:16])}</td></tr>'
            for c in commits)
        body = (f'<table class="tbl"><tbody>{rows}</tbody></table>' if rows
                else '<p class="sub">nothing committed</p>')

        prs = r.get("prs") or []
        if prs:
            body += '<div class="sub" style="margin-top:8px">pull requests</div>' + "".join(
                f'<div class="kv"><a href="{html.escape(p["url"], quote=True)}">'
                f'#{p["number"]}</a> {html.escape(p["title"])} '
                f'<em>{html.escape(", ".join(p["moved"]))}</em></div>' for p in prs)

        flags = []
        if r.get("dirty"):
            flags.append(f'{r["dirty"]} uncommitted')
        if r.get("ahead"):
            flags.append(f'{r["ahead"]} unpushed')
        if r.get("behind"):
            flags.append(f'{r["behind"]} behind')
        cards.append(f"""<div class="card"><div class="role">{html.escape(r.get("role", ""))}</div>
        <h3>{html.escape(r["name"])}</h3>
        <div class="sub">{html.escape(r.get("branch", ""))}
        {(" · " + " · ".join(flags)) if flags else ""}</div>
        <div style="margin-top:10px">{body}</div></div>""")

    strip = ""
    others = [d for d in days if d != day][:14]
    if others:
        links = " ".join(f'<a href="?day={html.escape(d, quote=True)}#report">{html.escape(d)}</a>'
                         for d in others)
        strip = (f'<div class="hd" style="margin-top:20px"><h3>Days before this one</h3></div>'
                 f'<p class="sub">{links}</p>')

    return head + f'<div class="cards">{"".join(cards)}</div>' + strip


def build(day=None):
    states = {name: repo_state(p) for name, p, _, _ in REPOS}
    # `oss`, not `bench`. The same stale name as the launchd plist: this page
    # reported the command that drives it as missing, because the command it
    # named stopped existing when the engine moved into oss.
    cmds = {c: installed(c) for c in ("oss",)}
    led, evid, files = reviews()

    todo, age = todo_load()
    triage = newest_triage()

    n_you = len([r for r in todo["rows"] if r["bucket"] == "you"]) if todo else 0
    nav = ['<h1>oss run hub</h1><div class="sub">Apache Log4j, on real applications</div>',
           '<div class="grp">start</div>',
           f'<a href="#todo" data-t="todo">To do{f" <b>({n_you})</b>" if n_you else ""}</a>',
           '<a href="#compose" data-t="compose">Send a review</a>',
           f'<a href="#report" data-t="report">Daily report</a>',
           '<a href="#home" data-t="home">Overview &amp; status</a>',
           '<a href="#flow" data-t="flow">Reviewing a PR</a>',
           '<a href="#reviews" data-t="reviews">Reviews</a>',
           '<a href="#triage" data-t="triage">Backlog triage</a>',
           '<div class="grp">sync</div>',
           f'<button class="btn wide" data-job="all">Sync everything</button>',
           '<div class="sub" data-msg="all" style="padding:6px 8px"></div>']
    secs = []

    # ---- to do
    secs.append(f'<section id="todo">{todo_html(todo, age)}</section>')

    # ---- compose
    secs.append(f'<section id="compose" hidden>{compose_html(todo)}</section>')

    # ---- daily report
    secs.append(f'<section id="report" hidden>'
                f'{report_html(report_load(day), report_days())}</section>')

    # ---- home
    cards = []
    for name, path, role, blurb in REPOS:
        s = states[name]
        cards.append(f"""<div class="card"><div class="role">{role}</div>
        <h3>{html.escape(name)}</h3><div class="sub">{html.escape(blurb)}</div>
        <div style="margin-top:10px">{status_badge(s)}</div>
        <div class="kv">{html.escape(s.get("head", "") or "—")}<br>
        {html.escape(s.get("when", ""))} · <code>{html.escape(s.get("path", str(path)))}</code></div></div>""")

    inst = "".join(
        f"<tr><td><code>{c}</code></td><td>{'<span class=ok>installed</span>' if p else '<span class=bad>not on PATH</span>'}</td>"
        f"<td><code>{html.escape(p or '—')}</code></td></tr>" for c, p in cmds.items())

    secs.append(f"""<section id="home" hidden><div class="hd"><h2>This bench</h2>
    {button("fetch", "Fetch")}<span class="sub" data-msg="fetch"></span></div>
    <p>Apache Log4j, run against real applications on real JVMs across the version ×
    config × app matrix. This page is the working surface: what is waiting on you, what
    was reviewed, and what the last sweep found.</p>
    <div class="cards">{''.join(cards)}</div>
    <h3>Installed commands</h3>
    <div class="tw"><table><thead><tr><th>command</th><th>state</th><th>path</th></tr></thead>
    <tbody>{inst}</tbody></table></div>
    <p class="sub">This page re-reads all three working trees on every request — no build
    step and no cache, so a reload is the refresh. It never pulls, merges or writes to a
    worktree. “behind” is measured against the last fetch, which is what
    <em>Fetch all three</em> is for.</p>
    </section>""")

    # ---- flow
    secs.append(f"""<section id="flow" hidden><div class="hd"><h2>Reviewing a PR, across the three</h2></div>
    {md_to_html(FLOW)}</section>""")

    # ---- reviews
    rows = ""
    for r in led:
        ev = "yes" if r["pr"] in evid else "—"
        wf = files.get(r["pr"])
        link = f'<code>{html.escape(wf)}</code>' if wf else "—"
        rows += (f'<tr><td><a href="https://github.com/apache/logging-log4j2/pull/{r["pr"]}">'
                 f'#{r["pr"]}</a></td><td>{html.escape(r["state"])}</td><td>{html.escape(r["when"])}</td>'
                 f'<td>{html.escape(r["author"])}</td><td>{html.escape(r["posted"])}</td>'
                 f'<td>{link}</td><td>{ev}</td><td>{html.escape(r["note"])}</td></tr>')
    secs.append(f"""<section id="reviews" hidden><div class="hd"><h2>Reviews</h2>
    <span class="sub">{len(led)} in the ledger · {len(evid)} with evidence in .bench/reviews</span>
    <button class="btn" data-job="reload">Re-read from disk</button></div>
    <p class="sub">“posted” is whether the paste-ready comment went upstream. The write-up
    column is the file under <code>~/.oss-cli/reviews/</code>; evidence is a
    <code>oss run review &lt;n&gt;</code> run still on disk.</p>
    <div class="tw"><table><thead><tr><th>PR</th><th>state</th><th>reviewed</th><th>author</th>
    <th>posted</th><th>write-up</th><th>evidence</th><th>note</th></tr></thead>
    <tbody>{rows or '<tr><td colspan=8>ledger empty</td></tr>'}</tbody></table></div></section>""")

    # ---- backlog triage, from knowledge-creator's own report
    auto = (f"anything older than {TRIAGE_MAX_AGE_H:g} h is re-swept in the background"
            if TRIAGE_MAX_AGE_H > 0 else
            "background re-sweep is off (<code>BENCH_TRIAGE_MAX_AGE_H=0</code>)")
    blurb = f"""<p class="sub">Generated by <code>knowledge-creator/triage.sh</code> —
    clusters, mergeable-now, one-fix-away, top issues. A few hundred API calls, so it runs
    off the request path: {auto}, and <em>Sweep now</em> starts one immediately. By hand:</p>
    <pre><code>cd ~/own\\ repo/knowledge-creator &amp;&amp; ./triage.sh {html.escape(UPSTREAM)} --no-ai</code></pre>"""
    if triage:
        age = datetime.now().timestamp() - triage.stat().st_mtime
        stale = TRIAGE_MAX_AGE_H > 0 and age > TRIAGE_MAX_AGE_H * 3600
        tstamp = (f'<span class="sub">{html.escape(triage.name)} · '
                  f'{"<span class=warn>" if stale else ""}{human_age(age)}'
                  f'{"</span>" if stale else ""}</span>')
        view = ('<iframe src="triage.html" style="width:100%;height:78vh;'
                'border:1px solid var(--line);border-radius:10px;'
                'background:var(--card)"></iframe>')
    else:
        tstamp = '<span class="sub warn">no report yet</span>'
        view = ('<p>No <code>triage-*.html</code> in knowledge-creator yet — '
                '<em>Sweep now</em> writes the first one.</p>')
    secs.append(f"""<section id="triage" hidden><div class="hd"><h2>Backlog triage</h2>
    {tstamp}{button("triage", "Sweep now")}<span class="sub" data-msg="triage"></span></div>
    {blurb}{view}</section>""")

    # ---- docs
    # The group label leads with the ROLE, not the repo name. "log4j2-workout" told
    # you which checkout a page came from; "bench · log4j2-workout" tells you what it
    # is FOR, which is the question someone scanning a sidebar is actually asking.
    for repo_name, kind, base, paths in DOCS:
        label = repo_name if kind == "core" else f"{kind} · {repo_name}"
        nav.append(f'<div class="grp">{html.escape(label)}</div>')
        for rel in paths:
            f = base / rel
            if not f.exists():
                continue
            sid = re.sub(r"[^a-z0-9]+", "-", f"{repo_name}-{rel}".lower()).strip("-")
            nav.append(f'<a href="#{sid}" data-t="{sid}">{html.escape(rel)}</a>')
            try:
                body = md_to_html(f.read_text(errors="replace"))
            except Exception as e:  # a doc that cannot be read is a fact, not a crash
                body = f"<p class='bad'>could not read: {html.escape(str(e))}</p>"
            secs.append(f'<section id="{sid}" hidden><div class="hd">'
                        f'<h2>{html.escape(rel)}</h2><span class="sub">{html.escape(repo_name)}</span>'
                        f'<button class="btn" data-job="reload">Re-read from disk</button></div>'
                        f'<div class="doc">{body}</div></section>')

    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    return f"""<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>oss run hub</title><style>{CSS}</style></head><body>
<div class="wrap"><nav>{''.join(nav)}</nav><main>{''.join(secs)}
<div class="foot">generated {stamp} · the working tree is read on every request ·
sync pulls what only GitHub knows</div></main></div>
<script id="ev-json" type="application/json">{json.dumps(EVENTS)}</script>
<script>{JS}</script></body></html>"""


FLOW = """
One PR, end to end. Each step is the repo that owns it, and nothing is done twice.

## 1. Know what you are looking at — `oss-cli`

No clone, no build. Facts, conventions, and your own prior work on the changed paths.

```bash
oss-cli review 4218 --no-verdict     # facts and convention gates only
oss-cli review 4218                  # ...plus a local verdict, if Ollama is up
```

Evidence is cached by **head commit SHA**, not PR number — a push invalidates it
automatically, so you are never reading a review of code that no longer exists.

## 2. Judge it — by hand

The part no tool does. `Reference/reviewing-a-contributor-pull-request` §2, in this order:

- Who filed the linked issue, and when? Hours after your own issue, from an
  account with no history, means the PR was written *from your issue text* — its
  passing tests assert the specification you wrote.
- Does the fix match the bug, or overshoot it? Check **every** implementation of
  the thing being changed, not only the one that motivated it.
- Does it change something a working user config depends on?

## 3. Run it — `oss run review`

```bash
oss run review 4218                 # eleven steps, one directory
oss run review 4218 --no-build      # steps 1-5 only, seconds
oss run review 4301 --3x --full     # 3.x clone, full reactor
```

Everything happens in a throwaway git worktree, so your clone stays on its branch
and `~/.m2` is never overwritten. Safe to run with the clone open in an IDE.

The two steps that matter most, because a human cannot do them by reading:

- **RED** — the PR's *test files only*, on the base commit, without its fix. It
  must fail. A test that passes here asserts that the code loads, not that it was
  fixed. Read the verdict carefully: *fail as required*, *compile error* (weaker
  — it pins an API, not a behaviour), *PASS* (blocking finding), or
  *inconclusive* (the build broke before any test ran — never read this as red).
- **pollution** — did the tests write into the source tree?

Step 8 runs the whole module suite and **does not** tell you the PR is broken:
`log4j-core-test` is 8729 tests with its own failures on any machine. The summary
splits failures into classes the PR touches and classes it does not. Untouched
ones are *probably* pre-existing — and "probably" is not a review finding, so run
the base before writing it down.

## 4. Reproduce it — `oss run repro`

```bash
oss run run core-java --config xml/<cfg> --log4j 2.26.1
oss run repro 4218 --pr --config xml/<cfg> --scenario <s> --log4j 2.25.5 --log4j 2.26.1
```

Baseline against **releases first**. `--install` overwrites `2.27.0-SNAPSHOT`, so
a baseline taken afterwards measures the PR twice.

A clean exit proves nothing: Log4j catches appender exceptions, reports them
through `StatusLogger`, and exits 0. Verify the artefact, not the exit code.

## 5. Write it up, then post — by hand

One file per PR under `~/.oss-cli/reviews/`, ending in a paste-ready block. Draft
first, post second. Separate blocking from non-blocking so the author knows what
gates the merge.

```bash
oss run followup --comment 4218        # read it
oss run followup --comment 4218 | gh pr comment 4218 -R apache/logging-log4j2 --body-file -
```

## 6. Keep it — `--file`

```bash
oss run review 4218 --file
```

Hands the write-up to `knowledge-creator/pr-review-file.py`, which files it under
`Projects/<topic>/pr-reviews/` and indexes it. This is the one artefact no
harvester can reconstruct: public threads record what was *said*, not the
reasoning that got there — including the parts you decided not to post.
"""


AGENT = "com.ramanathan.bench-hub"
AGENT_PLIST = HOME / "Library" / "LaunchAgents" / f"{AGENT}.plist"
AGENT_TEMPLATE = WORKOUT / "infra" / "launchd" / f"{AGENT}.plist"


def install_agent(remove=False):
    """Render the committed template with this checkout's paths, and load it.

    The template is the copy of record: a machine rebuilt from the repository
    can reinstall the agent without anyone remembering what was in it.
    """
    target = f"gui/{os.getuid()}/{AGENT}"
    if AGENT_PLIST.exists():
        subprocess.run(["launchctl", "bootout", target],
                       capture_output=True, text=True)
    if remove:
        AGENT_PLIST.unlink(missing_ok=True)
        print(f"removed {AGENT_PLIST}")
        return
    if not AGENT_TEMPLATE.exists():
        sys.exit(f"error: no template at {AGENT_TEMPLATE}")

    body = (AGENT_TEMPLATE.read_text()
            .replace("__ROOT__", str(WORKOUT))
            .replace("__HOME__", str(HOME)))
    AGENT_PLIST.parent.mkdir(parents=True, exist_ok=True)
    AGENT_PLIST.write_text(body)
    (WORKOUT / ".bench" / "hub").mkdir(parents=True, exist_ok=True)

    lint = subprocess.run(["plutil", "-lint", str(AGENT_PLIST)],
                          capture_output=True, text=True)
    if lint.returncode != 0:
        sys.exit(f"error: the rendered plist is malformed —\n{lint.stdout}{lint.stderr}")

    r = subprocess.run(["launchctl", "bootstrap", f"gui/{os.getuid()}", str(AGENT_PLIST)],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"error: launchctl bootstrap failed — {r.stderr.strip() or r.stdout.strip()}")
    print(f"installed {AGENT_PLIST}")
    print(f"  serving http://localhost:8787/ at login, restarts if it dies")
    print(f"  logs    {WORKOUT}/.bench/hub/agent.{{out,err}}.log")
    print(f"  remove  oss run hub --uninstall")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _reply(self, body, ctype, code=200):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, obj, code=200):
        self._reply(json.dumps(obj).encode(), "application/json", code)

    def do_POST(self):
        """Two endpoints, both from the composer: render markdown, and send.

        Errors come back as 200 with an `error` key rather than a status code —
        every one of them is a message for the person writing the review ("line
        must be part of the diff", "you cannot approve your own pull request"),
        and a 422 in the console is not that message.
        """
        path = urlparse(self.path).path
        try:
            n = int(self.headers.get("Content-Length") or 0)
            req = json.loads(self.rfile.read(n) or b"{}")
        except Exception:
            self._json({"error": "malformed request"}, 400)
            return
        try:
            if path == "/preview":
                self._json({"html": md_to_html(req.get("text") or "")})
            elif path == "/review":
                self._json(send_review(req))
            else:
                self._json({"error": f"no such endpoint: {path}"}, 404)
        except Exception as e:
            self._json({"error": str(e) or e.__class__.__name__})

    def do_GET(self):
        q = parse_qs(urlparse(self.path).query)
        if self.path.startswith("/triage.html"):
            body = triage_page(q.get("theme", ["auto"])[0])
            if body is None:
                self.send_error(404, "no triage-*.html in knowledge-creator")
                return
            ctype = "text/html; charset=utf-8"
        elif self.path.startswith("/job"):
            name = q.get("name", [""])[0]
            if not name:
                payload = {n: job_state(n) for n in JOBS}
            elif name not in JOBS:
                self.send_error(404, f"no such job: {name}")
                return
            else:
                payload = (job_start(name) if q.get("start")
                           else job_state(name))
            body = json.dumps(payload).encode()
            ctype = "application/json"
        elif self.path.startswith("/pr.json"):
            # On the request path on purpose: you asked for this PR, and it is one
            # `gh pr view` plus one paginated files call, cached by head SHA after
            # the first. The server is threaded, so the page stays alive meanwhile.
            try:
                body = json.dumps(pr_bundle(q.get("repo", [UPSTREAM])[0],
                                            q.get("pr", [""])[0],
                                            force=bool(q.get("reload")))).encode()
            except Exception as e:
                body = json.dumps({"error": str(e) or e.__class__.__name__}).encode()
            ctype = "application/json"
        elif self.path.startswith("/ask.json"):
            body = json.dumps(ask(q.get("name", [""])[0],
                                  q.get("q", [""])[0])).encode()
            ctype = "application/json"
        elif self.path.startswith("/refresh"):
            # Explicit, synchronous, and it says how long it took. The background
            # refresher exists so a page load never waits on twenty API calls.
            d = todo_refresh()
            body = json.dumps({"rows": len(d["rows"]), "at": d["at"]}).encode()
            ctype = "application/json"
        elif self.path.startswith("/status.json"):
            body = json.dumps({
                "stamp": "|".join(
                    f"{n}:{repo_state(p).get('head', '')}:{repo_state(p).get('dirty', 0)}"
                    for n, p, _, _ in REPOS)}).encode()
            ctype = "application/json"
        else:
            # ?day= picks which day the report renders in detail; every other
            # view is the same page regardless.
            body = build(day=(q.get("day", [""])[0] or None)).encode()
            ctype = "text/html; charset=utf-8"
        self._reply(body, ctype)


def hub_alive(port):
    """Is the thing on this port one of these? /status.json is answered by no
    other server, so this cannot mistake somebody's dev server for the hub."""
    try:
        with urlopen(f"http://127.0.0.1:{port}/status.json", timeout=2) as r:
            return "stamp" in json.loads(r.read())
    except Exception:
        return False


def main():
    ap = argparse.ArgumentParser(description="One local site over all three repos.")
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--once", action="store_true", help="write index.html and exit")
    # Opening is the default: a command whose whole job is to show you a page and
    # then only prints a URL reads as broken. --open is kept as a no-op so any
    # muscle memory or script that passes it still works.
    ap.add_argument("--open", action="store_true", help="(default) open a browser")
    ap.add_argument("--no-open", dest="no_open", action="store_true",
                    help="just serve; do not open a browser")
    ap.add_argument("--refresh", action="store_true",
                    help="re-fetch the To-do view from GitHub, then exit")
    ap.add_argument("--sync", choices=sorted(JOBS), metavar="JOB",
                    help=f"run one sync job and exit: {', '.join(sorted(JOBS))}")
    ap.add_argument("--pr", metavar="N",
                    help="open the review composer on this pull request")
    ap.add_argument("--repo", default=UPSTREAM,
                    help=f"which repository --pr means (default {UPSTREAM})")
    ap.add_argument("--install", action="store_true",
                    help="install the launchd agent so the site starts at login")
    ap.add_argument("--uninstall", action="store_true", help="remove that agent")
    ap.add_argument("--approve-upstream", metavar="OWNER/NAME", default="",
                    help="permit posts to exactly this repository, for this run only; "
                         "each send is still confirmed by typing the name. Without it, "
                         "every outward write is refused.")
    args = ap.parse_args()

    if args.install or args.uninstall:
        install_agent(remove=args.uninstall)
        return

    if args.sync:
        s = job_run(args.sync)
        print(f"{JOBS[args.sync][0]}: {s['msg']}  ({s['took']}s)")
        sys.exit(0 if s["ok"] else 1)

    if args.refresh:
        d = todo_refresh()
        by = {}
        for r in d["rows"]:
            by[r["bucket"]] = by.get(r["bucket"], 0) + 1
        print(f"{len(d['rows'])} PRs — " + ", ".join(f"{k}: {v}" for k, v in sorted(by.items())))
        print(TODO_CACHE)
        return

    missing = [n for n, p, _, _ in REPOS if not (p / ".git").exists()]
    if missing:
        print(f"note: not a git clone — {', '.join(missing)} "
              f"(set BENCH_OSSCLI_DIR / BENCH_KB_DIR)", file=sys.stderr)

    # Per run, never persisted. The agent's plist does not pass this, so the
    # always-on hub cannot post at all -- posting requires starting one by hand,
    # deliberately, with the repository named.
    global APPROVED_UPSTREAM
    if args.approve_upstream:
        if not re.fullmatch(r"[\w.-]+/[\w.-]+", args.approve_upstream):
            sys.exit(f"--approve-upstream takes a repository as owner/name — got {args.approve_upstream!r}")
        APPROVED_UPSTREAM = args.approve_upstream
        print(f"upstream writes APPROVED for {APPROVED_UPSTREAM} (each send still confirmed)")

    if args.once:
        out = WORKOUT / ".bench" / "hub" / "index.html"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(build())
        print(out)
        return

    url = f"http://localhost:{args.port}/"
    if args.pr:
        url += f"?repo={quote(args.repo)}&pr={quote(str(args.pr).lstrip('#'))}"

    # The launchd agent holds this port all day, so the second `oss run hub` of the
    # day used to die on "cannot bind" — which is exactly wrong, because the page
    # it wanted is already being served. If something on the port answers as a
    # hub, hand it the browser and get out of the way.
    if hub_alive(args.port):
        print(f"a hub is already serving {url}", file=sys.stderr)
        if not args.no_open:
            webbrowser.open(url)
        return

    try:
        # Threaded since the composer: loading a diff is a GitHub round trip on
        # the request path, and on a single-threaded server that froze every other
        # tab — including the preview the same page was waiting on.
        srv = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    except OSError as e:
        sys.exit(f"error: cannot bind {url} — {e}")
    # Refresh off the request path, so the first page load is instant even when
    # the cache is cold or an hour stale.
    def refresher():
        while True:
            _, age = todo_load()
            if age is None or age > 900:
                s = job_run("todo")
                print(f"to-do {'refreshed' if s['ok'] else 'refresh failed'}: {s['msg']}",
                      file=sys.stderr)
            # The triage sweep is far heavier than the to-do fetch, so it gets its
            # own, much longer staleness window. BENCH_TRIAGE_MAX_AGE_H=0 turns the
            # automatic sweep off and leaves the button.
            t_age = triage_age()
            if TRIAGE_MAX_AGE_H > 0 and (t_age is None or t_age > TRIAGE_MAX_AGE_H * 3600):
                s = job_run("triage")
                print(f"triage {'swept' if s['ok'] else 'sweep failed'}: {s['msg']}",
                      file=sys.stderr)
            # The daily report writes itself. Under launchd this is what makes it
            # daily at all: at midnight today_str() names a new file, which does
            # not exist, so the first pass after it writes the new day. Nothing
            # has to be pressed, and yesterday's file is never rewritten.
            f = REPORT_DIR / f"{today_str()}.json"
            if not f.exists() or time.time() - f.stat().st_mtime > 1800:
                s = job_run("report")
                print(f"report {'built' if s['ok'] else 'FAILED'}: {s['msg']}",
                      file=sys.stderr)
            time.sleep(300)
    threading.Thread(target=refresher, daemon=True).start()

    print(f"oss run hub on {url}   (ctrl-c to stop)", file=sys.stderr)
    if not args.no_open:
        if not webbrowser.open(url):
            print(f"could not open a browser — go to {url}", file=sys.stderr)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("", file=sys.stderr)


if __name__ == "__main__":
    main()
