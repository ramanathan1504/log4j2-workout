#!/usr/bin/env python3
"""
hub.py — one local site over all three repos.

    bench hub                 serve on http://localhost:8787, and open it
    bench hub --port 9000
    bench hub --no-open       serve only, no browser
    bench hub --once          write index.html and exit, no server
    bench hub --sync all      pull everything the page can pull, then exit
    bench hub --sync triage   one job only — fetch, todo, triage or all

The page is regenerated on every request, from the working tree as it is right
now. There is no build step, no cache and no watcher to fall out of sync: if you
commit in any of the three repos and reload, you are looking at the new state.

Stdlib only, deliberately. This machine has no markdown library and the site has
to keep working on a laptop with no network, so the renderer below is a small
subset renderer rather than a dependency.

It reads the three working trees; it never fetches, pulls or commits in them.
Two views are the exception, because they answer questions the working tree
cannot: the To-do board calls the GitHub API, and Backlog triage shells out to
knowledge-creator's triage.sh, which writes its report there. Both are cached,
both say how old they are, and neither runs on the request path.
"""

import argparse
import fcntl
import html
import json
import os
import re
import subprocess
import sys
import threading
import time
import webbrowser
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

HOME = Path.home()
WORKOUT = Path(__file__).resolve().parent.parent
OSSCLI = Path(os.environ.get("BENCH_OSSCLI_DIR") or HOME / "own repo" / "oss-cli")
KB = Path(os.environ.get("BENCH_KB_DIR") or HOME / "own repo" / "knowledge-creator")

REPOS = [
    ("log4j2-workout", WORKOUT, "runs", "real apps, real JVMs, the version matrix"),
    ("oss-cli", OSSCLI, "knows", "facts on any repo from the API — no clone"),
    ("knowledge-creator", KB, "remembers", "harvest, file, index, retrieve"),
]

DOCS = [
    ("log4j2-workout", WORKOUT, ["README.md", "CLAUDE.md", "docs/PR-REVIEW.md",
                                 "docs/BY-HAND.md", "docs/BENCH-NOTES.md", "docs/HANDOVER.md",
                                 "docs/GH-COMMANDS.md", "docs/FEATURE-MATRIX.md",
                                 "docs/ISSUES.md", "docs/GAPS.md"]),
    ("oss-cli", OSSCLI, ["README.md", "COMMANDS.md", "SETUP.md", "DEVELOPING.md",
                         "MACOS_AUTOMATION.md", "CHANGELOG.md"]),
    ("knowledge-creator", KB, ["README.md", "SETUP.md", "DEVELOPING.md", "AI-OPTIONAL.md"]),
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


def repo_state(path):
    if not (path / ".git").exists():
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


def ledger_map():
    out = {}
    f = WORKOUT / "docs" / "pr-reviews" / "ledger.tsv"
    if f.exists():
        for line in f.read_text().split("\n"):
            if not line.strip() or line.startswith("#"):
                continue
            c = line.split("\t")
            if len(c) >= 7:
                out[c[0]] = {"verdict": c[1], "when": c[2], "sha": c[3],
                             "author": c[4], "posted": c[5], "note": c[6]}
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
    targets |= {(UPSTREAM, n) for n in led}
    targets |= {(p["repository"]["nameWithOwner"], str(p["number"])) for p in authored}
    # Your own repos are not follow-up: nobody is waiting on you there, you just
    # merge them. This board is for work in someone else's project.
    targets = {(r, n) for r, n in targets if not r.lower().startswith(f"{me.lower()}/")}

    rows = []
    for repo, n in sorted(targets, key=lambda t: (t[0], -int(t[1]))):
        d = gh_json("pr", "view", n, "--repo", repo, "--json",
                    "number,title,author,state,isDraft,updatedAt,headRefOid,"
                    "reviews,comments,mergeable,reviewDecision,statusCheckRollup",
                    timeout=45)
        if not d:
            continue
        # The ledger only knows about the repo it was written for.
        l = led.get(n) if repo == UPSTREAM else None
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
    ledger = WORKOUT / "docs" / "pr-reviews" / "ledger.tsv"
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
             for p in (WORKOUT / "docs" / "pr-reviews").glob("*.md")
             if p.name[0].isdigit()}
    return rows, evidence, files


# -------------------------------------------------------------------- page ---
CSS = """
:root{--bg:#fbfaf8;--fg:#1c1b19;--mut:#6b675f;--line:#e2ded6;--card:#fff;
--acc:#8a4b2a;--ok:#2f6a3f;--warn:#8a6a1a;--bad:#9b2c2c;--code:#f3f0ea;}
@media (prefers-color-scheme:dark){:root:not([data-theme=light]){
--bg:#14130f;--fg:#eae7e0;--mut:#9d978c;--line:#2c2a25;--card:#1b1a16;
--acc:#d99a6c;--ok:#7fb98c;--warn:#d9b96c;--bad:#e08a8a;--code:#232019;}}
:root[data-theme=dark]{--bg:#14130f;--fg:#eae7e0;--mut:#9d978c;--line:#2c2a25;
--card:#1b1a16;--acc:#d99a6c;--ok:#7fb98c;--warn:#d9b96c;--bad:#e08a8a;--code:#232019;}
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
show(location.hash.slice(1)||'home');

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
  fetch('status.json').then(r=>r.json()).then(d=>{
  if(window.__stamp && window.__stamp!==d.stamp) location.reload();
  window.__stamp=d.stamp;}).catch(()=>{})},15000);
"""


def newest_triage():
    """knowledge-creator's triage.sh already answers 'what is happening in the
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
# triage.sh is a read-only GitHub sweep, but it is an expensive one — ~80 PRs and
# ~160 issues, a few hundred API calls — and it writes its report into
# knowledge-creator. So it never runs on the request path: the button and the
# background thread both start it here, and the page polls for the result.
TRIAGE_SH = KB / "triage.sh"
TRIAGE_MAX_AGE_H = float(os.environ.get("BENCH_TRIAGE_MAX_AGE_H", "24"))


def triage_age():
    t = newest_triage()
    return None if not t else datetime.now().timestamp() - t.stat().st_mtime


def triage_sweep():
    if not TRIAGE_SH.exists():
        raise FileNotFoundError(f"no triage.sh at {TRIAGE_SH}")
    r = subprocess.run([str(TRIAGE_SH), UPSTREAM, "--no-ai"], cwd=str(KB),
                       capture_output=True, text=True, timeout=1800)
    if r.returncode != 0:
        # The last non-empty line is the failure; the rest is progress.
        tail = [l for l in (r.stderr or r.stdout).strip().split("\n") if l.strip()]
        raise RuntimeError(tail[-1] if tail else f"exit {r.returncode}")
    t = newest_triage()
    return t.name if t else "swept"


# The report is a standalone page from another repo, with its own light-only
# palette. Rather than restyle it — it is regenerated by triage.sh and any edit
# there would be overwritten — map its palette onto the hub's dark tokens on the
# way out, and let the iframe answer prefers-color-scheme like the hub does.
TRIAGE_DARK = """
:root{color-scheme:dark}
body{background:#14130f;color:#eae7e0}
a{color:#d99a6c}
h3{color:#eae7e0}
h2{border-bottom-color:#2c2a25}
.hint,.stat-l,.ai-note,footer{color:#9d978c}
#sidebar{background:#1b1a16;color:#c9c3b8}
.sb-repo a{color:#d99a6c}
.sb-meta,.sb-sec,.sb-n{color:#9d978c}
.sb-hr{border-top-color:#2c2a25}
.nav-list a{color:#c9c3b8}
.nav-list a:hover{background:#232019;color:#f2efe8}
.sb-n{background:#232019}
table,.ai-table{background:#1b1a16;border-color:#2c2a25}
th{background:#232019;color:#c9c3b8;border-bottom-color:#2c2a25}
td{border-top-color:#2c2a25}
tr:hover td,.ai-table tr:hover td{background:#232019}
.stat,.cluster,.ai{background:#1b1a16;border-color:#2c2a25}
.cluster{border-left-color:#d99a6c}
.ai{background:#1b1a16;border-left-color:#a78bfa}
.ai-table th{background:#2a2233;color:#c4b5fd;border-bottom-color:#3b2f47}
.ai-table code{background:#232019;border-color:#2c2a25;color:#eae7e0}
.rr{color:#c9c3b8;border-top-color:#2c2a25}
.rl-bar{background:#2c2a25}
.warn{background:#2a2416;border-color:#5c4a1a;color:#d9b96c}
footer{border-top-color:#2c2a25}
.b{border-width:1px;border-style:solid}
.g{background:#16281c;color:#7fb98c;border-color:#2f4a37}
.r{background:#2b1717;color:#e08a8a;border-color:#5a2b2b}
.y{background:#2a2416;color:#d9b96c;border-color:#5c4a1a}
.u{background:#151f2e;color:#8ab4e8;border-color:#2a3f5c}
.s{background:#232019;color:#9d978c;border-color:#2c2a25}
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
    "all": ("Everything", sync_all),
}
SYNC_ORDER = ("fetch", "todo", "triage")

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
    """`bench hub` runs from launchd all day, so a terminal `--sync` is a second
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
        msg, ok = "already running in another bench hub — left it to finish", True
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
        <pre><code>bench hub --refresh</code></pre>
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
            body += (
                f'<tr><td class="sub">{html.escape(r["repo"])}</td>'
                f'<td><a href="https://github.com/{html.escape(r["repo"])}/pull/{r["pr"]}">'
                f'#{r["pr"]}</a></td>'
                f'<td>{html.escape(r["title"][:66])}{flags}</td>'
                f"<td>{who}</td>"
                f'<td>{html.escape(r["updated"])}</td>'
                f'<td class="sub">{html.escape(r["why"])}</td></tr>')
        cls = "bad" if key == "you" else ("warn" if key == "them" else "")
        out.append(
            f'<h3 class="{cls}">{title} <span class="sub">({len(rows)}) — {blurb}</span></h3>'
            f'<div class="tw"><table><thead><tr><th>repo</th><th>PR</th><th>title</th>'
            f'<th>role</th><th>updated</th><th>why</th></tr></thead>'
            f"<tbody>{body}</tbody></table></div>")

    out.append('<p class="sub">Two of these signals exist nowhere on GitHub, and come '
               'from <code>docs/pr-reviews/ledger.tsv</code>: <strong>review unsent</strong> '
               '(you wrote a verdict and never posted it) and <strong>pushed since '
               'review</strong> (the head SHA moved past the one you reviewed at). '
               'GitHub knows what you <em>said</em>; the ledger knows what you '
               '<em>decided</em>. Keep it current with '
               '<code>bench followup --sync &lt;n&gt;</code>.</p>'
               '<pre><code>bench followup --changed    # the same question, in the terminal\n'
               'bench review &lt;n&gt;              # the mechanical facts\n'
               'bench hub --refresh          # re-fetch this view</code></pre>')
    return "".join(out)


def status_badge(s):
    if not s["ok"]:
        return f'<span class="pill bad">{html.escape(s["why"])}</span>'
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


def build():
    states = {name: repo_state(p) for name, p, _, _ in REPOS}
    cmds = {c: installed(c) for c in ("bench", "oss-cli", "kb")}
    led, evid, files = reviews()

    todo, age = todo_load()
    triage = newest_triage()

    n_you = len([r for r in todo["rows"] if r["bucket"] == "you"]) if todo else 0
    nav = ['<h1>bench hub</h1><div class="sub">three repos, one page</div>',
           '<div class="grp">start</div>',
           f'<a href="#todo" data-t="todo">To do{f" <b>({n_you})</b>" if n_you else ""}</a>',
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

    secs.append(f"""<section id="home" hidden><div class="hd"><h2>Three repos, one job each</h2>
    {button("fetch", "Fetch all three")}<span class="sub" data-msg="fetch"></span></div>
    <p><strong>oss-cli knows → log4j2-workout runs → knowledge-creator remembers.</strong>
    One test decides where new work belongs: <em>does it need to execute code against a
    real app?</em> If yes it is the workout; if it only needs to be retrievable later it is
    knowledge-creator; if neither, oss-cli.</p>
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
    column is the file under <code>docs/pr-reviews/</code>; evidence is a
    <code>bench review &lt;n&gt;</code> run still on disk.</p>
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
    for repo_name, base, paths in DOCS:
        nav.append(f'<div class="grp">{html.escape(repo_name)}</div>')
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
<title>bench hub</title><style>{CSS}</style></head><body>
<div class="wrap"><nav>{''.join(nav)}</nav><main>{''.join(secs)}
<div class="foot">generated {stamp} · the working tree is read on every request ·
sync pulls what only GitHub knows</div></main></div>
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

The part no tool does. `docs/PR-REVIEW.md` §2, in this order:

- Who filed the linked issue, and when? Hours after your own issue, from an
  account with no history, means the PR was written *from your issue text* — its
  passing tests assert the specification you wrote.
- Does the fix match the bug, or overshoot it? Check **every** implementation of
  the thing being changed, not only the one that motivated it.
- Does it change something a working user config depends on?

## 3. Run it — `bench review`

```bash
bench review 4218                 # eleven steps, one directory
bench review 4218 --no-build      # steps 1-5 only, seconds
bench review 4301 --3x --full     # 3.x clone, full reactor
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

## 4. Reproduce it — `bench repro`

```bash
bench run core-java --config xml/<cfg> --log4j 2.26.1
bench repro 4218 --pr --config xml/<cfg> --scenario <s> --log4j 2.25.5 --log4j 2.26.1
```

Baseline against **releases first**. `--install` overwrites `2.27.0-SNAPSHOT`, so
a baseline taken afterwards measures the PR twice.

A clean exit proves nothing: Log4j catches appender exceptions, reports them
through `StatusLogger`, and exits 0. Verify the artefact, not the exit code.

## 5. Write it up, then post — by hand

One file per PR under `docs/pr-reviews/`, ending in a paste-ready block. Draft
first, post second. Separate blocking from non-blocking so the author knows what
gates the merge.

```bash
bench followup --comment 4218        # read it
bench followup --comment 4218 | gh pr comment 4218 -R apache/logging-log4j2 --body-file -
```

## 6. Keep it — `--file`

```bash
bench review 4218 --file
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
    print(f"  remove  bench hub --uninstall")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

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
            body = build().encode()
            ctype = "text/html; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)


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
    ap.add_argument("--install", action="store_true",
                    help="install the launchd agent so the site starts at login")
    ap.add_argument("--uninstall", action="store_true", help="remove that agent")
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

    if args.once:
        out = WORKOUT / ".bench" / "hub" / "index.html"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(build())
        print(out)
        return

    url = f"http://localhost:{args.port}/"
    try:
        srv = HTTPServer(("127.0.0.1", args.port), Handler)
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
            time.sleep(300)
    threading.Thread(target=refresher, daemon=True).start()

    print(f"bench hub on {url}   (ctrl-c to stop)", file=sys.stderr)
    if not args.no_open:
        if not webbrowser.open(url):
            print(f"could not open a browser — go to {url}", file=sys.stderr)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("", file=sys.stderr)


if __name__ == "__main__":
    main()
